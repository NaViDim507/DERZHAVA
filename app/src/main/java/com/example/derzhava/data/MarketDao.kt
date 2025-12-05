package com.example.derzhava.data

import androidx.room.*

@Dao
interface MarketDao {

    // Лот конкретного правителя по конкретному ресурсу (для игрока / NPC)
    @Query(
        """
        SELECT * FROM market_offers
        WHERE rulerName = :rulerName
          AND resourceType = :resourceType
        LIMIT 1
        """
    )
    fun getOfferForRulerAndResource(
        rulerName: String,
        resourceType: Int
    ): MarketOfferEntity?

    // Все чужие лоты по ресурсу (для обычной биржи игрока)
    @Query(
        """
        SELECT * FROM market_offers
        WHERE resourceType = :resourceType
          AND rulerName != :excludeRuler
        ORDER BY pricePerUnit ASC, id ASC
        """
    )
    fun getOffersForResource(
        resourceType: Int,
        excludeRuler: String
    ): List<MarketOfferEntity>

    // Все лоты по ресурсу (для админки, чтобы видеть NPC и реальных)
    @Query(
        """
        SELECT * FROM market_offers
        WHERE resourceType = :resourceType
        ORDER BY pricePerUnit ASC, id ASC
        """
    )
    fun getAllOffersForResource(resourceType: Int): List<MarketOfferEntity>

    // Все лоты конкретного правителя (для админки NPC)
    @Query(
        """
        SELECT * FROM market_offers
        WHERE rulerName = :rulerName
        ORDER BY resourceType, pricePerUnit
        """
    )
    fun getOffersForRuler(rulerName: String): List<MarketOfferEntity>

    @Query("SELECT * FROM market_offers WHERE id = :id LIMIT 1")
    fun getById(id: Long): MarketOfferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(offer: MarketOfferEntity): Long

    @Update
    fun update(offer: MarketOfferEntity)

    @Delete
    fun delete(offer: MarketOfferEntity)

    @Query(
        """
        DELETE FROM market_offers
        WHERE rulerName = :rulerName
          AND resourceType = :resourceType
        """
    )
    fun deleteForRulerAndResource(
        rulerName: String,
        resourceType: Int
    )
}
