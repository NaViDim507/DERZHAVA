package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Аналог таблицы botan в der1:
 *  strana, rab, bot, time1, time2
 */
@Entity(tableName = "scientist_training_jobs")
data class ScientistTrainingJobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val rulerName: String,      // strana
    val workers: Int,           // rab — сколько рабочих обучаем
    val scientists: Int,        // bot — сколько учёных тренеров
    val startTimeMillis: Long,  // time1
    val durationSeconds: Int    // time2 (e4)
)
