package com.example.derzhava.net

import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.MarketOfferEntity
import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Синхрон биржи между локальным хранилищем и сервером. Эти функции
 * обеспечивают загрузку и управление лотами через API, не используя
 * локальную базу данных Room. Используйте их в фрагментах биржи для
 * полной онлайн‑работы.
 */
object OnlineMarketSync {

    /**
     * Загружает список лотов по указанному типу ресурса, исключая
     * предложения указанного правителя. Запрос проходит через DAO,
     * который обращается к серверу. Результат не возвращается, так как
     * DAO кэширует данные в памяти, если требуется.
     */
    suspend fun syncDown(
        db: AppDatabase,
        resourceType: Int,
        excludeRuler: String
    ) = withContext(Dispatchers.IO) {
        db.marketDao().getOffersForResource(resourceType, excludeRuler)
    }

    /**
     * Создаёт или обновляет лот. Если для данного правителя уже есть
     * предложение по ресурсу, выполняется обновление через DAO.update,
     * иначе создаётся новый лот через DAO.insert.
     */
    suspend fun addOffer(
        db: AppDatabase,
        offer: MarketOfferEntity
    ) = withContext(Dispatchers.IO) {
        val dao = db.marketDao()
        val existing = dao.getOfferForRulerAndResource(offer.rulerName, offer.resourceType)
        if (existing == null) {
            dao.insert(offer)
        } else {
            dao.update(offer.copy(id = existing.id))
        }
    }

    /**
     * Удаляет лот по id. Если локальный DAO знает о лоте, используется
     * его удаление, иначе отправляем команду на сервер напрямую.
     */
    suspend fun deleteOffer(
        db: AppDatabase,
        id: Long,
        rulerName: String
    ) = withContext(Dispatchers.IO) {
        val dao = db.marketDao()
        val offer = dao.getById(id)
        if (offer != null) {
            dao.delete(offer)
        } else {
            ApiClient.apiService.deleteMarketOffer(id, rulerName)
        }
    }

    /**
     * Пушит лоты правителя на сервер. В этой реализации отдельный
     * эндпоинт для синхронизации не требуется, поэтому метод пустой.
     */
    suspend fun syncUp(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {
        // Лоты сохраняются через addOffer/deleteOffer
    }
}