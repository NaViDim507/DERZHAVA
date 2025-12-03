package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ArmyDao {

    @Query("SELECT * FROM army_state WHERE rulerName = :rulerName LIMIT 1")
    fun getByRuler(rulerName: String): ArmyState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(state: ArmyState)

    @Query("DELETE FROM army_state WHERE rulerName = :rulerName")
    fun deleteByRuler(rulerName: String)      // ← НОВОЕ
}
