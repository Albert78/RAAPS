package de.dh.raaps.core.repository

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
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
    var insulinApplicationsHistory: NavigableMap<Timestamp, MutableList<InsulinApplication>> = TreeMap()

    fun historyStart(): Timestamp = Timestamp.now().minus(historySize)

    /**
     * Loads the history from the database into the in-memory cache.
     */
    suspend fun load() {
        val historyStart = Timestamp.now() - historySize

        // Load Meals
        val mealEntities = metabolicEventsDao.getMealsInRange(historyStart.ms, Long.MAX_VALUE)
        val mealTypes = metabolicEventsDao.getAllMealTypes().associateBy { it.id }
        mealsHistory = mealEntities.mapNotNull { entity ->
            val typeEntity = mealTypes[entity.meal_type_id]
            typeEntity?.let { entity.toModel(it.toModel()) }
        }.groupByTo(TreeMap()) { it.timestamp }

        // Load Insulin Applications
        val insulinEntities = metabolicEventsDao.getInsulinApplicationsInRange(historyStart.ms, Long.MAX_VALUE)
        val insulinTypes = metabolicEventsDao.getAllInsulinTypes().associateBy { it.id }
        insulinApplicationsHistory = insulinEntities.mapNotNull { entity ->
            val typeEntity = insulinTypes[entity.insulin_type_id]
            typeEntity?.let { entity.toModel(it.toModel()) }
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
        if (insulinApplicationsHistory.isNotEmpty() && insulinApplicationsHistory.firstKey() < historyStart) {
            insulinApplicationsHistory.headMap(historyStart, false).clear()
        }
    }

    suspend fun addMealEntry(mealEntry: MealEntry) {
        val historyStart = historyStart()
        if (mealEntry.timestamp < historyStart) {
            throw IllegalArgumentException("Meal entry is too old to add to TreatmentRepository. History starts at $historyStart, meal to add: $mealEntry")
        }
        mealsHistory
            .computeIfAbsent(mealEntry.timestamp, { _ -> mutableListOf() })
            .add(mealEntry)

        val id = metabolicEventsDao.insertMeal(mealEntry.toEntity())
        if (id != -1L) {
            mealEntry.id = id
        }
    }

    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mealsHistory[mealEntry.timestamp]?.remove(mealEntry)
        metabolicEventsDao.deleteMeal(mealEntry.id)
    }

    suspend fun addInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        if (insulinApplication.timestamp < historyStart) {
            throw IllegalArgumentException("Insulin application is too old to add to TreatmentRepository. History starts at $historyStart, insulin application to add: $insulinApplication")
        }
        insulinApplicationsHistory
            .computeIfAbsent(insulinApplication.timestamp, { _ -> mutableListOf() })
            .add(insulinApplication)

        val id = metabolicEventsDao.insertInsulinApplication(insulinApplication.toEntity())
        if (id != -1L) {
            insulinApplication.id = id
        }
    }

    suspend fun removeInsulinApplication(insulinApplication: InsulinApplication) {
        insulinApplicationsHistory[insulinApplication.timestamp]?.remove(insulinApplication)
        metabolicEventsDao.deleteInsulinApplication(insulinApplication.id)
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

    fun getInsulinApplications(from: Timestamp? = null, to: Timestamp? = null): List<InsulinApplication> {
        val map = if (from == null && to == null) {
            insulinApplicationsHistory
        } else if (to == null) {
            insulinApplicationsHistory.tailMap(from)
        } else {
            insulinApplicationsHistory.subMap(from, to)
        }
        return map.values.flatten()
    }

    // --- Meal Types ---

    suspend fun getAllMealTypes(): List<de.dh.raaps.common.model.MealType> {
        return metabolicEventsDao.getAllMealTypes().map { it.toModel() }
    }

    suspend fun insertMealType(mealType: de.dh.raaps.common.model.MealType) {
        metabolicEventsDao.insertMealType(mealType.toEntity())
    }

    suspend fun deleteMealType(mealType: de.dh.raaps.common.model.MealType) {
        metabolicEventsDao.deleteMealType(mealType.id)
    }

    // --- Insulin Types ---

    suspend fun getAllInsulinTypes(): List<de.dh.raaps.common.model.InsulinType> {
        return metabolicEventsDao.getAllInsulinTypes().map { it.toModel() }
    }

    suspend fun insertInsulinType(insulinType: de.dh.raaps.common.model.InsulinType) {
        metabolicEventsDao.insertInsulinType(insulinType.toEntity())
    }

    suspend fun deleteInsulinType(insulinType: de.dh.raaps.common.model.InsulinType) {
        metabolicEventsDao.deleteInsulinType(insulinType.id)
    }
}