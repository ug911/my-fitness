package com.ug911.myfitness.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun dateToString(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    /** Lists are stored as a JSON array so option labels can contain anything. */
    @TypeConverter
    fun listToString(values: List<String>): String = Json.encodeToString(STRING_LIST, values)

    @TypeConverter
    fun stringToList(value: String): List<String> = when {
        value.isBlank() -> emptyList()
        else -> runCatching { Json.decodeFromString(STRING_LIST, value) }.getOrDefault(emptyList())
    }

    private companion object {
        val STRING_LIST = ListSerializer(String.serializer())
    }
}
