package com.example.derzhava.net

import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.GeneralState
import com.example.derzhava.net.toDto
import com.example.derzhava.net.toState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Синхрон генерала между локальным хранилищем и VPS. Позволяет
 * загрузить актуальные характеристики генерала и отправить
 * обновления на сервер. Используйте эти функции вместо обращения
 * напрямую к DAO, чтобы поддерживать онлайн‑режим игры.
 */
object OnlineGeneralSync {

    /**
     * Скачивает состояние генерала с сервера и сохраняет его через DAO.
     * Возвращает полученное состояние или локальное, если сервер ничего
     * не вернул.
     */
    suspend fun syncDown(
        db: AppDatabase,
        rulerName: String
    ): GeneralState? = withContext(Dispatchers.IO) {
        val dao = db.generalDao()
        val response = ApiClient.apiService.getGeneral(rulerName)
        return@withContext if (response.success && response.general != null) {
            val state = response.general.toState()
            dao.insert(state)
            state
        } else {
            dao.getByRuler(rulerName)
        }
    }

    /**
     * Отправляет локальное состояние генерала на сервер. Ничего не
     * делает, если локальное состояние отсутствует.
     */
    suspend fun syncUp(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {
        val dao = db.generalDao()
        val local = dao.getByRuler(rulerName) ?: return@withContext
        ApiClient.apiService.saveGeneral(local.toDto())
    }
}