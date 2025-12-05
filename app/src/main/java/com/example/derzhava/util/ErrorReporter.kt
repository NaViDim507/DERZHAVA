package com.example.derzhava.util

import com.example.derzhava.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * ErrorReporter позволяет отправлять сообщения об ошибках на сервер.
 * Используйте его для логирования непредвиденных сбоев, рассинхронизаций
 * и любых проблем, которые нужно зафиксировать на стороне back‑end.
 *
 * Пример использования:
 * ````kotlin
 *   ErrorReporter.logClient("Ошибка синхронизации армии", rulerName = user.rulerName)
 * ````
 */
object ErrorReporter {
    /**
     * Асинхронно отправляет ошибку на сервер. Тип ошибки определяйте сами: client или server.
     */
    private fun logAsync(
        errorType: String,
        message: String,
        context: String? = null,
        rulerName: String? = null
    ) {
        // Запускаем отправку в отдельной корутине, чтобы не блокировать UI
        CoroutineScope(Dispatchers.IO).launch {
            try {
             //   ApiClient.apiService.logError(rulerName, errorType, message, context)
            } catch (_: Exception) {
                // Игнорируем ошибки сети при логировании
            }
        }
    }

    /** Отправить сообщение об ошибке клиента. */
    fun logClient(message: String, context: String? = null, rulerName: String? = null) {
        logAsync("client", message, context, rulerName)
    }

    /** Отправить сообщение об ошибке сервера (используйте на серверной стороне PHP). */
    fun logServer(message: String, context: String? = null, rulerName: String? = null) {
        logAsync("server", message, context, rulerName)
    }
}