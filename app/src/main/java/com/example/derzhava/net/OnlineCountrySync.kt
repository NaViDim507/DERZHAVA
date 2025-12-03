package com.example.derzhava.net

import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CountryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Отвечает за синхрон между Room и VPS.
 */
object OnlineCountrySync {

    /**
     * После успешной регистрации/логина:
     *  - пробует забрать состояние с сервера;
     *  - если страны нет — создаёт дефолтную, сразу пушит на сервер;
     *  - в любом случае сохраняет актуальное состояние в Room.
     */
    suspend fun syncDownOrCreate(
        db: AppDatabase,
        rulerName: String,
        countryName: String
    ): CountryEntity = withContext(Dispatchers.IO) {

        val dao = db.countryDao()

        // 1. Пробуем забрать с сервера
        // Всегда обращаемся к серверу за состоянием страны.
        // Ошибки сети прокидываются наверх и должны быть обработаны вызывающей стороной.
        val response = ApiClient.apiService.getCountry(rulerName)

        if (response.success && response.country != null) {
            // Есть состояние на сервере
            val entity = response.country.toEntity()
            dao.insertCountry(entity)
            return@withContext entity
        } else {
            // На сервере страны ещё нет — создаём дефолт и сразу отправляем его на сервер
            val created = CountryEntity(
                rulerName = rulerName,
                countryName = countryName
            )
            // Пушим на VPS; если возникнет ошибка, она попадёт наружу
            ApiClient.apiService.saveCountry(created.toDto())
            dao.insertCountry(created)
            return@withContext created
        }
    }

    /**
     * Пушит текущее состояние из Room на сервер (например, при выходе из игры
     * или по кнопке "Сохранить").
     */
    suspend fun syncUp(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {
        val dao = db.countryDao()
        val country = dao.getCountryByRuler(rulerName) ?: return@withContext

        // Передаём состояние на сервер. Ошибки сети не скрываем.
        ApiClient.apiService.saveCountry(country.toDto())
    }
}
