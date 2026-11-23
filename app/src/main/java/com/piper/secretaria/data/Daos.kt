package com.piper.secretaria.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY done ASC, CASE WHEN remindAt IS NULL THEN 1 ELSE 0 END, remindAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE done = 0 AND remindAt IS NOT NULL AND remindAt > :now")
    suspend fun pendingReminders(now: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE done = 0")
    suspend fun pending(): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY done ASC, createdAt DESC LIMIT 200")
    suspend fun all(): List<TaskEntity>

    @Insert suspend fun insert(task: TaskEntity): Long
    @Update suspend fun update(task: TaskEntity)
    @Delete suspend fun delete(task: TaskEntity)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startAt ASC")
    fun observeAll(): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE startAt >= :from AND startAt < :to ORDER BY startAt")
    suspend fun between(from: Long, to: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE startAt > :now OR (snoozedUntil IS NOT NULL AND snoozedUntil > :now)")
    suspend fun upcoming(now: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE externalId IS NOT NULL")
    suspend fun external(): List<EventEntity>

    /** Para o painel web: compromissos a partir de [from] (o painel mostra até 30 dias para trás). */
    @Query("SELECT * FROM events WHERE startAt >= :from ORDER BY startAt LIMIT 1500")
    suspend fun since(from: Long): List<EventEntity>

    @Insert suspend fun insert(event: EventEntity): Long
    @Update suspend fun update(event: EventEntity)
    @Delete suspend fun delete(event: EventEntity)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY createdAt DESC LIMIT 500")
    suspend fun all(): List<NoteEntity>

    @Insert suspend fun insert(note: NoteEntity): Long
    @Delete suspend fun delete(note: NoteEntity)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 200")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Insert suspend fun insert(n: NotificationEntity): Long

    @Query("UPDATE notifications SET read = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications")
    suspend fun clear()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY createdAt DESC")
    suspend fun all(): List<MemoryEntity>

    @Insert suspend fun insert(m: MemoryEntity): Long
    @Delete suspend fun delete(m: MemoryEntity)

    @Query("DELETE FROM memories")
    suspend fun clear()
}
