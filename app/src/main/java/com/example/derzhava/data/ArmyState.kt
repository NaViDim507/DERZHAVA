package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Упрощённый аналог военной части gos:
 * хранит количество войск и базовые параметры атаки/защиты.
 */
@Entity(tableName = "army_state")
data class ArmyState(
    @PrimaryKey
    val rulerName: String,   // логин правителя

    // Количество войск
    val infantry: Int = 0,   // пехота
    val cossacks: Int = 0,   // казаки
    val guards: Int = 0,     // гвардия
    val catapults: Int = 0,  // катапульты

    // Параметры войск (как «атв» и «зав» в PHP)
    val infantryAttack: Int = 10,
    val infantryDefense: Int = 10,
    val cossackAttack: Int = 15,
    val cossackDefense: Int = 12,
    val guardAttack: Int = 20,
    val guardDefense: Int = 18
)
