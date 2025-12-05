package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WarMoveDao {

    @Insert
    fun insert(m: WarMoveEntity)

    @Query("SELECT * FROM war_moves WHERE warId = :warId ORDER BY ts DESC")
    fun byWar(warId: Long): List<WarMoveEntity>

    @Query("DELETE FROM war_moves WHERE warId IN (:warIds)")
    fun deleteByWarIds(warIds: List<Long>)        // ← НОВОЕ
}
