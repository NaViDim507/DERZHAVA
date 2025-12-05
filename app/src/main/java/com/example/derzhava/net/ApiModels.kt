package com.example.derzhava.net

import com.google.gson.annotations.SerializedName

/**
 * Модель пользователя, возвращаемая сервером при авторизации и регистрации.
 * Помимо id, логина и названия страны теперь содержит флаг is_admin и
 * временные метки регистрации и последнего входа. is_admin определяет права
 * пользователя (администратор или обычный игрок). registrationTime и
 * lastLoginTime могут быть null, если сервер не поддерживает эти поля.
 */
data class ApiUser(
    @SerializedName("id") val id: Long,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("country_name") val countryName: String,
    @SerializedName("is_admin") val isAdmin: Int? = 0,
    @SerializedName("registration_time") val registrationTime: Long? = null,
    @SerializedName("last_login_time") val lastLoginTime: Long? = null
)

data class LoginResponse(
    val success: Boolean,
    val message: String?,
    val user: ApiUser?
)

// структура та же
typealias RegisterResponse = LoginResponse

// --- Администрирование / NPC ---

/**
 * Ответ от npc_list.php. Содержит массив стран (обычно NPC‑стран) или null.
 */
data class NpcListResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("countries") val countries: List<CountryDto>?
)

/**
 * Ответ от users_get.php. Содержит список пользователей. Каждый элемент
 * представляет собой объект ApiUser с расширенными полями, включая
 * is_admin, registration_time и last_login_time.
 */
data class UsersListResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("users") val users: List<ApiUser>?
)

// ответ war_capture.php
data class WarCaptureResponse(
    val success: Boolean,
    val message: String?,
    val data: WarCaptureData?
)

data class WarCaptureData(
    val war_id: Long?,
    val state: String?,
    val attacker_won: Int?,
    val reward_money: Long?,
    val reward_land: Int?,
    val ended_at: Long?
)