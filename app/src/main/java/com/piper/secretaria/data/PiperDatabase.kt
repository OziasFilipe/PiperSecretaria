package com.piper.secretaria.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
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
            Room.databaseBuilder(context, PiperDatabase::class.java, "piper.db")
                .addMigrations(MIGRATION_1_2)
                .build()

        /** 1.1: eventos importados de agendas externas. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN externalId TEXT")
                db.execSQL("ALTER TABLE events ADD COLUMN source TEXT")
            }
        }
    }
}
