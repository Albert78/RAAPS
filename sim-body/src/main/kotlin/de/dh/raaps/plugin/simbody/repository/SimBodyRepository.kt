package de.dh.raaps.plugin.simbody.repository

import de.dh.raaps.common.model.data.Block
import de.dh.raaps.plugin.simbody.model.BodyProfile
import de.dh.raaps.plugin.simbody.repository.db.BodyProfileEntity
import de.dh.raaps.plugin.simbody.repository.db.ImpactDao
import de.dh.raaps.plugin.simbody.repository.db.SimulationStateEntity
import org.json.JSONArray
import org.json.JSONObject

class SimBodyRepository(private val impactDao: ImpactDao) {

    suspend fun getSimulationState() = impactDao.getSimulationState()

    suspend fun updateSimulationState(
        currentBgMgDl: Int,
        lastTickTimestampMs: Long,
        exerciseIntensity: Double,
        stressLevel: Double,
        illnessFactor: Double
    ) {
        impactDao.updateSimulationState(
            SimulationStateEntity(
                currentBgMgDl = currentBgMgDl,
                lastTickTimestampMs = lastTickTimestampMs,
                exerciseIntensity = exerciseIntensity,
                stressLevel = stressLevel,
                illnessFactor = illnessFactor
            )
        )
    }

    suspend fun getActiveBodyProfile(): BodyProfile? {
        val entity = impactDao.getActiveBodyProfile() ?: return null
        return BodyProfile(
            icBlocks = parseBlocks(entity.icBlocks),
            isfBlocks = parseBlocks(entity.isfBlocks),
            liverGlucoseOutputBlocks = parseBlocks(entity.liverGlucoseOutputBlocks)
        )
    }

    private fun parseBlocks(json: String): List<Block> {
        val array = JSONArray(json)
        val list = mutableListOf<Block>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(Block(
                duration = de.dh.raaps.common.model.data.Minutes(obj.getInt("duration").toShort()),
                amount = obj.getDouble("amount")
            ))
        }
        return list
    }

    private fun blocksToJson(blocks: List<Block>): String {
        val array = JSONArray()
        blocks.forEach {
            val obj = JSONObject()
            obj.put("duration", it.duration.value)
            obj.put("amount", it.amount)
            array.put(obj)
        }
        return array.toString()
    }

    suspend fun saveBodyProfile(name: String, profile: BodyProfile, isActive: Boolean = false) {
        impactDao.insertBodyProfile(
            BodyProfileEntity(
                name = name,
                icBlocks = blocksToJson(profile.icBlocks),
                isfBlocks = blocksToJson(profile.isfBlocks),
                liverGlucoseOutputBlocks = blocksToJson(profile.liverGlucoseOutputBlocks),
                isActive = isActive
            )
        )
    }
}
