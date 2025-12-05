package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CommandCenterDao {

    @Query("SELECT * FROM command_center WHERE rulerName = :rulerName LIMIT 1")
    fun getStateByRuler(rulerName: String): CommandCenterState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertState(state: CommandCenterState)
}
