package com.example.derzhava.data

import kotlin.math.roundToInt

/**
 * Отвечает за "тик" мира:
 *  - производство ресурсов раз в час по формуле комбината;
 *  - прирост населения по формуле городка (если включён).
 *
 * Считает оффлайн: по разнице между nowMillis и lastProductionTime.
 */
object ProductionManager {

    private const val HOUR_MILLIS = 60 * 60 * 1000L

    fun applyHourlyProductionAndGrowth(
        country: CountryEntity,
        nowMillis: Long
    ): CountryEntity {
        // Если ещё ни разу не тикали — просто зафиксируем стартовое время
        if (country.lastProductionTime == 0L) {
            return country.copy(lastProductionTime = nowMillis)
        }

        val diffMillis = nowMillis - country.lastProductionTime
        if (diffMillis < HOUR_MILLIS) {
            // Меньше часа прошло – тика нет
            return country
        }

        val hours = (diffMillis / HOUR_MILLIS).toInt()
        if (hours <= 0) return country

        // Производство ресурсов в час (как в KombinatFragment / ResourcesFragment)
        val income = calculateResourceIncome(country)

        // Прирост населения в час (как rabGrowth в TownFragment)
        val popPerHour = calculatePopulationGrowthPerHour(country)

        // Если вообще НИЧЕГО не производится (ресурсы = 0 и прирост людей = 0),
        // то ресурсы/людей не трогаем, но время всё равно "сжигаем"
        if (income.isZero() && popPerHour <= 0) {
            val newLast = country.lastProductionTime + hours * HOUR_MILLIS
            return country.copy(lastProductionTime = newLast)
        }

        val newMetal   = country.metal   + income.metal * hours
        val newMineral = country.mineral + income.mineral * hours
        val newWood    = country.wood    + income.wood * hours
        val newFood    = country.food    + income.food * hours

        val workersDelta =
            if (popPerHour > 0) popPerHour * hours else 0
        val newWorkers = country.workers + workersDelta

        val newLast = country.lastProductionTime + hours * HOUR_MILLIS

        return country.copy(
            metal = newMetal,
            mineral = newMineral,
            wood = newWood,
            food = newFood,
            workers = newWorkers,
            lastProductionTime = newLast
        )
    }

    /**
     * Производство ресурсов в ЧАС по той же логике, что в комбинате:
     *  komm/komn/komd/komp = (rabX * naukX) / 100
     *  + проверка шахт/рудников/лесов/полей (если не хватает – 0).
     */
    private fun calculateResourceIncome(c: CountryEntity): ResourceIncome {
        var metalIncome = ((c.metallWorkers * c.scienceMetal) / 100.0).roundToInt()
        var mineralIncome = ((c.mineWorkers * c.scienceStone) / 100.0).roundToInt()
        var woodIncome = ((c.woodWorkers * c.scienceWood) / 100.0).roundToInt()
        var foodIncome = ((c.industryWorkers * c.scienceFood) / 100.0).roundToInt()

        val x1 = (c.shah - c.metallWorkers / 10.0).roundToInt()
        val x2 = (c.rudn - c.mineWorkers / 10.0).roundToInt()
        val x3 = (c.lesa - c.woodWorkers / 10.0).roundToInt()
        val x4 = (c.pole - c.industryWorkers / 10.0).roundToInt()

        if (x1 <= 0) metalIncome = 0
        if (x2 <= 0) mineralIncome = 0
        if (x3 <= 0) woodIncome = 0
        if (x4 <= 0) foodIncome = 0

        return ResourceIncome(
            metal = metalIncome,
            mineral = mineralIncome,
            wood = woodIncome,
            food = foodIncome
        )
    }

    /**
     * Прирост населения в городке за час:
     * rabGrowth = (((ww1/2) / 100) * scienceGrowthBonus)
     * где ww1 = все рабочие блока (свободные + на производстве).
     *
     * Если прирост выключен – 0.
     * Если формула даёт 0 или минус – тоже не растём.
     */
    private fun calculatePopulationGrowthPerHour(c: CountryEntity): Int {
        if (!c.populationGrowthEnabled) return 0

        // Прирост возможен только если построены Комбинат и Городок
        val canGrowPopulation = (c.domik1 == 1 && c.domik2 == 1)
        if (!canGrowPopulation) return 0

        val totalWorkersBlock =
            c.workers + c.metallWorkers + c.mineWorkers + c.woodWorkers + c.industryWorkers

        val rabGrowth = (((totalWorkersBlock / 2.0) / 100.0) * c.scienceGrowthBonus)
            .roundToInt()

        return rabGrowth.coerceAtLeast(0)
    }

    data class ResourceIncome(
        val metal: Int,
        val mineral: Int,
        val wood: Int,
        val food: Int
    ) {
        fun isZero() = metal <= 0 && mineral <= 0 && wood <= 0 && food <= 0
    }
}
