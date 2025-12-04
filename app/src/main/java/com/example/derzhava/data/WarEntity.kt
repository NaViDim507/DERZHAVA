package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Война-кампания на территории врага.
 */
@Entity(tableName = "wars")
data class WarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val attackerRuler: String,
    val defenderRuler: String,
    val attackerCountry: String,
    val defenderCountry: String,

    // войска на территории врага
    val peh: Int,
    val kaz: Int,
    val gva: Int,
    val catapults: Int,

    // тайминги
    val startAt: Long,
    val canRaidAt: Long,
    val canCaptureAt: Long,
    val endedAt: Long? = null,

    // состояние и итог
    val state: String = "active",    // active / captured / failed / recalled
    val isResolved: Boolean = false,
    val attackerWon: Boolean? = null,

    // точность разведки
    val reconAcc: Int = 0
)
