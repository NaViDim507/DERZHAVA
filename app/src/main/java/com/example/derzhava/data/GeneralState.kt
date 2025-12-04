package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Состояние генерала для конкретного правителя.
 * В оригинале это примерно gn/gv/go/gm (атака/защита/организация/мораль).
 */
@Entity(tableName = "general_state")
data class GeneralState(
    @PrimaryKey
    val rulerName: String,   // тот же ключ, что и у CountryEntity

    val level: Int = 1,          // общий уровень генерала
    val attack: Int = 0,         // навык атаки
    val defense: Int = 0,        // навык защиты
    val leadership: Int = 0,     // организация/лидерство (мораль, управление войсками)
    val experience: Long = 0L,   // опыт (на будущее, для боёв)
    val battles: Int = 0,        // всего боёв
    val wins: Int = 0            // побед
)
