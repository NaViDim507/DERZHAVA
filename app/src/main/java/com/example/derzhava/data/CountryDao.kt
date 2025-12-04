package com.example.derzhava.data  // <-- при необходимости поменяй

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete

@Dao
interface CountryDao {

    @Query("SELECT * FROM countries WHERE rulerName = :rulerName LIMIT 1")
    fun getCountryByRuler(rulerName: String): CountryEntity?
    @Query("SELECT * FROM countries WHERE isNpc = 1 ORDER BY countryName")
    fun getNpcCountries(): List<CountryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCountry(country: CountryEntity)

    // --- NPC-страны ---

    @Query("SELECT * FROM countries WHERE isNpc = 1")
    fun getAllNpcCountries(): List<CountryEntity>

    @Query("SELECT * FROM countries WHERE isNpc = 1 AND rulerName = :rulerName LIMIT 1")
    fun getNpcByRuler(rulerName: String): CountryEntity?

    @Query("SELECT * FROM countries WHERE isNpc = 0")
    fun getAllRealCountries(): List<CountryEntity>

    @Delete
    fun deleteCountry(country: CountryEntity)   // ← НОВОЕ

    @Query("SELECT * FROM countries WHERE rulerName != :rulerName")
    fun getAllExcept(rulerName: String): List<CountryEntity>
}
