package com.piper.secretaria.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun eventTypeToString(t: EventType): String = t.name
    @TypeConverter fun stringToEventType(s: String): EventType =
        runCatching { EventType.valueOf(s) }.getOrDefault(EventType.MEETING)

    @TypeConverter fun categoryToString(c: NotificationCategory): String = c.name
    @TypeConverter fun stringToCategory(s: String): NotificationCategory =
        runCatching { NotificationCategory.valueOf(s) }.getOrDefault(NotificationCategory.SISTEMA)
}

@Database(
    entities = [TaskEntity::class, EventEntity::class, NoteEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class PiperDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun events(): EventDao
    abstract fun notes(): NoteDao
    abstract fun notifications(): NotificationDao

    companion object {
        fun create(context: Context): PiperDatabase =
            Room.databaseBuilder(context, PiperDatabase::class.java, "piper.db").build()
    }
}
