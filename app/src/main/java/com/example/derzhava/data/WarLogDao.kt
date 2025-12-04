package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface WarLogDao {

    @Insert
    fun insert(l: WarLogEntity)

    @Query("SELECT * FROM war_logs WHERE warId = :warId ORDER BY ts DESC")
    fun byWar(warId: Long): List<WarLogEntity>


    @Query("DELETE FROM war_logs WHERE warId IN (:warIds)")
    fun deleteByWarIds(warIds: List<Long>)        // ← НОВОЕ
}
