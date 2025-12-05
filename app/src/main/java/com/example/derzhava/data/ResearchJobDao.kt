package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ResearchJobDao {

    @Query("SELECT * FROM research_jobs WHERE rulerName = :rulerName LIMIT 1")
    fun getJobForRuler(rulerName: String): ResearchJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(job: ResearchJobEntity): Long

    @Delete
    fun delete(job: ResearchJobEntity)

    @Query("DELETE FROM research_jobs WHERE rulerName = :rulerName")
    fun deleteByRuler(rulerName: String)
}
