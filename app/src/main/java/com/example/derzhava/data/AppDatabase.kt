package com.example.derzhava.data

import android.content.Context
import com.example.derzhava.net.ApiClient
import com.example.derzhava.net.toEntity
import com.example.derzhava.net.toDto
import com.example.derzhava.net.toState
import com.example.derzhava.net.toArmyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Заменённая реализация локальной базы данных. Вместо использования
 * Android Room все операции направляются на удалённый сервер через API.
 * Этот класс предоставляет те же методы DAO, что и ранее, но под капотом
 * вызывает соответствующие PHP‑скрипты на VPS. Таким образом, игра
 * полностью работает в онлайн‑режиме без локального хранения.
 */
class AppDatabase private constructor() {

    /**
     * Временный кэш войн. Ранее этот кэш существовал только в памяти,
     * поэтому после перезапуска приложения список незавершённых войн
     * исчезал, даже если они оставались на сервере. Теперь кэш
     * сериализуется в SharedPreferences для сохранения между сессиями.
     * Инициализация кэша происходит в getInstance() через loadWarCache().
     * Любые изменения кэша должны сопровождаться вызовом saveWarCache().
     */
    private val warCache = mutableMapOf<Long, WarEntity>()

    /** Контекст приложения для доступа к SharedPreferences. Заполняется
     *  в getInstance(context). Используем applicationContext, чтобы избежать
     *  утечек Activity/Fragment.
     */
    private var appContext: Context? = null

