package com.example.derzhava.data

import androidx.room.*

@Dao
interface AllianceDao {

    @Query(
        """
        SELECT * FROM alliances
        WHERE rulerA = :a AND rulerB = :b
        LIMIT 1
        """
    )
    fun getAlliance(a: String, b: String): AllianceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(alliance: AllianceEntity): Long

    @Update
    fun update(alliance: AllianceEntity)

    @Query(
        """
        SELECT * FROM alliances
        WHERE (rulerA = :ruler OR rulerB = :ruler)
          AND status = 1
        """
    )
    fun getActiveAlliancesForRuler(ruler: String): List<AllianceEntity>

    @Query(
        """
        SELECT * FROM alliances
        WHERE (rulerA = :ruler OR rulerB = :ruler)
          AND status = 0
        """
    )
    fun getPendingForRuler(ruler: String): List<AllianceEntity>

    @Query(
        """
        SELECT * FROM alliances
        WHERE (rulerA = :ruler OR rulerB = :ruler)
          AND status = 0
          AND expiresAt < :now
        """
    )
    fun getExpiredPendingForRuler(ruler: String, now: Long): List<AllianceEntity>
}
