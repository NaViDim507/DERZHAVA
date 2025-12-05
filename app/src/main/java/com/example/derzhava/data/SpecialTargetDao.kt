package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface SpecialTargetDao {

    @Query("SELECT * FROM special_targets")
    fun getAll(): List<SpecialTargetEntity>

    @Query("SELECT * FROM special_targets WHERE id = :id")
    fun getById(id: Long): SpecialTargetEntity?

    @Query("SELECT * FROM special_targets WHERE rulerName = :rulerName LIMIT 1")
    fun getByRuler(rulerName: String): SpecialTargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(target: SpecialTargetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(targets: List<SpecialTargetEntity>)

    @Update
    fun update(target: SpecialTargetEntity)
}
