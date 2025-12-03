package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScientistTrainingJobDao {

    @Query("SELECT * FROM scientist_training_jobs WHERE rulerName = :rulerName LIMIT 1")
    fun getJobForRuler(rulerName: String): ScientistTrainingJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(job: ScientistTrainingJobEntity): Long

    @Delete
    fun delete(job: ScientistTrainingJobEntity)

    @Query("DELETE FROM scientist_training_jobs WHERE rulerName = :rulerName")
    fun deleteByRuler(rulerName: String)
}
