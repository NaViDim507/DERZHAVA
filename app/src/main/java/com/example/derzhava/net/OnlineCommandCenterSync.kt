package com.example.derzhava.net

import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.CommandCenterState
import com.example.derzhava.net.toDto
import com.example.derzhava.net.toState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Синхрон командного центра между локальным хранилищем и сервером.
 * Эти функции позволяют загружать актуальное состояние спецслужб с VPS
 * и отправлять локальные изменения обратно на сервер. Используйте
 * их вместо прямой работы с Room, чтобы обеспечить работу в онлайн‑режиме.
 */
object OnlineCommandCenterSync {

    /**
     * Скачивает состояние командного центра для заданного правителя и
     * сохраняет его через DAO. Возвращает полученное состояние либо
     * локальное, если сервер ничего не вернул.
     */
    suspend fun syncDown(
        db: AppDatabase,
        rulerName: String
    ): CommandCenterState? = withContext(Dispatchers.IO) {
        val dao = db.commandCenterDao()
        val response = ApiClient.apiService.getCommandCenter(rulerName)
        return@withContext if (response.success && response.commandCenter != null) {
            val state = response.commandCenter.toState()
            dao.insertState(state)
            state
        } else {
            dao.getStateByRuler(rulerName)
        }
    }

    /**
     * Отправляет текущее состояние командного центра на сервер. Если
     * локальное состояние отсутствует, ничего не делает.
     */
    suspend fun syncUp(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {
        val dao = db.commandCenterDao()
        val local = dao.getStateByRuler(rulerName) ?: return@withContext
        ApiClient.apiService.saveCommandCenter(local.toDto())
    }
}