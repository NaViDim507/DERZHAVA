package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Цель для спецопераций (NPC-сосед).
 * Позже сюда можно подставлять реальных игроков из онлайн-БД.
 */
@Entity(tableName = "special_targets")
data class SpecialTargetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val rulerName: String,
    val countryName: String,

    val perimeter: Int,          // "уровень" периметра
    val security: Int,           // общая защита от диверсий/воров/агитации

    val money: Int = 500,        // деньги в казне (для воровства)

    val isAlly: Boolean = false  // союзник / враг
)
