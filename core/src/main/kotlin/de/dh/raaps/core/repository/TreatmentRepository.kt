package de.dh.raaps.core.repository

import de.dh.raaps.common.model.DeferredBolus
import de.dh.raaps.common.model.ID_UNDEFINED
import de.dh.raaps.common.model.InsulinAmount
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinCategory
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinStatus
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.MetabolicEventsDao
import de.dh.raaps.core.repository.db.mappers.toEntity
import de.dh.raaps.core.repository.db.mappers.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository for active metabolic treatments (Insulin and Meals).
 * It maintains an in-memory cache for fast access by the APS core and handles persistence via DAOs.
 */
class TreatmentRepository(
    /**
     * Size of the in-memory cache. Entries older than this will be removed from RAM during [prune].
     * Note: This does not affect persistence in the database.
     */
    val historySize: Minutes,
    appDatabase: AppDatabase
) {
    private val metabolicEventsDao: MetabolicEventsDao = appDatabase.metabolicEventsDao()
    private val mutex = Mutex()

    private var mealsHistory: MutableList<MealEntry> = mutableListOf()
    private var insulinHistory: MutableList<InsulinApplication> = mutableListOf()
    private var deferredBoluses: MutableList<DeferredBolus> = mutableListOf()
    private var mealTypes: List<MealType> = emptyList()
    private var insulinTypes: List<InsulinType> = emptyList()

    fun observeMeals(): Flow<List<MealEntry>> = metabolicEventsDao.observeAllMeals()
        .map { entities ->
            val mealTypesMap = metabolicEventsDao.getAllMealTypes().associateBy { it.id }
            entities.mapNotNull { entity ->
                val type = mealTypesMap[entity.meal_type_id]?.toModel()
                type?.let { entity.toModel(it) }
            }
        }

    fun observeInsulinApplications(includeCancelled: Boolean = false): Flow<List<InsulinApplication>> = metabolicEventsDao.observeAllInsulinApplications()
        .map { entities ->
            val insulinTypesMap = metabolicEventsDao.getAllInsulinTypes().associateBy { it.id }
            entities.mapNotNull { entity ->
                val type = insulinTypesMap[entity.insulin_type_id]?.toModel()
                type?.let { entity.toModel(it) }
            }.filter { includeCancelled || it.status != InsulinStatus.Cancelled }
        }

    /**
     * Returns the timestamp where the in-memory history starts.
     */
    fun historyStart(): Timestamp = Timestamp.now().minus(historySize)

    /**
     * Loads the history from the database into the in-memory cache.
     */
    suspend fun load() = mutex.withLock {
        val historyStart = Timestamp.now() - historySize

        // Load Meal Types
        mealTypes = metabolicEventsDao.getAllMealTypes().map { it.toModel() }
        val mealTypesMap = mealTypes.associateBy { it.id }

        // Load Meals
        val mealEntities = metabolicEventsDao.getMealsSince(historyStart.ms)
        mealsHistory = mealEntities.mapNotNull { entity ->
            val type = mealTypesMap[entity.meal_type_id]
            type?.let { entity.toModel(it) }
        }.toMutableList()
        mealsHistory.sortBy { it.timestamp }

        // Load Insulin Types
        insulinTypes = metabolicEventsDao.getAllInsulinTypes().map { it.toModel() }
        val insulinTypesMap = insulinTypes.associateBy { it.id }

        // Load Insulin
        val insulinEntities = metabolicEventsDao.getInsulinApplicationsSince(historyStart.ms)
        insulinHistory = insulinEntities.mapNotNull { entity ->
            val type = insulinTypesMap[entity.insulin_type_id]
            type?.let { entity.toModel(it) }
        }.toMutableList()
        insulinHistory.sortBy { it.timestamp }

        // Load Deferred Boluses
        deferredBoluses = metabolicEventsDao.getAllDeferredBoluses()
            .map { it.toModel() }
            .toMutableList()
        deferredBoluses.sortBy { it.timestamp }
    }

    /**
     * Removes entries from the in-memory cache that are older than the history size.
     */
    suspend fun prune() = mutex.withLock {
        val historyStart = historyStart()
        mealsHistory.removeIf { it.timestamp < historyStart }
        insulinHistory.removeIf { it.timestamp < historyStart }
        deferredBoluses.removeIf { it.timestamp < historyStart }
    }

    /**
     * Merges a range of insulin history from the pump.
     * Updates matching entries, inserts new entries, and cancels unconfirmed scheduled entries.
     */
    suspend fun mergeInsulinHistory(history: InsulinHistory, insulinType: InsulinType) = mutex.withLock {
        val from = Timestamp(history.from)
        val to = Timestamp(history.to)
        val historyStart = historyStart()

        val typesMap = metabolicEventsDao.getAllInsulinTypes().associateBy { it.id }

        // 1. Load existing pump entries in range (sorted by timestamp)
        val existingEntities = metabolicEventsDao.getInsulinApplicationsSince((from - Minutes(5)).ms)
            .filter { it.origin == InsulinOrigin.Pump && it.timestamp <= to + Minutes(5) }

        val existingApplications = existingEntities.mapNotNull { entity ->
            typesMap[entity.insulin_type_id]?.toModel()?.let { entity.toModel(it) }
        }

        // Create O(1) lookup map for candidates with pumpId
        val byPumpId = existingApplications
            .filter { it.pumpId != null }
            .associateBy { it.pumpId!! }

        val matchedExistingIds = mutableSetOf<Long>()
        val updatedApplications = mutableListOf<InsulinApplication>()
        val newApplications = mutableListOf<InsulinApplication>()

        // 2. Match points with existing entries
        history.points.forEach { point ->
            if (point.amount < InsulinAmount.EPSILON) return@forEach
            val timestamp = Timestamp(point.timestamp)

            // Priority 1: O(1) lookup by pumpId
            var match: InsulinApplication? = if (point.pumpId != null) {
                val candidate = byPumpId[point.pumpId]
                if (candidate != null && !matchedExistingIds.contains(candidate.id)) candidate else null
            } else null

            // Priority 2: Timestamp window search (±30 seconds)
            if (match == null) {
                val minTime = timestamp.minusSeconds(30)
                val maxTime = timestamp.plusSeconds(30)

                for (candidate in existingApplications) {
                    if (candidate.timestamp < minTime) continue
                    if (candidate.timestamp > maxTime) break // Early break as list is sorted
                    if (!matchedExistingIds.contains(candidate.id) && candidate.amount.isAlmostEqual(point.amount)) {
                        match = candidate
                        break
                    }
                }
            }

            if (match != null) {
                matchedExistingIds.add(match.id)
                val updated = match.copy(
                    dose = match.dose.copy(timestamp = timestamp, amount = point.amount),
                    status = InsulinStatus.Confirmed,
                    basal = point.category == InsulinCategory.Basal || match.basal,
                    pumpId = point.pumpId ?: match.pumpId
                )
                updatedApplications.add(updated)
            } else {
                val newApp = InsulinApplication(
                    timestamp = timestamp,
                    amount = point.amount,
                    insulinType = insulinType,
                    origin = InsulinOrigin.Pump,
                    basal = point.category == InsulinCategory.Basal,
                    status = InsulinStatus.Confirmed,
                    pumpId = point.pumpId
                )
                newApplications.add(newApp)
            }
        }

        // 3. Collect scheduled entries that were not matched to cancel
        val cancelledApplications = existingApplications
            .filter { it.status == InsulinStatus.Scheduled && it.timestamp in from..to && !matchedExistingIds.contains(it.id) }
            .map { it.copy(status = InsulinStatus.Cancelled) }

        // 4. Perform batch DB updates and inserts
        val toUpdateEntities = (updatedApplications + cancelledApplications).map { it.toEntity() }
        if (toUpdateEntities.isNotEmpty()) {
            metabolicEventsDao.updateInsulinApplications(toUpdateEntities)
        }

        if (newApplications.isNotEmpty()) {
            val insertedIds = metabolicEventsDao.insertInsulinApplications(newApplications.map { it.toEntity() })
            newApplications.forEachIndexed { index, app ->
                if (index < insertedIds.size && insertedIds[index] != -1L) {
                    app.id = insertedIds[index]
                }
            }
        }

        // 5. Update in-memory cache
        if (to >= historyStart) {
            val updatedOrInserted = updatedApplications + newApplications
            val processedIds = (updatedOrInserted + cancelledApplications).map { it.id }.toSet()

            insulinHistory.removeIf { processedIds.contains(it.id) }

            insulinHistory.addAll(updatedOrInserted.filter { it.timestamp >= historyStart })
            insulinHistory.addAll(cancelledApplications.filter { it.timestamp >= historyStart })
            insulinHistory.sortBy { it.timestamp }
        }
    }

    /**
     * Adds a new meal entry or updates an existing one in the cache and database.
     */
    suspend fun addMealEntry(mealEntry: MealEntry) {
        val historyStart = historyStart()
        mutex.withLock {
            if (mealEntry.timestamp >= historyStart) {
                // If it's an update, remove the old one by ID
                if (mealEntry.id != ID_UNDEFINED) {
                    mealsHistory.removeIf { it.id == mealEntry.id }
                }
                // Also maintain the "unique per timestamp" rule for safety
                mealsHistory.removeIf { it.timestamp == mealEntry.timestamp }

                mealsHistory.add(mealEntry)
                mealsHistory.sortBy { it.timestamp }
            }
        }

        if (mealEntry.id != ID_UNDEFINED) {
            metabolicEventsDao.updateMeal(mealEntry.toEntity())
        } else {
            metabolicEventsDao.deleteMealsInRange(mealEntry.timestamp.ms, mealEntry.timestamp.ms)
            val id = metabolicEventsDao.insertMeal(mealEntry.toEntity())
            if (id != -1L) {
                mealEntry.id = id
            }
        }
    }

    /**
     * Deletes a meal entry from the cache and database.
     */
    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mutex.withLock {
            mealsHistory.remove(mealEntry)
        }
        metabolicEventsDao.deleteMeal(mealEntry.id)
    }

    /**
     * Returns a specific meal entry by ID.
     */
    suspend fun getMeal(id: Long): MealEntry? {
        mutex.withLock {
            val inMemory = mealsHistory.find { it.id == id }
            if (inMemory != null) return inMemory
        }

        val entity = metabolicEventsDao.getMealById(id) ?: return null
        val mealTypesMap = metabolicEventsDao.getAllMealTypes().associateBy { it.id }
        val type = mealTypesMap[entity.meal_type_id]?.toModel()
        return type?.let { entity.toModel(it) }
    }

    /**
     * Adds a new insulin application or updates an existing one in the cache and database.
     */
    suspend fun addInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        mutex.withLock {
            if (insulinApplication.timestamp >= historyStart) {
                if (insulinApplication.id != 0L) {
                    insulinHistory.removeIf { it.id == insulinApplication.id }
                }
                insulinHistory.removeIf { it.timestamp == insulinApplication.timestamp && it.origin == insulinApplication.origin }
                insulinHistory.add(insulinApplication)
                insulinHistory.sortBy { it.timestamp }
            }
        }

        if (insulinApplication.id != 0L) {
            metabolicEventsDao.updateInsulinApplication(insulinApplication.toEntity())
        } else {
            metabolicEventsDao.deleteInsulinApplicationsInRange(
                insulinApplication.timestamp.ms,
                insulinApplication.timestamp.ms,
                insulinApplication.origin
            )
            val id = metabolicEventsDao.insertInsulinApplication(insulinApplication.toEntity())
            if (id != -1L) {
                insulinApplication.id = id
            }
        }
    }

    /**
     * Updates an existing insulin application in the cache and database.
     */
    suspend fun updateInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        mutex.withLock {
            if (insulinApplication.timestamp >= historyStart) {
                insulinHistory.removeIf { it.id == insulinApplication.id }
                insulinHistory.add(insulinApplication)
                insulinHistory.sortBy { it.timestamp }
            }
        }
        metabolicEventsDao.updateInsulinApplication(insulinApplication.toEntity())
    }

    /**
     * Deletes an insulin application from the cache and database.
     */
    suspend fun removeInsulinApplication(insulinApplication: InsulinApplication) {
        mutex.withLock {
            insulinHistory.remove(insulinApplication)
        }
        metabolicEventsDao.deleteInsulinApplication(insulinApplication.id)
    }

    /**
     * Returns a flattened list of meal entries within the optional timestamp range from the cache.
     */
    suspend fun getMeals(from: Timestamp? = null, to: Timestamp? = null): List<MealEntry> = mutex.withLock {
        return mealsHistory.filter { meal ->
            (from == null || meal.timestamp >= from) && (to == null || meal.timestamp <= to)
        }.toList()
    }

    /**
     * Returns a flattened list of insulin applications within the optional timestamp range from the cache.
     */
    suspend fun getInsulinApplications(
        from: Timestamp? = null,
        to: Timestamp? = null,
        includeCancelled: Boolean = false
    ): List<InsulinApplication> = mutex.withLock {
        return insulinHistory.filter { insulin ->
            (includeCancelled || insulin.status != InsulinStatus.Cancelled) &&
            (from == null || insulin.timestamp >= from) &&
            (to == null || insulin.timestamp <= to)
        }.toList()
    }

    /**
     * Returns a list of scheduled (unconfirmed) insulin applications from the cache.
     */
    suspend fun getScheduledInsulinApplications(): List<InsulinApplication> = mutex.withLock {
        return insulinHistory.filter { it.status == InsulinStatus.Scheduled }.toList()
    }

    /**
     * Returns a scheduled (unconfirmed) insulin application by its ID, if present.
     */
    suspend fun getScheduledInsulinApplication(id: Long): InsulinApplication? = mutex.withLock {
        return insulinHistory.find { it.id == id && it.status == InsulinStatus.Scheduled }
    }

    /**
     * Confirms a scheduled insulin application by setting its status to Confirmed,
     * and updating its timestamp and delivered amount.
     * @return `true` if the scheduled application was found and confirmed, `false` otherwise.
     */
    suspend fun confirmScheduledBolus(
        id: Long,
        timestamp: Timestamp,
        deliveredAmount: InsulinAmount
    ): Boolean = mutex.withLock {
        val application = insulinHistory.find { it.id == id && it.status == InsulinStatus.Scheduled } ?: return@withLock false
        val finalAmount = if (deliveredAmount > InsulinAmount.ZERO) deliveredAmount else application.dose.amount
        val finalTimestamp = if (timestamp.isValid()) timestamp else application.dose.timestamp

        val updated = application.copy(
            dose = application.dose.copy(
                timestamp = finalTimestamp,
                amount = finalAmount
            ),
            status = InsulinStatus.Confirmed
        )
        insulinHistory.removeIf { it.id == id }
        insulinHistory.add(updated)
        insulinHistory.sortBy { it.timestamp }

        metabolicEventsDao.updateInsulinApplication(updated.toEntity())
        return@withLock true
    }

    // --- Meal Types ---

    /**
     * Returns all available meal types.
     */
    suspend fun getAllMealTypes(): List<MealType> = mutex.withLock {
        return mealTypes.toList()
    }

    /**
     * Inserts or updates a meal type in the database and updates the in-memory list.
     */
    suspend fun insertMealType(mealType: MealType) {
        metabolicEventsDao.insertMealType(mealType.toEntity())
        mutex.withLock {
            mealTypes = (mealTypes.filter { it.id != mealType.id } + mealType).sortedBy { it.name }
        }
    }

    /**
     * Deletes a meal type from the database and updates the in-memory list.
     */
    suspend fun deleteMealType(mealType: MealType) {
        metabolicEventsDao.deleteMealType(mealType.id)
        mutex.withLock {
            mealTypes = mealTypes.filter { it.id != mealType.id }
        }
    }

    // --- Insulin Types ---

    /**
     * Returns all available insulin types.
     */
    suspend fun getAllInsulinTypes(): List<InsulinType> = mutex.withLock {
        return insulinTypes.toList()
    }

    /**
     * Inserts or updates an insulin type in the database and updates the in-memory list.
     */
    suspend fun insertInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.insertInsulinType(insulinType.toEntity())
        mutex.withLock {
            insulinTypes = (insulinTypes.filter { it.id != insulinType.id } + insulinType).sortedBy { it.name }
        }
    }

    /**
     * Deletes an insulin type from the database and updates the in-memory list.
     */
    suspend fun deleteInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.deleteInsulinType(insulinType.id)
        mutex.withLock {
            insulinTypes = insulinTypes.filter { it.id != insulinType.id }
        }
    }

    // --- Deferred Boluses ---

    suspend fun addDeferredBolus(deferredBolus: DeferredBolus) {
        val historyStart = historyStart()
        mutex.withLock {
            if (deferredBolus.timestamp >= historyStart) {
                deferredBoluses.add(deferredBolus)
                deferredBoluses.sortBy { it.timestamp }
            }
        }
        val id = metabolicEventsDao.insertDeferredBolus(deferredBolus.toEntity())
        if (id != -1L) {
            deferredBolus.id = id
        }
    }

    suspend fun getDeferredBoluses(): List<DeferredBolus> = mutex.withLock {
        return deferredBoluses.toList()
    }

    suspend fun updateDeferredBolus(deferredBolus: DeferredBolus) {
        mutex.withLock {
            deferredBoluses.removeIf { it.id == deferredBolus.id }
            deferredBoluses.add(deferredBolus)
            deferredBoluses.sortBy { it.timestamp }
        }
        metabolicEventsDao.updateDeferredBolus(deferredBolus.toEntity())
    }

    suspend fun removeDeferredBolus(deferredBolus: DeferredBolus) {
        mutex.withLock {
            deferredBoluses.removeIf { it.id == deferredBolus.id }
        }
        metabolicEventsDao.deleteDeferredBolus(deferredBolus.id)
    }

    suspend fun removeDeferredBoluses(deferredBolusesToRemove: Collection<DeferredBolus>) {
        val idsToRemove = deferredBolusesToRemove.map { it.id }.filter { it != ID_UNDEFINED }
        if (idsToRemove.isEmpty()) return

        mutex.withLock {
            deferredBoluses.removeIf { bolus -> idsToRemove.contains(bolus.id) }
        }
        metabolicEventsDao.deleteDeferredBoluses(idsToRemove)
    }

    suspend fun addScheduledPumpInsulinEntry(
        timestamp: Timestamp,
        amount: InsulinAmount,
        insulinType: InsulinType,
        basal: Boolean,
        correction: Boolean,
        meal: Boolean
    ): InsulinApplication {
        val application = InsulinApplication(
            timestamp = timestamp,
            amount = amount,
            insulinType = insulinType,
            origin = InsulinOrigin.Pump,
            basal = basal,
            correction = correction,
            meal = meal,
            status = InsulinStatus.Scheduled
        )
        addInsulinApplication(application)
        return application
    }

    suspend fun setInsulinAdministered(administeredMealIds: MutableSet<Long>) {
        if (administeredMealIds.isNotEmpty()) {
            metabolicEventsDao.markMealsAsInsulinAdministered(administeredMealIds.toList())
        }
    }
}