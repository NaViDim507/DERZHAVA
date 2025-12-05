package com.example.derzhava.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface WarDao {

    @Query(
        """
        SELECT * FROM wars
        WHERE attackerRuler = :rulerName OR defenderRuler = :rulerName
        ORDER BY startAt DESC
        """
    )
    fun getWarsForRuler(rulerName: String): List<WarEntity>

    @Query("SELECT * FROM wars WHERE id = :id LIMIT 1")
    fun getById(id: Long): WarEntity?

    @Query(
        """
        DELETE FROM wars
        WHERE attackerRuler = :rulerName
           OR defenderRuler = :rulerName
        """
    )
    fun deleteWarsOfRuler(rulerName: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(war: WarEntity): Long

    @Update
    fun update(war: WarEntity)

    @Query("SELECT * FROM wars WHERE id = :id LIMIT 1")
    fun byId(id: Long): WarEntity?

    // <<< ВОТ ЭТО ПРАВИМ >>>
    @Query(
        """
        SELECT COUNT(*) FROM wars
        WHERE defenderRuler = :rulerName
          AND state = 'active'
        """
    )
    fun countActiveAsDefender(rulerName: String): Int
    // <<< БЕЗ isFinished >>>

    @Query(
        """
        SELECT COUNT(*) FROM wars
        WHERE attackerRuler = :rulerName
          AND state = 'active'
        """
    )
    fun countActiveForAttacker(rulerName: String): Int

    @Query(
        """
        SELECT 
            CASE 
                WHEN attackerRuler = :rulerName THEN defenderRuler
                ELSE attackerRuler
            END AS enemy
        FROM wars
        WHERE (attackerRuler = :rulerName OR defenderRuler = :rulerName)
          AND state = 'active'
        """
    )
    fun getActiveEnemiesForRuler(rulerName: String): List<String>
}
