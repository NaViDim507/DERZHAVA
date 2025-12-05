package com.example.derzhava.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ассамблея (chat) — аналог таблицы chat из der1.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // кто пишет
    val rulerName: String,          // nik
    val countryName: String,        // strana

    // содержимое
    val text: String,               // mess

    // время
    val timestampMillis: Long,      // time

    // приват/системные
    val isPrivate: Boolean = false,         // pr (1 = приваты)
    val targetRulerName: String? = null,    // p2
    val isSystem: Boolean = false,          // системные сообщения

    // медаль/погон как в chat.php: med = '<img src="/pogons/zaX.jpg">'
    // здесь хранится именно путь, например "/pogons/za3.jpg" или "/pogons/wm5.jpg"
    val medalPath: String? = null
)
