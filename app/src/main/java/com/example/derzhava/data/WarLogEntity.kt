package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "war_logs")
data class WarLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val warId: Long,
    val ts: Long,
    val type: String,   // recon / raid_town / raid_birzha / raid_cc / capture_ok / capture_fail и т.п.
    val payload: String // JSON с цифрами, потом красиво распарсим
)
