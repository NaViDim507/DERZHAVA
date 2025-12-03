package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Один лот на бирже.
 *
 * resourceType:
 *  1 - металл
 *  2 - камень
 *  3 - дерево
 *  4 - зерно
 *  5 - рабочие
 *  6 - учёные
 */
@Entity(tableName = "market_offers")
data class MarketOfferEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val rulerName: String,     // владелец лота (логин правителя)
    val resourceType: Int,     // 1..6
    val amount: Int,           // сколько на продаже
    val pricePerUnit: Int      // цена за 1 ед.
)
