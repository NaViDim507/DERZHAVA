package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Аналог таблицы nauka в der1 (komanda kombik):
 *  strana, nauka, data, time, bot, pr
 */
@Entity(tableName = "research_jobs")
data class ResearchJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val rulerName: String,     // strana
    val scienceType: Int,      // 1..7 как в PHP (см. mod5/mod6)
    val startTimeMillis: Long, // data
    val durationSeconds: Int,  // time (z)
    val scientists: Int,       // bot
    val progressPoints: Int    // pr = d/100
)
