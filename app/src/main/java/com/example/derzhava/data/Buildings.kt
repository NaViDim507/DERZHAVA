package com.example.derzhava.data

object Buildings {

    data class BuildingCost(
        val money: Int = 0,
        val food: Int = 0,
        val wood: Int = 0,
        val metal: Int = 0
    )
    const val KOMBINAT = 1
    const val TOWN = 2
    const val COMMAND_CENTER = 3
    const val WAR_BASE = 4
    const val PERIMETR = 5
    const val BIRZHA = 6
    const val WATCH_TOWER = 7

    fun name(id: Int): String = when (id) {
        KOMBINAT       -> "Комбинат"
        TOWN           -> "Городок"
        COMMAND_CENTER -> "Командный центр"
        WAR_BASE       -> "Военная база"
        PERIMETR       -> "Периметр"
        BIRZHA         -> "Биржа"
        WATCH_TOWER    -> "Сторожевая башня"
        else           -> "Неизвестное здание"
    }

    /** Базовое время из stroyka.php ($tim) в секундах */
    private fun baseTimeSeconds(id: Int): Long = when (id) {
        KOMBINAT       -> 385_000L
        TOWN           -> 327_000L
        COMMAND_CENTER -> 496_000L
        WAR_BASE       -> 415_000L
        PERIMETR       -> 268_000L
        BIRZHA         -> 597_000L
        WATCH_TOWER    -> 897_000L
        else           -> 300_000L
    }

    /**
     * Расчёт времени строительства, как в PHP:
     *  $tim = round($tim / $rab)
     */
    fun calcDurationMillis(id: Int, workers: Int): Long {
        val w = workers.coerceAtLeast(1)
        return baseTimeSeconds(id) * 1000L / w
    }
    fun cost(type: Int): BuildingCost = when (type) {
        KOMBINAT -> BuildingCost(
            money = 2000,
            food  = 0,
            wood  = 800,
            metal = 600
        )
        TOWN -> BuildingCost(
            money = 1500,
            food  = 500,
            wood  = 600,
            metal = 0
        )
        COMMAND_CENTER -> BuildingCost(
            money = 3000,
            food  = 0,
            wood  = 0,
            metal = 800
        )
        WAR_BASE -> BuildingCost(
            money = 2500,
            food  = 500,
            wood  = 400,
            metal = 800
        )
        PERIMETR -> BuildingCost(
            money = 3000,
            food  = 0,
            wood  = 700,
            metal = 700
        )
        BIRZHA -> BuildingCost(
            money = 4000,
            food  = 0,
            wood  = 300,
            metal = 300
        )
        WATCH_TOWER -> BuildingCost(
            money = 1500,
            food  = 0,
            wood  = 400,
            metal = 400
        )
        else -> BuildingCost()
    }
}
