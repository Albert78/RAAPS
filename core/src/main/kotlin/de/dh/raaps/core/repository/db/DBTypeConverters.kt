package de.dh.raaps.core.repository.db

import androidx.room.TypeConverter
import de.dh.raaps.common.model.ApsMode
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.aps.AlgorithmReasoning
import de.dh.raaps.core.repository.db.entities.DBBlock
import de.dh.raaps.core.repository.db.entities.DBBgBlock
import org.json.JSONArray
import org.json.JSONObject

class DbTypeConverters {
    @TypeConverter
    fun fromApsMode(mode: ApsMode): String = mode.name

    @TypeConverter
    fun toApsMode(value: String): ApsMode {
        try {
            return ApsMode.valueOf(value)
        } catch (_: Exception) {
            return ApsMode.BasalOnly
        }
    }

    @TypeConverter
    fun fromMinutes(minutes: Minutes?): Short? = minutes?.value

    @TypeConverter
    fun toMinutes(value: Short?): Minutes? = value?.let { Minutes(it) }

    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? = timestamp?.ms

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? = value?.let { Timestamp(it) }

    @TypeConverter
    fun fromAlgorithmReasoning(reasoning: AlgorithmReasoning): String = reasoning.name

    @TypeConverter
    fun toAlgorithmReasoning(value: String): AlgorithmReasoning {
        return try {
            AlgorithmReasoning.valueOf(value)
        } catch (_: Exception) {
            AlgorithmReasoning.INTERNAL_ERROR
        }
    }

    @TypeConverter
    fun fromListOfBlocks(blocks: List<DBBlock>?): String? {
        if (blocks == null) return null
        val jsonArray = JSONArray()
        blocks.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("amount", it.amount)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toListOfBlocks(jsonString: String?): List<DBBlock>? {
        if (jsonString == null) return null
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<DBBlock>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(DBBlock(jsonObject.getInt("duration").toShort(), jsonObject.getDouble("amount")))
        }
        return list
    }

    @TypeConverter
    fun fromListOfBgBlocks(blocks: List<DBBgBlock>?): String? {
        if (blocks == null) return null
        val jsonArray = JSONArray()
        blocks.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("target", it.target)
            jsonObject.put("lowThreshold", it.lowThreshold)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toListOfBgBlocks(jsonString: String?): List<DBBgBlock>? {
        if (jsonString == null) return null
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<DBBgBlock>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(
                DBBgBlock(
                    jsonObject.getInt("duration").toShort(),
                    jsonObject.getInt("target").toShort(),
                    jsonObject.getInt("lowThreshold").toShort()
                )
            )
        }
        return list
    }
}
