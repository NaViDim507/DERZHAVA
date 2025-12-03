package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Аналог таблицы kmb из der1 (обучение войск по учёным).
 */
@Entity(tableName = "training_jobs")
data class TrainingJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rulerName: String,        // strana / логин правителя

    val unitType: Int,            // 1 - пехота, 2 - казаки, 3 - гвардия
    val workers: Int,             // сколько рабочих обучаем (rab)
    val scientists: Int,          // сколько учёных занято (bot)

    val startTimeMillis: Long,    // время начала обучения
    val durationSeconds: Int      // длительность обучения (time2) в секундах
)
