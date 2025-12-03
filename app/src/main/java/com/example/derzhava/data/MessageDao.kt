package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MessageDao {

    @Insert
    fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE rulerName = :rulerName ORDER BY timestampMillis DESC")
    fun getMessagesForRuler(rulerName: String): List<MessageEntity>

    @Query("DELETE FROM messages WHERE rulerName = :rulerName")
    fun deleteAllForRuler(rulerName: String)

    // НОВОЕ: кол-во непрочитанных
    @Query("SELECT COUNT(*) FROM messages WHERE rulerName = :rulerName AND isRead = 0")
    fun getUnreadCount(rulerName: String): Int

    // НОВОЕ: пометить всё прочитанным
    @Query("UPDATE messages SET isRead = 1 WHERE rulerName = :rulerName")
    fun markAllAsRead(rulerName: String)
}
