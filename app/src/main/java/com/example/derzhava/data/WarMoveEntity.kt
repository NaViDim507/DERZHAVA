package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "war_moves")
data class WarMoveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val warId: Long,
    val type: String,   // reinforce / recall / raid / capture
    val ts: Long,
    val peh: Int = 0,
    val kaz: Int = 0,
    val gva: Int = 0,
    val cat: Int = 0
)
