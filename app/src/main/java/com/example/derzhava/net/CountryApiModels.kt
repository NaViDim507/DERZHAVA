package com.example.derzhava.net

import com.google.gson.annotations.SerializedName
import com.example.derzhava.data.CountryEntity

data class CountryDto(
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("country_name") val countryName: String,

    @SerializedName("metal")   val metal: Int,
    @SerializedName("mineral") val mineral: Int,
    @SerializedName("wood")    val wood: Int,
    @SerializedName("food")    val food: Int,
    @SerializedName("money")   val money: Int,

    @SerializedName("workers") val workers: Int,
    @SerializedName("bots")    val bots: Int,

    @SerializedName("metall_workers")   val metallWorkers: Int,
    @SerializedName("mine_workers")     val mineWorkers: Int,
    @SerializedName("wood_workers")     val woodWorkers: Int,
    @SerializedName("industry_workers") val industryWorkers: Int,

    @SerializedName("last_tax_time")               val lastTaxTime: Long,
    @SerializedName("last_production_time")        val lastProductionTime: Long,
    @SerializedName("last_resource_update_time")   val lastResourceUpdateTime: Long,
    @SerializedName("last_population_update_time") val lastPopulationUpdateTime: Long,
    @SerializedName("population_growth_enabled")   val populationGrowthEnabled: Boolean,
    @SerializedName("science_growth_bonus")        val scienceGrowthBonus: Int,
    @SerializedName("stash_money")                 val stashMoney: Int,

    @SerializedName("global_science_level") val globalScienceLevel: Int,
    @SerializedName("science_metal")        val scienceMetal: Int,
    @SerializedName("science_stone")        val scienceStone: Int,
    @SerializedName("science_wood")         val scienceWood: Int,
    @SerializedName("science_food")         val scienceFood: Int,
    @SerializedName("science_demolition")   val scienceDemolition: Int,

    @SerializedName("epoch") val epoch: Int,

    @SerializedName("bunker_infantry") val bunkerInfantry: Int,
    @SerializedName("bunker_cossacks") val bunkerCossacks: Int,
    @SerializedName("bunker_guards")   val bunkerGuards: Int,

    @SerializedName("peh") val peh: Int,
    @SerializedName("kaz") val kaz: Int,
    @SerializedName("gva") val gva: Int,

    @SerializedName("domik1") val domik1: Int,
    @SerializedName("domik2") val domik2: Int,
    @SerializedName("domik3") val domik3: Int,
    @SerializedName("domik4") val domik4: Int,
    @SerializedName("domik5") val domik5: Int,
    @SerializedName("domik6") val domik6: Int,
    @SerializedName("domik7") val domik7: Int,

    @SerializedName("land") val land: Int,
    @SerializedName("lesa") val lesa: Int,
    @SerializedName("shah") val shah: Int,
    @SerializedName("rudn") val rudn: Int,
    @SerializedName("pole") val pole: Int,

    @SerializedName("zah")           val zah: Int,
    @SerializedName("is_npc")        val isNpc: Boolean,
    @SerializedName("defense_level") val defenseLevel: Int
)

data class CountryGetResponse(
    val success: Boolean,
    val message: String?,
    val country: CountryDto?
)

data class CountrySaveResponse(
    val success: Boolean,
    val message: String?
)

// Мапперы туда/обратно

fun CountryEntity.toDto(): CountryDto =
    CountryDto(
        rulerName = rulerName,
        countryName = countryName,
        metal = metal,
        mineral = mineral,
        wood = wood,
        food = food,
        money = money,
        workers = workers,
        bots = bots,
        metallWorkers = metallWorkers,
        mineWorkers = mineWorkers,
        woodWorkers = woodWorkers,
        industryWorkers = industryWorkers,
        lastTaxTime = lastTaxTime,
        lastProductionTime = lastProductionTime,
        lastResourceUpdateTime = lastResourceUpdateTime,
        lastPopulationUpdateTime = lastPopulationUpdateTime,
        populationGrowthEnabled = populationGrowthEnabled,
        scienceGrowthBonus = scienceGrowthBonus,
        stashMoney = stashMoney,
        globalScienceLevel = globalScienceLevel,
        scienceMetal = scienceMetal,
        scienceStone = scienceStone,
        scienceWood = scienceWood,
        scienceFood = scienceFood,
        scienceDemolition = scienceDemolition,
        epoch = epoch,
        bunkerInfantry = bunkerInfantry,
        bunkerCossacks = bunkerCossacks,
        bunkerGuards = bunkerGuards,
        peh = peh,
        kaz = kaz,
        gva = gva,
        domik1 = domik1,
        domik2 = domik2,
        domik3 = domik3,
        domik4 = domik4,
        domik5 = domik5,
        domik6 = domik6,
        domik7 = domik7,
        land = land,
        lesa = lesa,
        shah = shah,
        rudn = rudn,
        pole = pole,
        zah = zah,
        isNpc = isNpc,
        defenseLevel = defenseLevel
    )

fun CountryDto.toEntity(): CountryEntity =
    CountryEntity(
        rulerName = rulerName,
        countryName = countryName,
        metal = metal,
        mineral = mineral,
        wood = wood,
        food = food,
        money = money,
        workers = workers,
        bots = bots,
        metallWorkers = metallWorkers,
        mineWorkers = mineWorkers,
        woodWorkers = woodWorkers,
        industryWorkers = industryWorkers,
        lastTaxTime = lastTaxTime,
        lastProductionTime = lastProductionTime,
        populationGrowthEnabled = populationGrowthEnabled,
        scienceGrowthBonus = scienceGrowthBonus,
        stashMoney = stashMoney,
        lastResourceUpdateTime = lastResourceUpdateTime,
        lastPopulationUpdateTime = lastPopulationUpdateTime,
        globalScienceLevel = globalScienceLevel,
        scienceMetal = scienceMetal,
        scienceStone = scienceStone,
        scienceWood = scienceWood,
        scienceFood = scienceFood,
        scienceDemolition = scienceDemolition,
        epoch = epoch,
        bunkerInfantry = bunkerInfantry,
        bunkerCossacks = bunkerCossacks,
        bunkerGuards = bunkerGuards,
        peh = peh,
        kaz = kaz,
        gva = gva,
        domik1 = domik1,
        domik2 = domik2,
        domik3 = domik3,
        domik4 = domik4,
        domik5 = domik5,
        domik6 = domik6,
        domik7 = domik7,
        land = land,
        lesa = lesa,
        shah = shah,
        rudn = rudn,
        pole = pole,
        zah = zah,
        isNpc = isNpc,
        npcNote = null, // на сервер пока не передаём
        defenseLevel = defenseLevel
    )
