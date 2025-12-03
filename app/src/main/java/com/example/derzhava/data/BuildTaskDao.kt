package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update   // <-- добавить

@Dao
interface BuildTaskDao {

    @Query("SELECT * FROM build_tasks WHERE rulerName = :rulerName")
    fun getTasksForRuler(rulerName: String): List<BuildTaskEntity>

    @Insert
    fun insert(task: BuildTaskEntity): Long

    @Query("SELECT * FROM build_tasks WHERE id = :id LIMIT 1")
    fun getTaskById(id: Long): BuildTaskEntity?

    @Delete
    fun delete(task: BuildTaskEntity)

    @Update
    fun update(task: BuildTaskEntity)

    @Query("DELETE FROM build_tasks WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM build_tasks WHERE rulerName = :rulerName")
    fun deleteAllForRuler(rulerName: String)
}
