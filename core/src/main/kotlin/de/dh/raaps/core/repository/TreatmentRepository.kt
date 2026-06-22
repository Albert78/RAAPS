package de.dh.raaps.core.repository

import de.dh.raaps.common.model.Bolus
import de.dh.raaps.common.model.InsulinType
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.MealType
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.AppDatabase
import de.dh.raaps.core.repository.db.MetabolicEventsDao
import de.dh.raaps.core.repository.db.toEntity
import de.dh.raaps.core.repository.db.toModel
import java.util.NavigableMap
import java.util.TreeMap

/**
 * Repository for active metabolic treatments (Insulin and Meals).
 * It maintains an in-memory cache for fast access by the APS core and handles persistence via DAOs.
 */
class TreatmentRepository(
    val historySize: Minutes,
    appDatabase: AppDatabase
) {
    private val metabolicEventsDao: MetabolicEventsDao = appDatabase.metabolicEventsDao()

    var mealsHistory: NavigableMap<Timestamp, MutableList<MealEntry>> = TreeMap()
    var bolusHistory: NavigableMap<Timestamp, MutableList<Bolus>> = TreeMap()
    var mealTypes: List<MealType> = emptyList()
    var insulinTypes: List<InsulinType> = emptyList()

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
        }.groupByTo(TreeMap()) { it.timestamp }

        // Load Insulin Types
        insulinTypes = metabolicEventsDao.getAllInsulinTypes().map { it.toModel() }
        val insulinTypesMap = insulinTypes.associateBy { it.id }

        // Load Bolus
        val bolusEntities = metabolicEventsDao.getBolusesSince(historyStart.ms)
        bolusHistory = bolusEntities.mapNotNull { entity ->
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
        if (bolusHistory.isNotEmpty() && bolusHistory.firstKey() < historyStart) {
            bolusHistory.headMap(historyStart, false).clear()
        }
    }

    suspend fun addMealEntry(mealEntry: MealEntry) {
        val historyStart = historyStart()
        if (mealEntry.timestamp >= historyStart) {
            mealsHistory
                .computeIfAbsent(mealEntry.timestamp, { _ -> mutableListOf() })
                .add(mealEntry)
        }

        val id = metabolicEventsDao.insertMeal(mealEntry.toEntity())
        if (id != -1L) {
            mealEntry.id = id
        }
    }

    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mealsHistory[mealEntry.timestamp]?.remove(mealEntry)
        metabolicEventsDao.deleteMeal(mealEntry.id)
    }

    suspend fun addBolus(bolus: Bolus) {
        val historyStart = historyStart()
        if (bolus.timestamp >= historyStart) {
            bolusHistory
                .computeIfAbsent(bolus.timestamp, { _ -> mutableListOf() })
                .add(bolus)
        }

        val id = metabolicEventsDao.insertBolus(bolus.toEntity())
        if (id != -1L) {
            bolus.id = id
        }
    }

    suspend fun removeBolus(bolus: Bolus) {
        bolusHistory[bolus.timestamp]?.remove(bolus)
        metabolicEventsDao.deleteBolus(bolus.id)
    }

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

    fun getBoluses(from: Timestamp? = null, to: Timestamp? = null): List<Bolus> {
        val map = if (from == null && to == null) {
            bolusHistory
        } else if (to == null) {
            bolusHistory.tailMap(from)
        } else {
            bolusHistory.subMap(from, to)
        }
        return map.values.flatten()
    }

    // --- Meal Types ---

    fun getAllMealTypes(): List<MealType> {
        return mealTypes
    }

    suspend fun insertMealType(mealType: MealType) {
        metabolicEventsDao.insertMealType(mealType.toEntity())
        mealTypes = (mealTypes.filter { it.id != mealType.id } + mealType).sortedBy { it.name }
    }

    suspend fun deleteMealType(mealType: MealType) {
        metabolicEventsDao.deleteMealType(mealType.id)
        mealTypes = mealTypes.filter { it.id != mealType.id }
    }

    // --- Insulin Types ---

    fun getAllInsulinTypes(): List<InsulinType> {
        return insulinTypes
    }

    suspend fun insertInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.insertInsulinType(insulinType.toEntity())
        insulinTypes = (insulinTypes.filter { it.id != insulinType.id } + insulinType).sortedBy { it.name }
    }

    suspend fun deleteInsulinType(insulinType: InsulinType) {
        metabolicEventsDao.deleteInsulinType(insulinType.id)
        insulinTypes = insulinTypes.filter { it.id != insulinType.id }
    }
}