package de.dh.raaps.core.repository

import de.dh.raaps.common.model.InsulinApplication
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
import java.util.NavigableMap
import java.util.TreeMap

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

    var mealsHistory: NavigableMap<Timestamp, MutableList<MealEntry>> = TreeMap()
    var insulinHistory: NavigableMap<Timestamp, MutableList<InsulinApplication>> = TreeMap()
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
     * Calculates the tick index (intervals since epoch) for a given timestamp.
     */
    fun tickForTimestamp(timestamp: Timestamp): Int {
        val minutesPerTick = 60 / BASAL_TICKS_PER_HOUR
        return (timestamp.ms / 60_000 / minutesPerTick).toInt()
    }

    fun startOfTick(tick: Int): Timestamp {
        val minutesPerTick = 60 / BASAL_TICKS_PER_HOUR
        return Timestamp(tick * 60_000L * minutesPerTick)
    }

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
        }.groupByTo(TreeMap()) { it.timestamp }

        // Load Insulin Types
        insulinTypes = metabolicEventsDao.getAllInsulinTypes().map { it.toModel() }
        val insulinTypesMap = insulinTypes.associateBy { it.id }

        // Load Insulin
        val insulinEntities = metabolicEventsDao.getInsulinApplicationsSince(historyStart.ms)
        insulinHistory = insulinEntities.mapNotNull { entity ->
            val type = insulinTypesMap[entity.insulin_type_id]
            type?.let { entity.toModel(it) }
        }.groupByTo(TreeMap()) { it.timestamp }
    }

    /**
     * Removes entries from the in-memory cache that are older than the history size.
     */
    fun prune() {
        val historyStart = historyStart()
        if (mealsHistory.isNotEmpty() && mealsHistory.firstKey() < historyStart) {
            mealsHistory.headMap(historyStart, false).clear()
        }
        if (insulinHistory.isNotEmpty() && insulinHistory.firstKey() < historyStart) {
            insulinHistory.headMap(historyStart, false).clear()
        }
    }

    /**
     * Persists or updates an insulin application in the cache and database.
     * For "Basal" entries from the Pump, it replaces existing entries at the same timestamp.
     */
    suspend fun updateInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        if (insulinApplication.timestamp >= historyStart) {
            val list = insulinHistory.computeIfAbsent(insulinApplication.timestamp) { mutableListOf() }
            if (insulinApplication.reason == "Basal" && insulinApplication.origin == InsulinOrigin.Pump) {
                list.removeIf { it.reason == "Basal" && it.origin == InsulinOrigin.Pump }
                metabolicEventsDao.deleteInsulinApplicationByUniqueKey(insulinApplication.timestamp.ms, "Basal", InsulinOrigin.Pump)
            } else {
                list.removeIf { it.id == insulinApplication.id }
            }
            list.add(insulinApplication)
        }
        val id = metabolicEventsDao.insertInsulinApplication(insulinApplication.toEntity())
        if (id != -1L) {
            insulinApplication.id = id
        }
    }

    /**
     * Adds a new meal entry to the cache and database.
     */
    suspend fun addMealEntry(mealEntry: MealEntry) {
        val historyStart = historyStart()
        if (mealEntry.timestamp >= historyStart) {
            mealsHistory
                .computeIfAbsent(mealEntry.timestamp) { mutableListOf() }
                .add(mealEntry)
        }

        val id = metabolicEventsDao.insertMeal(mealEntry.toEntity())
        if (id != -1L) {
            mealEntry.id = id
        }
    }

    /**
     * Deletes a meal entry from the cache and database.
     */
    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mealsHistory[mealEntry.timestamp]?.remove(mealEntry)
        metabolicEventsDao.deleteMeal(mealEntry.id)
    }

    /**
     * Adds a new insulin application to the cache and database.
     */
    suspend fun addInsulinApplication(insulinApplication: InsulinApplication) {
        updateInsulinApplication(insulinApplication)
    }

    /**
     * Deletes an insulin application from the cache and database.
     */
    suspend fun removeInsulinApplication(insulinApplication: InsulinApplication) {
        insulinHistory[insulinApplication.timestamp]?.remove(insulinApplication)
        metabolicEventsDao.deleteInsulinApplication(insulinApplication.id)
    }

    /**
     * Deletes all insulin applications with the specified origin from the cache and database.
     */
    suspend fun clearInsulinApplicationsByOrigin(origin: InsulinOrigin) {
        // Clear from cache
        insulinHistory.values.forEach { list ->
            list.removeAll { it.origin == origin }
        }
        insulinHistory.entries.removeIf { it.value.isEmpty() }

        // Clear from database
        metabolicEventsDao.deleteInsulinApplicationsByOrigin(origin)
    }

    /**
     * Deletes all bolus insulin applications with the specified origin from the cache and database.
     */
    suspend fun clearBolusesByOrigin(origin: InsulinOrigin) {
        // Clear from cache
        insulinHistory.values.forEach { list ->
            list.removeAll { it.origin == origin && it.reason != "Basal" }
        }
        insulinHistory.entries.removeIf { it.value.isEmpty() }

        // Clear from database
        metabolicEventsDao.deleteBolusApplicationsByOrigin(origin)
    }

    /**
     * Returns a flattened list of meal entries within the optional timestamp range from the cache.
     */
    fun getMeals(from: Timestamp? = null, to: Timestamp? = null): List<MealEntry> {
        val map = if (from == null && to == null) {
            mealsHistory
        } else if (to == null) {
            mealsHistory.tailMap(from)
        } else {
            mealsHistory.subMap(from, to)
        }
        return map.values.flatten()
    }

    /**
     * Returns a flattened list of insulin applications within the optional timestamp range from the cache.
     */
    fun getInsulinApplications(from: Timestamp? = null, to: Timestamp? = null): List<InsulinApplication> {
        val map = if (from == null && to == null) {
            insulinHistory
        } else if (to == null) {
            insulinHistory.tailMap(from)
        } else {
            insulinHistory.subMap(from, to)
        }
        return map.values.flatten()
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

    companion object {
        val BASAL_TICKS_PER_HOUR = 3
    }
}