    // --- Country ---
    val countryDao: CountryDao = object : CountryDao {
        override fun getCountryByRuler(rulerName: String): CountryEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getCountry(rulerName) }
                if (resp.success && resp.country != null) resp.country.toEntity() else null
            } catch (_: Exception) {
                null
            }
        }

        override fun getNpcCountries(): List<CountryEntity> {
            return try {
                // Новый эндпоинт npc_list.php возвращает полный список NPC‑стран.
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getNpcList() }
                resp.countries?.map { dto -> dto.toEntity().copy(isNpc = true) } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        override fun insertCountry(country: CountryEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.saveCountry(country.toDto()) }
            } catch (_: Exception) {
                // Игнорируем ошибки сети при сохранении страны
            }
        }

        override fun getAllNpcCountries(): List<CountryEntity> = getNpcCountries()

        override fun getNpcByRuler(rulerName: String): CountryEntity? {
            return getNpcCountries().firstOrNull { it.rulerName == rulerName }
        }

        override fun getAllRealCountries(): List<CountryEntity> {
            return try {
                // Запрашиваем список всех пользователей. Затем фильтруем не‑администраторов и NPC.
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getUsers() }
                val users = resp.users ?: return emptyList()
                val realUsers = users.filter { (it.isAdmin ?: 0) == 0 }
                // Для каждой записи загружаем страну. Если загрузить не удаётся, пропускаем.
                val countries = mutableListOf<CountryEntity>()
                for (u in realUsers) {
                    val countryResp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getCountry(u.rulerName) }
                    if (countryResp.success && countryResp.country != null) {
                        countries.add(countryResp.country.toEntity())
                    }
                }
                countries
            } catch (_: Exception) {
                emptyList()
            }
        }

        override fun deleteCountry(country: CountryEntity) {
            // Нет операции удаления на сервере — игнорируем
        }

        override fun getAllExcept(rulerName: String): List<CountryEntity> {
            return try {
                // Загрузка списка всех пользователей (включая администраторов) и NPC.
                val usersResp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getUsers() }
                val users = usersResp.users ?: emptyList()
                val npcsResp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getNpcList() }
                val npcCountries = npcsResp.countries?.map { dto -> dto.toEntity().copy(isNpc = true) } ?: emptyList()

                val result = mutableListOf<CountryEntity>()

                // Добавляем игроков (исключаем текущего пользователя). Администраторы и обычные
                // пользователи возвращаются из getUsers(). Каждого игрока нужно
                // конвертировать в CountryEntity. Если страна не найдена, пропускаем.
                for (u in users) {
                    if (u.rulerName == rulerName) continue
                    // Игроки могут быть как админы, так и обычные. Нам всё равно нужно
                    // подгрузить их страны, чтобы они отображались в соседях.
                    val countryResp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getCountry(u.rulerName) }
                    if (countryResp.success && countryResp.country != null) {
                        val entity = countryResp.country.toEntity().copy(isNpc = false)
                        result.add(entity)
                    }
                }

                // Добавляем NPC‑страны
                result.addAll(npcCountries)

                // Удаляем возможные дубликаты по имени правителя
                return result.distinctBy { it.rulerName }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Для совместимости с прежним API Room предоставляем методы, возвращающие DAO
    fun countryDao(): CountryDao = countryDao
    fun commandCenterDao(): CommandCenterDao = commandCenterDao
    fun generalDao(): GeneralDao = generalDao
    fun armyDao(): ArmyDao = armyDao
    fun buildTaskDao(): BuildTaskDao = buildTaskDao
    fun trainingJobDao(): TrainingJobDao = trainingJobDao
    fun researchJobDao(): ResearchJobDao = researchJobDao
    fun scientistTrainingJobDao(): ScientistTrainingJobDao = scientistTrainingJobDao
    fun marketDao(): MarketDao = marketDao
    fun messageDao(): MessageDao = messageDao
    fun chatDao(): ChatDao = chatDao
    fun warDao(): WarDao = warDao
    fun warMoveDao(): WarMoveDao = warMoveDao
    fun warLogDao(): WarLogDao = warLogDao
    fun allianceDao(): AllianceDao = allianceDao

    // --- Command Center ---
    val commandCenterDao: CommandCenterDao = object : CommandCenterDao {
        override fun getStateByRuler(rulerName: String): CommandCenterState? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getCommandCenter(rulerName) }
                if (resp.success && resp.commandCenter != null) resp.commandCenter.toState() else null
            } catch (_: Exception) {
                null
            }
        }
        override fun insertState(state: CommandCenterState) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.saveCommandCenter(state.toDto()) }
            } catch (_: Exception) {
                // Игнорируем ошибки сети
            }
        }
    }

    // --- General ---
    val generalDao: GeneralDao = object : GeneralDao {
        override fun getByRuler(rulerName: String): GeneralState? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getGeneral(rulerName) }
                if (resp.success && resp.general != null) resp.general.toState() else null
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(state: GeneralState) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.saveGeneral(state.toDto()) }
            } catch (_: Exception) {
                // ignore network errors
            }
        }
    }

    // --- Army ---
    val armyDao: ArmyDao = object : ArmyDao {
        override fun getByRuler(rulerName: String): ArmyState? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getArmy(rulerName) }
                if (resp.success && resp.army != null) resp.army.toArmyState() else null
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(state: ArmyState) {
            try {
                runBlocking(Dispatchers.IO) {
                    ApiClient.apiService.saveArmy(
                        rulerName = state.rulerName,
                        peh = state.infantry,
                        kaz = state.cossacks,
                        gva = state.guards
                    )
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        // В онлайн‑версии удаление армии не поддерживается — игнорируем вызов
        override fun deleteByRuler(rulerName: String) {
            // Нет эндпоинта удаления армии на сервере
        }
    }

    // --- Build tasks ---
    val buildTaskDao: BuildTaskDao = object : BuildTaskDao {
        override fun getTasksForRuler(rulerName: String): List<BuildTaskEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getBuildTasks(rulerName) }
                resp.tasks?.map { it.toEntity() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun insert(task: BuildTaskEntity): Long {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.addBuildTask(task.toDto()) }
                resp.data?.id ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        override fun getTaskById(id: Long): BuildTaskEntity? {
            return try {
                // Используем эндпоинт build_task_get.php, который возвращает одну задачу по id
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getBuildTaskById(id) }
                if (!resp.success || resp.task == null) return null
                return resp.task.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun delete(task: BuildTaskEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteBuildTask(task.id) }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun update(task: BuildTaskEntity) {
            try {
                runBlocking(Dispatchers.IO) {
                    ApiClient.apiService.deleteBuildTask(task.id)
                    ApiClient.apiService.addBuildTask(task.toDto())
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun deleteById(id: Long) {
            try {
                // Удаляем задачу на сервере по id. Сервер не требует имени правителя.
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteBuildTask(id) }
            } catch (_: Exception) {
                // игнорируем ошибки сети
            }
        }
        override fun deleteAllForRuler(rulerName: String) {
            val tasks = getTasksForRuler(rulerName)
            try {
                runBlocking(Dispatchers.IO) {
                    tasks.forEach { ApiClient.apiService.deleteBuildTask(it.id) }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // --- Training job ---
    val trainingJobDao: TrainingJobDao = object : TrainingJobDao {
        override fun getJobForRuler(rulerName: String): TrainingJobEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getTrainingJobs(rulerName) }
                resp.jobs?.firstOrNull()?.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(job: TrainingJobEntity): Long {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.addTrainingJob(job.toDto()) }
                resp.data?.id ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        override fun delete(job: TrainingJobEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteTrainingJob(job.id) }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun deleteByRuler(rulerName: String) {
            val job = getJobForRuler(rulerName) ?: return
            delete(job)
        }
    }

    // --- Research job ---
    val researchJobDao: ResearchJobDao = object : ResearchJobDao {
        override fun getJobForRuler(rulerName: String): ResearchJobEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getResearchJobs(rulerName) }
                resp.jobs?.firstOrNull()?.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(job: ResearchJobEntity): Long {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.addResearchJob(job.toDto()) }
                resp.data?.id ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        override fun delete(job: ResearchJobEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteResearchJob(job.id) }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun deleteByRuler(rulerName: String) {
            val job = getJobForRuler(rulerName) ?: return
            delete(job)
        }
    }

    // --- Scientist training job ---
    val scientistTrainingJobDao: ScientistTrainingJobDao = object : ScientistTrainingJobDao {
        override fun getJobForRuler(rulerName: String): ScientistTrainingJobEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getScientistTrainingJobs(rulerName) }
                resp.jobs?.firstOrNull()?.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(job: ScientistTrainingJobEntity): Long {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.addScientistTrainingJob(job.toDto()) }
                resp.data?.id ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        override fun delete(job: ScientistTrainingJobEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteScientistTrainingJob(job.id) }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun deleteByRuler(rulerName: String) {
            val job = getJobForRuler(rulerName) ?: return
            delete(job)
        }
    }

    // --- Market ---
    val marketDao: MarketDao = object : MarketDao {
        override fun getOfferForRulerAndResource(rulerName: String, resourceType: Int): MarketOfferEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMarketOffers(resourceType, null) }
                resp.offers?.firstOrNull { it.rulerName == rulerName }?.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun getOffersForResource(resourceType: Int, excludeRuler: String): List<MarketOfferEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMarketOffers(resourceType, excludeRuler) }
                resp.offers?.map { it.toEntity() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun getAllOffersForResource(resourceType: Int): List<MarketOfferEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMarketOffers(resourceType, null) }
                resp.offers?.map { it.toEntity() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun getOffersForRuler(rulerName: String): List<MarketOfferEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMarketOffers(null, null) }
                resp.offers?.filter { it.rulerName == rulerName }?.map { it.toEntity() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun getById(id: Long): MarketOfferEntity? {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMarketOffers(null, null) }
                resp.offers?.firstOrNull { it.id == id }?.toEntity()
            } catch (_: Exception) {
                null
            }
        }
        override fun insert(offer: MarketOfferEntity): Long {
            return try {
                val resp = runBlocking(Dispatchers.IO) {
                    ApiClient.apiService.addMarketOffer(
                        rulerName = offer.rulerName,
                        resourceType = offer.resourceType,
                        amount = offer.amount,
                        pricePerUnit = offer.pricePerUnit
                    )
                }
                resp.offerId ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
        override fun update(offer: MarketOfferEntity) {
            try {
                runBlocking(Dispatchers.IO) {
                    ApiClient.apiService.deleteMarketOffer(offerId = offer.id, rulerName = offer.rulerName)
                    ApiClient.apiService.addMarketOffer(
                        rulerName = offer.rulerName,
                        resourceType = offer.resourceType,
                        amount = offer.amount,
                        pricePerUnit = offer.pricePerUnit
                    )
                }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun delete(offer: MarketOfferEntity) {
            try {
                runBlocking(Dispatchers.IO) { ApiClient.apiService.deleteMarketOffer(offerId = offer.id, rulerName = offer.rulerName) }
            } catch (_: Exception) {
                // ignore
            }
        }
        override fun deleteForRulerAndResource(rulerName: String, resourceType: Int) {
            val offers = getOffersForResource(resourceType, excludeRuler = "")
            try {
                runBlocking(Dispatchers.IO) {
                    offers.filter { it.rulerName == rulerName }.forEach {
                        ApiClient.apiService.deleteMarketOffer(it.id, it.rulerName)
                    }
                }
            } catch (_: Exception) {
                // ignore
            }
        }
    }

    // --- Messages ---
    val messageDao: MessageDao = object : MessageDao {
        override fun insert(message: MessageEntity): Long {
            // Для исходящих сообщений определяем отправителя и получателя.
            // В обычных уведомлениях сообщение адресовано самому правителю
            // (sender = target = rulerName). Для приглашений в союз и
            // подобных уведомлений поле payloadRuler указывает инициатора,
            // а rulerName — кому сообщение должно прийти. В этом случае
            // отправителем является инициатор (payloadRuler), а получателем
            // — rulerName. Такое разделение позволяет приглашениям
            // правильно уходить адресату, а не к инициатору.
            val sender: String = message.payloadRuler ?: message.rulerName
            val target: String = message.rulerName
            try {
                // Отправку сообщения выполняем в фоне, чтобы не блокировать UI поток.
                GlobalScope.launch(Dispatchers.IO) {
                    try {
                        ApiClient.apiService.sendMessage(
                            senderRuler = sender,
                            targetRuler = target,
                            text = message.text,
                            type = message.type,
                            payloadRuler = message.payloadRuler
                        )
                    } catch (_: Exception) {
                        // игнорируем сетевые ошибки
                    }
                }
            } catch (_: Exception) {
                // ignore any other errors
            }
            return 0L
        }
        override fun getMessagesForRuler(rulerName: String): List<MessageEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getMessages(rulerName) }
                resp.messages?.map { it.toEntity() } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun deleteAllForRuler(rulerName: String) {
            // Нет эндпоинта удаления всех сообщений — игнор
        }
        override fun getUnreadCount(rulerName: String): Int {
            return getMessagesForRuler(rulerName).count { !it.isRead }
        }
        override fun markAllAsRead(rulerName: String) {
            // Нет эндпоинта пометки — игнор
        }
    }

    // --- Chat ---
    val chatDao: ChatDao = object : ChatDao {
        override fun insert(message: ChatMessageEntity): Long {
            try {
                runBlocking(Dispatchers.IO) {
                    ApiClient.apiService.sendChatMessage(
                        rulerName = message.rulerName,
                        countryName = message.countryName,
                        text = message.text,
                        isPrivate = if (message.isPrivate) 1 else 0,
                        targetRulerName = message.targetRulerName,
                        isSystem = if (message.isSystem) 1 else 0,
                        medalPath = message.medalPath
                    )
                }
            } catch (_: Exception) {
                // ignore errors
            }
            return 0L
        }
        override fun getLastMessages(limit: Int): List<ChatMessageEntity> {
            return try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getChatMessages(null) }
                val all = resp.messages?.map { it.toEntity() } ?: emptyList()
                all.takeLast(limit).reversed()
            } catch (_: Exception) {
                emptyList()
            }
        }
        override fun deleteAll() {
            // Нет эндпоинта — игнор
        }
    }

    // --- Other DAOs ---
    // Нереализованные DAO возвращают пустые объекты или игнорируют вызовы.
    // Это необходимо для удовлетворения интерфейсов Room, хотя в онлайн‑режиме
    // сущности войны и союзов хранятся только на сервере.
    val warDao: WarDao = object : WarDao {
        /**
         * Загружает список войн для правителя с сервера. Каждая запись
         * конвертируется в WarEntity. В случае ошибок сеть возвращает
         * пустой список. Сервер возвращает только незавершённые войны.
         */
        override fun getWarsForRuler(rulerName: String): List<WarEntity> {
            val serverWars = try {
                val resp = runBlocking(Dispatchers.IO) { ApiClient.apiService.getWarsForRuler(rulerName) }
                if (!resp.success) emptyList() else resp.wars?.map { dto ->
                    // Конвертация WarDto -> WarEntity. catapults не поддерживается сервером.
                    // Сервер возвращает таймстемпы в секундах — переводим в миллисекунды,
                    // чтобы сравнение с System.currentTimeMillis() работало корректно.
                    val startAtMs = dto.startAt * 1000
                    val canRaidAtMs = dto.canRaidAt * 1000
                    val canCaptureAtMs = dto.canCaptureAt * 1000

                    WarEntity(
                        id = dto.id,
                        attackerRuler = dto.attackerRuler,
                        defenderRuler = dto.defenderRuler,
                        attackerCountry = dto.attackerCountry,
                        defenderCountry = dto.defenderCountry,
                        peh = dto.attackerPeh,
                        kaz = dto.attackerKaz,
                        gva = dto.attackerGva,
                        catapults = 0,
                        startAt = startAtMs,
                        canRaidAt = canRaidAtMs,
                        canCaptureAt = canCaptureAtMs,
                        endedAt = null,
                        state = dto.state,
                        isResolved = dto.isResolved,
                        attackerWon = dto.attackerWon,
                        reconAcc = dto.reconAcc
                    )
                } ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            synchronized(warCache) {
                // Обновляем кэш только если сервер ответил корректно.
                if (serverWars.isNotEmpty()) {
                    serverWars.forEach { warCache[it.id] = it }
                    // После обновления с сервера сохраняем кэш. Это позволит
                    // восстановить актуальные войны после перезапуска приложения,
                    // даже если war_list.php временно недоступен.
                    saveWarCache()
                }

                // Для мгновенного отображения только что объявленной войны добавляем
                // локальные записи из кэша, которых ещё нет на сервере.
                val merged = (serverWars + warCache.values)
                    .filter { it.attackerRuler == rulerName || it.defenderRuler == rulerName }
                    .associateBy { it.id }
                    .values
                    .sortedByDescending { it.startAt }

                return merged
            }
        }

        /**
         * Получение конкретной войны не поддерживается напрямую сервером.
         * Для простоты повторно загружаем все войны правителя и ищем
         * нужную по id. Если пользователь не найден, возвращаем null.
         */
        override fun getById(id: Long): WarEntity? {
            // Попытаться найти войну среди войн всех правителей нецелесообразно.
            // Возвращаем null; WarDetailsFragment будет использовать passed id только
            // в рамках текущего списка войн.
            return null
        }
        override fun deleteWarsOfRuler(rulerName: String) {
            // Удаление войн на сервере не поддерживается
        }
        override fun insert(war: WarEntity): Long {
            // Создание войны осуществляется через war_declare.php, но сохраняем
            // локальную копию, чтобы UI мог сразу показать новую войну до ответа
            // war_list.php. Также сохраняем кэш в SharedPreferences, чтобы
            // объявленная война не исчезла после перезапуска приложения.
            synchronized(warCache) {
                warCache[war.id] = war
                // После изменения кэша сохраняем его на диск
                saveWarCache()
            }
            return war.id
        }
        override fun update(war: WarEntity) {
            // Нет отдельного эндпоинта обновления войны. Локально не храним войны,
            // поэтому обновления игнорируются.
        }
        override fun byId(id: Long): WarEntity? = getById(id)
        override fun countActiveAsDefender(rulerName: String): Int {
            return getWarsForRuler(rulerName).count { it.defenderRuler == rulerName && it.state == "active" }
        }
        override fun countActiveForAttacker(rulerName: String): Int {
            return getWarsForRuler(rulerName).count { it.attackerRuler == rulerName && it.state == "active" }
        }
        override fun getActiveEnemiesForRuler(rulerName: String): List<String> {
            return getWarsForRuler(rulerName)
                .filter { it.state == "active" && (it.attackerRuler == rulerName || it.defenderRuler == rulerName) }
                .map {
                    if (it.attackerRuler == rulerName) it.defenderRuler else it.attackerRuler
                }
        }
    }

    val warMoveDao: WarMoveDao = object : WarMoveDao {
        override fun insert(m: WarMoveEntity) {}
        override fun byWar(warId: Long): List<WarMoveEntity> = emptyList()
        override fun deleteByWarIds(warIds: List<Long>) {}
    }

    val warLogDao: WarLogDao = object : WarLogDao {
        override fun insert(l: WarLogEntity) {}
        override fun byWar(warId: Long): List<WarLogEntity> = emptyList()
        override fun deleteByWarIds(warIds: List<Long>) {}
    }

    val allianceDao: AllianceDao = object : AllianceDao {
        override fun getAlliance(a: String, b: String): AllianceEntity? = null
        override fun insert(alliance: AllianceEntity): Long = 0L
        override fun update(alliance: AllianceEntity) {}
        override fun getActiveAlliancesForRuler(ruler: String): List<AllianceEntity> = emptyList()
        override fun getPendingForRuler(ruler: String): List<AllianceEntity> = emptyList()
        override fun getExpiredPendingForRuler(ruler: String, now: Long): List<AllianceEntity> = emptyList()
    }

    /**
     * Загружает сохранённый кэш войн из SharedPreferences в память. Если
     * сохранённого состояния нет или формат повреждён, метод ничего не
     * делает. Вызывается при создании экземпляра базы в getInstance().
     */
    private fun loadWarCache() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences("derzhava_wars", Context.MODE_PRIVATE)
        val json = prefs.getString("wars", null) ?: return
        try {
            val arr = org.json.JSONArray(json)
            synchronized(warCache) {
                warCache.clear()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.getLong("id")
                    val attackerRuler = obj.getString("attackerRuler")
                    val defenderRuler = obj.getString("defenderRuler")
                    val attackerCountry = obj.getString("attackerCountry")
                    val defenderCountry = obj.getString("defenderCountry")
                    val peh = obj.getInt("peh")
                    val kaz = obj.getInt("kaz")
                    val gva = obj.getInt("gva")
                    val catapults = obj.getInt("catapults")
                    val startAt = obj.getLong("startAt")
                    val canRaidAt = obj.getLong("canRaidAt")
                    val canCaptureAt = obj.getLong("canCaptureAt")
                    val state = obj.getString("state")
                    val isResolved = obj.getBoolean("isResolved")
                    val attackerWon = if (obj.isNull("attackerWon")) null else obj.getBoolean("attackerWon")
                    val reconAcc = obj.getInt("reconAcc")
                    warCache[id] = WarEntity(
                        id = id,
                        attackerRuler = attackerRuler,
                        defenderRuler = defenderRuler,
                        attackerCountry = attackerCountry,
                        defenderCountry = defenderCountry,
                        peh = peh,
                        kaz = kaz,
                        gva = gva,
                        catapults = catapults,
                        startAt = startAt,
                        canRaidAt = canRaidAt,
                        canCaptureAt = canCaptureAt,
                        state = state,
                        isResolved = isResolved,
                        attackerWon = attackerWon,
                        reconAcc = reconAcc
                    )
                }
            }
        } catch (_: Exception) {
            // Игнорируем повреждённый формат
        }
    }

    /**
     * Сохраняет текущий кэш войн в SharedPreferences. Вызывать после
     * изменений кэша (insert/update/load). Операция выполняется
     * синхронно, объём данных невелик, поэтому задержка минимальна.
     */
    private fun saveWarCache() {
        val ctx = appContext ?: return
        val arr = org.json.JSONArray()
        synchronized(warCache) {
            for (war in warCache.values) {
                val obj = org.json.JSONObject()
                obj.put("id", war.id)
                obj.put("attackerRuler", war.attackerRuler)
                obj.put("defenderRuler", war.defenderRuler)
                obj.put("attackerCountry", war.attackerCountry)
                obj.put("defenderCountry", war.defenderCountry)
                obj.put("peh", war.peh)
                obj.put("kaz", war.kaz)
                obj.put("gva", war.gva)
                obj.put("catapults", war.catapults)
                obj.put("startAt", war.startAt)
                obj.put("canRaidAt", war.canRaidAt)
                obj.put("canCaptureAt", war.canCaptureAt)
                obj.put("state", war.state)
                obj.put("isResolved", war.isResolved)
                obj.putOpt("attackerWon", war.attackerWon)
                obj.put("reconAcc", war.reconAcc)
                arr.put(obj)
            }
        }
        val prefs = ctx.getSharedPreferences("derzhava_wars", Context.MODE_PRIVATE)
        prefs.edit().putString("wars", arr.toString()).apply()
    }

    // --- Singleton access ---
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase()
                // Сохраняем applicationContext для работы с SharedPreferences
                instance.appContext = context.applicationContext
                // Загружаем кэш войн из SharedPreferences при создании базы
                instance.loadWarCache()
                INSTANCE = instance
                instance
            }
        }
    }
}