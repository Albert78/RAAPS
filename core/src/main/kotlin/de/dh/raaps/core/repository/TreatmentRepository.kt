package de.dh.raaps.core.repository

import de.dh.raaps.common.model.INSULIN_EPSILON
import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.InsulinHistory
import de.dh.raaps.common.model.InsulinOrigin
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.MetabolicEventsDao
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    var mealsHistory: MutableList<MealEntry> = mutableListOf()
    var insulinHistory: MutableList<InsulinApplication> = mutableListOf()
    var mealTypes: List<MealType> = emptyList()
    var insulinTypes: List<InsulinType> = emptyList()

    fun observeMeals(): Flow<List<MealEntry>> = metabolicEventsDao.observeAllMeals()
        .map { entities ->
            val mealTypesMap = metabolicEventsDao.getAllMealTypes().associateBy { it.id }
            entities.mapNotNull { entity ->
                val type = mealTypesMap[entity.meal_type_id]?.toModel()
                type?.let { entity.toModel(it) }
            }
        }

    fun observeInsulinApplications(): Flow<List<InsulinApplication>> = metabolicEventsDao.observeAllInsulinApplications()
        .map { entities ->
            val insulinTypesMap = metabolicEventsDao.getAllInsulinTypes().associateBy { it.id }
            entities.mapNotNull { entity ->
                val type = insulinTypesMap[entity.insulin_type_id]?.toModel()
                type?.let { entity.toModel(it) }
            }
        }

    /**
     * Returns the timestamp where the in-memory history starts.
     */
    fun historyStart(): Timestamp = Timestamp.now().minus(historySize)

    /**
     * Loads the history from the database into the in-memory cache.
     */
    suspend fun load() {
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
    }

    /**
     * Removes entries from the in-memory cache that are older than the history size.
     */
    fun prune() {
        val historyStart = historyStart()
        mealsHistory.removeIf { it.timestamp < historyStart }
        insulinHistory.removeIf { it.timestamp < historyStart }
    }

    /**
     * Merges a range of insulin history from the pump.
     * Replaces all existing Pump entries in the given range.
     */
    suspend fun mergeInsulinHistory(history: InsulinHistory, insulinType: InsulinType) {
        val from = Timestamp(history.from)
        val to = Timestamp(history.to)
        val historyStart = historyStart()

        val newApplications = history.points.map { point ->
            InsulinApplication(
                timestamp = Timestamp(point.timestamp),
                amount = point.amount,
                insulinType = insulinType,
                origin = InsulinOrigin.Pump
            )
        }.filter { it.amount > INSULIN_EPSILON && it.timestamp >= from && it.timestamp <= to }

        // 1. Database sync
        metabolicEventsDao.deleteInsulinApplicationsInRange(from.ms, to.ms, InsulinOrigin.Pump)
        newApplications.forEach { metabolicEventsDao.insertInsulinApplication(it.toEntity()) }

        // 2. In-memory sync
        if (to >= historyStart) {
            val effectiveFrom = if (from < historyStart) historyStart else from
            insulinHistory.removeIf { it.origin == InsulinOrigin.Pump && it.timestamp >= effectiveFrom && it.timestamp <= to }
            insulinHistory.addAll(newApplications.filter { it.timestamp >= historyStart })
            insulinHistory.sortBy { it.timestamp }
        }
    }

    /**
     * Adds a new meal entry to the cache and database.
     * Overwrites if timestamp is the same.
     */
    suspend fun addMealEntry(mealEntry: MealEntry) {
        val historyStart = historyStart()
        if (mealEntry.timestamp >= historyStart) {
            mealsHistory.removeIf { it.timestamp == mealEntry.timestamp }
            mealsHistory.add(mealEntry)
            mealsHistory.sortBy { it.timestamp }
        }

        metabolicEventsDao.deleteMealsInRange(mealEntry.timestamp.ms, mealEntry.timestamp.ms)
        val id = metabolicEventsDao.insertMeal(mealEntry.toEntity())
        if (id != -1L) {
            mealEntry.id = id
        }
    }

    /**
     * Deletes a meal entry from the cache and database.
     */
    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mealsHistory.remove(mealEntry)
        metabolicEventsDao.deleteMeal(mealEntry.id)
    }

    /**
     * Adds a new insulin application to the cache and database.
     * Overwrites if timestamp and origin are the same.
     */
    suspend fun addInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        if (insulinApplication.timestamp >= historyStart) {
            insulinHistory.removeIf { it.timestamp == insulinApplication.timestamp && it.origin == insulinApplication.origin }
            insulinHistory.add(insulinApplication)
            insulinHistory.sortBy { it.timestamp }
        }
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

    /**
     * Deletes an insulin application from the cache and database.
     */
    suspend fun removeInsulinApplication(insulinApplication: InsulinApplication) {
        insulinHistory.remove(insulinApplication)
        metabolicEventsDao.deleteInsulinApplication(insulinApplication.id)
    }

    /**
     * Returns a flattened list of meal entries within the optional timestamp range from the cache.
     */
    fun getMeals(from: Timestamp? = null, to: Timestamp? = null): List<MealEntry> {
        return mealsHistory.filter { meal ->
            (from == null || meal.timestamp >= from) && (to == null || meal.timestamp <= to)
        }
    }

    /**
     * Returns a flattened list of insulin applications within the optional timestamp range from the cache.
     */
    fun getInsulinApplications(from: Timestamp? = null, to: Timestamp? = null): List<InsulinApplication> {
        return insulinHistory.filter { insulin ->
            (from == null || insulin.timestamp >= from) && (to == null || insulin.timestamp <= to)
        }
    }

    // --- Meal Types ---

    /**
     * Returns all available meal types.
     */
    fun getAllMealTypes(): List<MealType> {
        return mealTypes
    }

    /**
     * Inserts or updates a meal type in the database and updates the in-memory list.
     */
    suspend fun insertMealType(mealType: MealType) {
        metabolicEventsDao.insertMealType(mealType.toEntity())
        mealTypes = (mealTypes.filter { it.id != mealType.id } + mealType).sortedBy { it.name }
    }

    /**
     * Deletes a meal type from the database and updates the in-memory list.
     */
    suspend fun deleteMealType(mealType: MealType) {
        metabolicEventsDao.deleteMealType(mealType.id)
        mealTypes = mealTypes.filter { it.id != mealType.id }
    }

    // --- Insulin Types ---

    /**
     * Returns all available insulin types.
     */
    fun getAllInsulinTypes(): List<InsulinType> {
        return insulinTypes
    }

    /**
     * Inserts or updates an insulin type in the database and updates the in-memory list.
     */
    suspend fun insertInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.insertInsulinType(insulinType.toEntity())
        insulinTypes = (insulinTypes.filter { it.id != insulinType.id } + insulinType).sortedBy { it.name }
    }

    /**
     * Deletes an insulin type from the database and updates the in-memory list.
     */
    suspend fun deleteInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.deleteInsulinType(insulinType.id)
        insulinTypes = insulinTypes.filter { it.id != insulinType.id }
    }
}