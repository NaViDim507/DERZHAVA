package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ChatDao {

    @Insert
    fun insert(message: ChatMessageEntity): Long

    // последние N сообщений Ассамблеи (как LIMIT в chat.php)
    @Query("SELECT * FROM chat_messages ORDER BY timestampMillis DESC LIMIT :limit")
    fun getLastMessages(limit: Int = 50): List<ChatMessageEntity>

    // на будущее — очистка лога, если понадобится
    @Query("DELETE FROM chat_messages")
    fun deleteAll()
}
