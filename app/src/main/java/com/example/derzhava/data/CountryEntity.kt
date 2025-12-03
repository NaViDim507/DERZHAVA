package com.example.derzhava.data  // <-- если у тебя derzhava2, поменяй

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Аналог таблицы gos (упрощённо).
 * domik1..domik7 как в game.php:
 *  domik1 - Комбинат
 *  domik2 - Городок
 *  domik3 - Командный центр
 *  domik4 - Военная база
 *  domik5 - Периметр
 *  domik6 - Биржа
 *  domik7 - Сторожевая башня
 */
@Entity(tableName = "countries")
data class CountryEntity(
    @PrimaryKey val rulerName: String,   // логин
    val countryName: String,             // strana

    // РЕСУРСЫ
    val metal: Int = 1500,
    val mineral: Int = 2000,
    val wood: Int = 2000,
    val food: Int = 3000,
    val money: Int = 20000,

    // НАСЕЛЕНИЕ (базовые значения, как rab и bot в PHP)
    val workers: Int = 300,          // свободные рабочие
    val bots: Int = 50,              // учёные / боты

    // ГОРОДОК (domm/town): детальное распределение рабочих
    // rabmet / rabmin / rabdro / rabpro из der1
    val metallWorkers: Int = 0,      // металлурги
    val mineWorkers: Int = 0,        // шахтёры
    val woodWorkers: Int = 0,        // лесорубы
    val industryWorkers: Int = 0,    // промышленность / комбинат

    // ГОРОДОК: налоги и прирост населения
    // datanal, prirost, naukpri, zan в PHP
    val lastTaxTime: Long = 0L,                  // время последнего сбора налогов
    val lastProductionTime: Long = 0L,           // время последнего тика ресурсов/прироста
    val populationGrowthEnabled: Boolean = true, // prirost: true=включён, false=выключен
    val scienceGrowthBonus: Int = 10,            // naukpri — бонус к приросту (%)
    val stashMoney: Int = 0,                     // zan — деньги в "тайнике"

    // Пассивные процессы
    val lastResourceUpdateTime: Long = 0L,       // последний пересчёт ресурсов
    val lastPopulationUpdateTime: Long = 0L,     // последний пересчёт прироста населения


    // КОМБИНАТ: научные уровни (как nauk* в PHP)
    val globalScienceLevel: Int = 10,       // naukur — общий научный уровень (минимум 1)
    val scienceMetal: Int = 10,             // naukmet — выплавка железа (%)
    val scienceStone: Int = 10,             // naukmin — добыча камня (%)
    val scienceWood: Int = 10,              // naukdro — переработка древесины (%)
    val scienceFood: Int = 10,              // naukpro — выращивание зерна (%)
    val scienceDemolition: Int = 0,        // naukdem — демонтаж зданий (%)

    // Эпоха (epoha в PHP, пока просто 1, чтобы формула из kombik/mod6 не делилась на 0)
    val epoch: Int = 1,

    // Войска в бункере (peht/kazt/gvat в PHP)
    val bunkerInfantry: Int = 0,
    val bunkerCossacks: Int = 0,
    val bunkerGuards: Int = 0,

    // АРМИЯ (простая копия полей из gos — сейчас почти не используем,
    // основная логика армии живёт в ArmyState).
    // По умолчанию новые игроки не имеют войск, поэтому здесь 0. Ранее
    // пехота была равна 10, из‑за чего в разделе "Население" отображалась
    // пехота, тогда как на военной базе её не было. Теперь эти значения
    // обнуляются и берутся из ArmyState.
    val peh: Int = 0,
    val kaz: Int = 0,
    val gva: Int = 0,

    // ЗДАНИЯ (domik1..domik7)
    // По умолчанию все строения отсутствуют (0). При первом входе игрока
    // здания должны быть стройплощадками. Значение 1 означает, что
    // здание построено. Ранее здесь стояло значение 1, что приводило к
    // автоматическому созданию всех зданий у новых игроков независимо от
    // данных в базе данных. Теперь дефолты равны 0.
    val domik1: Int = 0,   // Комбинат
    val domik2: Int = 0,   // Городок
    val domik3: Int = 0,   // Командный центр
    val domik4: Int = 0,   // Военная база
    val domik5: Int = 0,   // Периметр
    val domik6: Int = 0,   // Биржа
    val domik7: Int = 0,   // Сторожевая башня

    // ---------- ТЕРРИТОРИЯ (как land/lesa/shah/rudn/pole в PHP) ----------
    val land: Int = 1000,    // свободная земля
    val lesa: Int = 1500,     // леса
    val shah: Int = 1000,     // шахты
    val rudn: Int = 1500,     // рудники
    val pole: Int = 2000,     // поля

    // Технические флаги
    val isNpc: Boolean = false,          // это NPC или нет
    val npcNote: String? = null,          // комментарий для админа

    val defenseLevel: Int = 0,

    // ЗВАНИЯ АССАМБЛЕИ (как zah в PHP)
    val zah: Int = 0               // очки, по которым считаем погоны/звания
)
