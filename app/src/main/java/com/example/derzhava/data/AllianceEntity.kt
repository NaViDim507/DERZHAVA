package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "alliances",
    indices = [Index(value = ["rulerA", "rulerB"], unique = true)]
)
data class AllianceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rulerA: String,          // пара всегда в отсортированном виде
    val rulerB: String,
    val initiator: String,       // кто предложил союз
    val status: Int,             // 0=pending,1=active,2=rejected,3=broken,4=expired
    val createdAt: Long,
    val expiresAt: Long,
    val respondedAt: Long? = null
)
