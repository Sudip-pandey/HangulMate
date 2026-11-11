package com.hangulmate.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

/**
 * Room converters for list columns. Lists are stored as JSON arrays
 * (org.json — part of the Android framework, no extra dependency).
 * Malformed stored JSON degrades to an empty list instead of crashing.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            List(array.length()) { i -> array.optString(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromIntList(value: List<Int>): String {
        val array = JSONArray()
        value.forEach { array.put(it) }
        return array.toString()
    }

    @TypeConverter
    fun toIntList(value: String): List<Int> {
        if (value.isBlank()) return emptyList()
        return try {
            val array = JSONArray(value)
            List(array.length()) { i -> array.optInt(i) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
