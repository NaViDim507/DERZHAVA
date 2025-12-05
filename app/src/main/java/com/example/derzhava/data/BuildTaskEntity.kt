package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Задача строительства.
 */
@Entity(tableName = "build_tasks")
data class BuildTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rulerName: String,      // логин правителя
    val buildingType: Int,      // тип здания (1..7, см. Buildings)
    val workers: Int,           // СКОЛЬКО рабочих отправили на стройку
    val startTimeMillis: Long,
    val endTimeMillis: Long
)
