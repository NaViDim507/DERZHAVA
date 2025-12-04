package com.example.derzhava.net

import com.example.derzhava.data.ArmyState
import com.google.gson.annotations.SerializedName

/**
 * DTO армии с сервера (по сути "срез" из gos_app).
 */
data class ArmyDto(
    @SerializedName("ruler_name") val rulerName: String,

    @SerializedName("peh")  val infantry: Int,
    @SerializedName("kaz")  val cossacks: Int,
    @SerializedName("gva")  val guards: Int,

    // на будущее: войска в бункере (если понадобятся)
    @SerializedName("peht") val bunkerInfantry: Int? = null,
    @SerializedName("kazt") val bunkerCossacks: Int? = null,
    @SerializedName("gvat") val bunkerGuards: Int? = null
)

/**
 * Ответ army_get.php
 */
data class ArmyResponse(
    val success: Boolean,
    val message: String?,
    val army: ArmyDto?
)

/**
 * Ответ army_save.php
 */
data class ArmySaveResponse(
    val success: Boolean,
    val message: String?
)

/**
 * Конвертация сервера → локальное ArmyState.
 * Катапульты и статы атаки/защиты оставляем локальными.
 */
fun ArmyDto.toArmyState(): ArmyState =
    ArmyState(
        rulerName = rulerName,
        infantry  = infantry,
        cossacks  = cossacks,
        guards    = guards,
        catapults = 0,          // сервер пока не хранит катапульты
        // attack/defense поля возьмутся дефолтные из data class
    )

/**
 * Обновить локальное ArmyState данными сервера, не трогая катапульты и статы.
 */
fun ArmyState.withServerCounts(dto: ArmyDto): ArmyState =
    copy(
        infantry = dto.infantry,
        cossacks = dto.cossacks,
        guards   = dto.guards
    )
