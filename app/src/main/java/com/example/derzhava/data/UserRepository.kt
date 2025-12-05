package com.example.derzhava.data

import android.content.Context

class UserRepository(context: Context) {

    private val prefs = context.getSharedPreferences("derzhava_prefs", Context.MODE_PRIVATE)

    fun hasUser(): Boolean {
        return prefs.contains(KEY_RULER_NAME)
    }

    fun getUser(): User? {
        if (!hasUser()) return null
        val ruler = prefs.getString(KEY_RULER_NAME, null) ?: return null
        val country = prefs.getString(KEY_COUNTRY_NAME, null) ?: return null
        val password = prefs.getString(KEY_PASSWORD, "") ?: ""
        val isAdmin = prefs.getBoolean(KEY_IS_ADMIN, false)
        return User(ruler, country, password, isAdmin)
    }

    fun registerUser(user: User) {
        prefs.edit()
            .putString(KEY_RULER_NAME, user.rulerName)
            .putString(KEY_COUNTRY_NAME, user.countryName)
            .putString(KEY_PASSWORD, user.password)
            .putBoolean(KEY_IS_ADMIN, user.isAdmin)
            .apply()
    }

    /**
     * Обновляет пароль текущего пользователя. Новый пароль сохраняется
     * в SharedPreferences. Если пользователь не найден, метод ничего
     * не делает.
     */
    fun updatePassword(newPassword: String) {
        val user = getUser() ?: return
        prefs.edit()
            .putString(KEY_PASSWORD, newPassword)
            .apply()
    }

    /**
     * Удаляет данные текущего пользователя из SharedPreferences. Вызывайте этот
     * метод при выходе из игры, чтобы сбросить локальный профиль и заставить
     * пользователя снова пройти авторизацию. Без очистки профиль хранится
     * локально, и приложение автоматически откроет GameFragment даже без
     * подключения к серверу, что приводит к нежелательному «оффлайн‑режиму».
     */
    fun clearUser() {
        prefs.edit()
            .remove(KEY_RULER_NAME)
            .remove(KEY_COUNTRY_NAME)
            .remove(KEY_PASSWORD)
            .remove(KEY_IS_ADMIN)
            .apply()
    }

    fun checkLogin(rulerName: String, password: String): Boolean {
        val user = getUser() ?: return false
        return user.rulerName == rulerName && user.password == password
    }

    /**
     * Время (в миллисекундах), когда игрок последний раз открывал экран сообщений.
     * Используется, чтобы считать непрочитанные сообщения по timestamp'у, так как
     * сервер не поддерживает пометку сообщений как прочитанных. Если значение
     * отсутствует, по умолчанию считается 0 (все входящие сообщения считаются
     * новыми).
     */
    fun getLastMessagesReadTimestamp(): Long {
        return prefs.getLong(KEY_LAST_READ_MESSAGES, 0L)
    }

    /**
     * Обновляет метку времени последнего прочтения сообщений. Вызывайте этот
     * метод после просмотра сообщений, чтобы красный индикатор количества
     * непрочитанных сообщений корректно исчезал. Значение сохраняется в
     * SharedPreferences.
     */
    fun setLastMessagesReadTimestamp(timestamp: Long) {
        prefs.edit()
            .putLong(KEY_LAST_READ_MESSAGES, timestamp)
            .apply()
    }

    companion object {
        private const val KEY_RULER_NAME = "ruler_name"
        private const val KEY_COUNTRY_NAME = "country_name"
        private const val KEY_PASSWORD = "password"

        // Новый ключ: хранит флаг администратора для пользователя. По умолчанию false.
        private const val KEY_IS_ADMIN = "is_admin"

        private const val KEY_LAST_READ_MESSAGES = "last_read_messages_ts"
    }
}
