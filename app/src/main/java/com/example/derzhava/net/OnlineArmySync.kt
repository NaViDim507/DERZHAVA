package com.example.derzhava.net

import com.example.derzhava.data.AppDatabase
import com.example.derzhava.data.ArmyDao
import com.example.derzhava.data.ArmyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Синхрон армии между Room и VPS (gos_app.peh/kaz/gva).
 */
object OnlineArmySync {

    /**
     * Потянуть армию с сервера и сохранить/обновить локальное ArmyState.
     * Вызывать:
     *  - после логина/регистрации;
     *  - при заходе в военную базу, если нужна актуализация.
     */
    suspend fun syncDown(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {

        val armyDao: ArmyDao = db.armyDao()
        val local: ArmyState? = armyDao.getByRuler(rulerName)

        // Всегда обращаемся к серверу; ошибки сети должны быть обработаны вызывающей стороной
        val response = ApiClient.apiService.getArmy(rulerName)

        if (!response.success || response.army == null) {
            // Сервер ничего не дал — если локального нет, создаём пустое состояние
            if (local == null) {
                armyDao.insert(ArmyState(rulerName = rulerName))
            }
            return@withContext
        }

        val dto = response.army
        val merged = if (local == null) {
            dto.toArmyState()
        } else {
            // Берём количество войск с сервера, а катапульты и статы — локальные.
            local.withServerCounts(dto)
        }

        armyDao.insert(merged)
    }

    /**
     * Отправить на VPS текущее состояние армии из Room.
     * Вызывать:
     *  - после обучения;
     *  - после увольнения войск;
     *  - после любых операций, где меняется infantry/cossacks/guards.
     */
    suspend fun syncUp(
        db: AppDatabase,
        rulerName: String
    ) = withContext(Dispatchers.IO) {

        val armyDao = db.armyDao()
        val local = armyDao.getByRuler(rulerName) ?: return@withContext

        // Отправляем состояние армии на сервер. Ошибки сети не скрываем
        ApiClient.apiService.saveArmy(
            rulerName = rulerName,
            peh = local.infantry,
            kaz = local.cossacks,
            gva = local.guards
        )
    }
}
