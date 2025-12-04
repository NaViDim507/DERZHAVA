package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrainingJobDao {

    @Query("SELECT * FROM training_jobs WHERE rulerName = :rulerName LIMIT 1")
    fun getJobForRuler(rulerName: String): TrainingJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(job: TrainingJobEntity): Long

    @Delete
    fun delete(job: TrainingJobEntity)

    @Query("DELETE FROM training_jobs WHERE rulerName = :rulerName")
    fun deleteByRuler(rulerName: String)
}
