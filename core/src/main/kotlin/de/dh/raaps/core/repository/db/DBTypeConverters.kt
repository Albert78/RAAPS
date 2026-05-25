package de.dh.raaps.core.repository.db

import androidx.room.TypeConverter
import de.dh.raaps.common.model.data.Minutes
import de.dh.raaps.common.model.data.Timestamp
import de.dh.raaps.core.repository.db.entities.DBBlock
import de.dh.raaps.core.repository.db.entities.DBTargetBlock
import org.json.JSONArray
import org.json.JSONObject

class DbTypeConverters {
    @TypeConverter
    fun fromMinutes(minutes: Minutes?): Short? = minutes?.value

    @TypeConverter
    fun toMinutes(value: Short?): Minutes? = value?.let { Minutes(it) }

    @TypeConverter
    fun fromTimestamp(timestamp: Timestamp?): Long? = timestamp?.ms

    @TypeConverter
    fun toTimestamp(value: Long?): Timestamp? = value?.let { Timestamp(it) }

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
    fun fromListOfTargetBlocks(blocks: List<DBTargetBlock>?): String? {
        if (blocks == null) return null
        val jsonArray = JSONArray()
        blocks.forEach {
            val jsonObject = JSONObject()
            jsonObject.put("duration", it.duration)
            jsonObject.put("lowTarget", it.lowTarget)
            jsonObject.put("highTarget", it.highTarget)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    @TypeConverter
    fun toListOfTargetBlocks(jsonString: String?): List<DBTargetBlock>? {
        if (jsonString == null) return null
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<DBTargetBlock>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            list.add(
                DBTargetBlock(
                    jsonObject.getInt("duration").toShort(),
                    jsonObject.getInt("lowTarget").toShort(),
                    jsonObject.getInt("highTarget").toShort()
                )
            )
        }
        return list
    }
}