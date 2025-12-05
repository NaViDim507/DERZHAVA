package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rulerName: String,
    val text: String,
    val timestampMillis: Long,
    val isRead: Boolean = false,      // <-- добавили
    // НОВОЕ:
    val type: String = "generic",     // "generic", "alliance_invite", ...
    val payloadRuler: String? = null  // для приглашения: инициатор союза
)
