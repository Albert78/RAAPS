package de.dh.raaps.core.aps

import de.dh.raaps.common.model.InsulinApplication
import de.dh.raaps.common.model.MealEntry
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.DataRepository
import java.util.NavigableMap
import java.util.TreeMap

class MetabolicEventsModel(
    val historySize: Minutes,
    val dataRepository: DataRepository
) {
    var mealsHistory: NavigableMap<Timestamp, MutableList<MealEntry>> = TreeMap()
    var insulinApplicationsHistory: NavigableMap<Timestamp, MutableList<InsulinApplication>> = TreeMap()

    fun historyStart(): Timestamp = Timestamp.now().minus(historySize)

    suspend fun load() {
        val historyStart = Timestamp.now() - historySize
        mealsHistory = dataRepository.loadMeals(from = historyStart)
            .groupByTo(TreeMap()) { it.timestamp }
        insulinApplicationsHistory = dataRepository.loadInsulinApplications(from = historyStart)
            .groupByTo(TreeMap()) { it.timestamp }
    }

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
            throw IllegalArgumentException("Meal entry is too old to add to MetabolicEventsModel. History starts at $historyStart, meal to add: $mealEntry")
        }
        mealsHistory
            .computeIfAbsent(mealEntry.timestamp, { _ -> mutableListOf() })
            .add(mealEntry)
        dataRepository.insertMeal(mealEntry)
    }

    suspend fun removeMealEntry(mealEntry: MealEntry) {
        mealsHistory[mealEntry.timestamp]?.remove(mealEntry)
        dataRepository.deleteMeal(mealEntry)
    }

    suspend fun addInsulinApplication(insulinApplication: InsulinApplication) {
        val historyStart = historyStart()
        if (insulinApplication.timestamp < historyStart) {
            throw IllegalArgumentException("Insulin application is too old to add to MetabolicEventsModel. History starts at $historyStart, insulin application to add: $insulinApplication")
        }
        insulinApplicationsHistory
            .computeIfAbsent(insulinApplication.timestamp, { _ -> mutableListOf() })
            .add(insulinApplication)
        dataRepository.insertInsulinApplication(insulinApplication)
    }

    suspend fun removeInsulinApplication(insulinApplication: InsulinApplication) {
        insulinApplicationsHistory[insulinApplication.timestamp]?.remove(insulinApplication)
        dataRepository.deleteInsulinApplication(insulinApplication)
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
}