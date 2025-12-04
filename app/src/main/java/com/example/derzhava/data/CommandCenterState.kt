package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "command_center")
data class CommandCenterState(
    @PrimaryKey
    val rulerName: String,

    // параметры спецслужб
    val intel: Int = 10,        // разведка
    val sabotage: Int = 10,     // диверсия
    val theft: Int = 10,        // воровство
    val agitation: Int = 10,    // агитация / вербовка

    // кулдауны спецопераций
    val lastReconTime: Long = 0L,
    val lastSabotageTime: Long = 0L,
    val lastTheftTime: Long = 0L,
    val lastAllianceTime: Long = 0L
)
