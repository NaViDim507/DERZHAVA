package com.example.derzhava.net

import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Body

/**
 * Retrofit-интерфейс для связи с сервером. Здесь описаны все PHP-эндпоинты,
 * обеспечивающие полноценную онлайн‑работу игры без локальной базы данных.
 */
interface ApiService {

    // ===========================
    //   Аутентификация
    // ===========================

    /**
     * Вход под существующим правителем.
     */
    @FormUrlEncoded
    @POST("login.php")
    suspend fun login(
        @Field("mode") mode: String = "login",
        @Field("ruler_name") rulerName: String,
        @Field("password") password: String
    ): LoginResponse

    /**
     * Регистрация нового правителя и страны.
     */
    @FormUrlEncoded
    @POST("login.php")
    suspend fun register(
        @Field("mode") mode: String = "register",
        @Field("ruler_name") rulerName: String,
        @Field("country_name") countryName: String,
        @Field("password") password: String
    ): RegisterResponse

    // ===========================
    //   Состояние страны
    // ===========================

    /**
     * Получить параметры страны для правителя.
     */
    @FormUrlEncoded
    @POST("country_get.php")
    suspend fun getCountry(
        @Field("ruler_name") rulerName: String
    ): CountryGetResponse

    /**
     * Сохранить/обновить параметры страны на сервере. Передаётся JSON‑тело.
     */
    @POST("country_save.php")
    suspend fun saveCountry(
        @Body country: CountryDto
    ): CountrySaveResponse

    // ===========================
    //   Война
    // ===========================

    @FormUrlEncoded
    @POST("war_declare.php")
    suspend fun declareWar(
        @Field("attacker_ruler") attackerRuler: String,
        @Field("defender_ruler") defenderRuler: String,
        @Field("peh") peh: Int,
        @Field("kaz") kaz: Int,
        @Field("gva") gva: Int
    ): SimpleWarResponse

    @FormUrlEncoded
    @POST("war_list.php")
    suspend fun getWarsForRuler(
        @Field("ruler_name") rulerName: String
    ): WarListResponse

    @FormUrlEncoded
    @POST("war_reinforce.php")
    suspend fun sendReinforcements(
        @Field("war_id") warId: Long,
        @Field("attacker_ruler") attackerRuler: String,
        @Field("peh") peh: Int,
        @Field("kaz") kaz: Int,
        @Field("gva") gva: Int
    ): SimpleWarResponse

    @FormUrlEncoded
    @POST("war_recall.php")
    suspend fun recallTroops(
        @Field("war_id") warId: Long,
        @Field("attacker_ruler") attackerRuler: String,
        @Field("peh") peh: Int,
        @Field("kaz") kaz: Int,
        @Field("gva") gva: Int
    ): SimpleWarResponse

    /**
     * Оборона против вторжения. Защитник отправляет войска на бой.
     * Если защита побеждает, война завершается; если проигрывает, война продолжается.
     * Сервер возвращает объект WarDefenseResponse с информацией о новом состоянии войны
     * и остатках войск атакующего.
     */
    @FormUrlEncoded
    @POST("war_defense.php")
    suspend fun warDefense(
        @Field("war_id") warId: Long,
        @Field("defender_ruler") defenderRuler: String,
        @Field("peh") peh: Int,
        @Field("kaz") kaz: Int,
        @Field("gva") gva: Int
    ): WarDefenseResponse

    @FormUrlEncoded
    @POST("war_recon.php")
    suspend fun warRecon(
        @Field("war_id") warId: Long,
        @Field("attacker_ruler") attackerRuler: String
    ): WarReconResponse

    @FormUrlEncoded
    @POST("war_demolish.php")
    suspend fun warDemolish(
        @Field("war_id") warId: Long,
        @Field("attacker_ruler") attackerRuler: String,
        @Field("building_key") buildingKey: String
    ): WarDemolishResponse

    @FormUrlEncoded
    @POST("war_capture.php")
    suspend fun warCapture(
        @Field("war_id") warId: Long,
        @Field("attacker_ruler") attackerRuler: String,
        @Field("attacker_won") attackerWon: Int
    ): WarCaptureResponse

    // ===========================
    //   Армия
    // ===========================

    @FormUrlEncoded
    @POST("army_get.php")
    suspend fun getArmy(
        @Field("ruler_name") rulerName: String
    ): ArmyResponse

    @FormUrlEncoded
    @POST("army_save.php")
    suspend fun saveArmy(
        @Field("ruler_name") rulerName: String,
        @Field("peh") peh: Int,
        @Field("kaz") kaz: Int,
        @Field("gva") gva: Int
    ): ArmySaveResponse

    // ===========================
    //   Командный центр
    // ===========================

    @FormUrlEncoded
    @POST("command_get.php")
    suspend fun getCommandCenter(
        @Field("ruler_name") rulerName: String
    ): CommandGetResponse

    @POST("command_save.php")
    suspend fun saveCommandCenter(
        @Body commandCenter: CommandCenterDto
    ): CommandSaveResponse

    // ===========================
    //   Генерал
    // ===========================

    @FormUrlEncoded
    @POST("general_get.php")
    suspend fun getGeneral(
        @Field("ruler_name") rulerName: String
    ): GeneralGetResponse

    @POST("general_save.php")
    suspend fun saveGeneral(
        @Body general: GeneralDto
    ): GeneralSaveResponse

    // ===========================
    //   Задачи строительства
    // ===========================

    @FormUrlEncoded
    @POST("build_tasks_get.php")
    suspend fun getBuildTasks(
        @Field("ruler_name") rulerName: String
    ): BuildTasksResponse

    @POST("build_task_add.php")
    suspend fun addBuildTask(
        @Body task: BuildTaskDto
    ): BuildTaskAddResponse

    @FormUrlEncoded
    @POST("build_task_delete.php")
    suspend fun deleteBuildTask(
        @Field("id") id: Long
    ): SimpleResponse

    /**
     * Получить одну задачу строительства по id. Используется для
     * корректного отображения экрана стройки. Сервер вернёт
     * task=null, если задача не найдена.
     */
    @FormUrlEncoded
    @POST("build_task_get.php")
    suspend fun getBuildTaskById(
        @Field("id") id: Long
    ): BuildTaskGetResponse

    // ===========================
    //   Обучение войск (kmb)
    // ===========================

    @FormUrlEncoded
    @POST("training_jobs_get.php")
    suspend fun getTrainingJobs(
        @Field("ruler_name") rulerName: String
    ): TrainingJobsResponse

    @POST("training_job_add.php")
    suspend fun addTrainingJob(
        @Body job: TrainingJobDto
    ): TrainingJobAddResponse

    @FormUrlEncoded
    @POST("training_job_delete.php")
    suspend fun deleteTrainingJob(
        @Field("id") id: Long
    ): SimpleResponse

    // ===========================
    //   Научные исследования
    // ===========================

    @FormUrlEncoded
    @POST("research_jobs_get.php")
    suspend fun getResearchJobs(
        @Field("ruler_name") rulerName: String
    ): ResearchJobsResponse

    @POST("research_job_add.php")
    suspend fun addResearchJob(
        @Body job: ResearchJobDto
    ): ResearchJobAddResponse

    @FormUrlEncoded
    @POST("research_job_delete.php")
    suspend fun deleteResearchJob(
        @Field("id") id: Long
    ): SimpleResponse

    // ===========================
    //   Обучение учёных
    // ===========================

    @FormUrlEncoded
    @POST("scientist_training_jobs_get.php")
    suspend fun getScientistTrainingJobs(
        @Field("ruler_name") rulerName: String
    ): ScientistTrainingJobsResponse

    @POST("scientist_training_job_add.php")
    suspend fun addScientistTrainingJob(
        @Body job: ScientistTrainingJobDto
    ): ScientistTrainingJobAddResponse

    @FormUrlEncoded
    @POST("scientist_training_job_delete.php")
    suspend fun deleteScientistTrainingJob(
        @Field("id") id: Long
    ): SimpleResponse

    // ===========================
    //   Биржа (рынок)
    // ===========================

    @FormUrlEncoded
    @POST("market_get.php")
    suspend fun getMarketOffers(
        @Field("resource_type") resourceType: Int?,
        @Field("exclude_ruler") excludeRuler: String?
    ): MarketResponse

    @FormUrlEncoded
    @POST("market_add.php")
    suspend fun addMarketOffer(
        @Field("ruler_name") rulerName: String,
        @Field("resource_type") resourceType: Int,
        @Field("amount") amount: Int,
        @Field("price_per_unit") pricePerUnit: Int
    ): MarketAddResponse

    @FormUrlEncoded
    @POST("market_delete.php")
    suspend fun deleteMarketOffer(
        @Field("offer_id") offerId: Long,
        @Field("ruler_name") rulerName: String
    ): SimpleResponse

    // ===========================
    //   Личные сообщения
    // ===========================

    @FormUrlEncoded
    @POST("messages_get.php")
    suspend fun getMessages(
        @Field("ruler_name") rulerName: String
    ): MessagesResponse

    @FormUrlEncoded
    @POST("message_send.php")
    suspend fun sendMessage(
        @Field("sender_ruler") senderRuler: String,
        @Field("target_ruler") targetRuler: String,
        @Field("text") text: String,
        @Field("type") type: String,
        @Field("payload_ruler") payloadRuler: String?
    ): MessageSendResponse

    // ===========================
    //   Общий чат (Ассамблея)
    // ===========================

    @FormUrlEncoded
    @POST("chat_get.php")
    suspend fun getChatMessages(
        @Field("since_ts") sinceTimestamp: Long?
    ): ChatResponse

    @FormUrlEncoded
    @POST("chat_send.php")
    suspend fun sendChatMessage(
        @Field("ruler_name") rulerName: String,
        @Field("country_name") countryName: String,
        @Field("text") text: String,
        @Field("is_private") isPrivate: Int,
        @Field("target_ruler_name") targetRulerName: String?,
        @Field("is_system") isSystem: Int,
        @Field("medal_path") medalPath: String?
    ): ChatSendResponse

    // ===========================
    //   Спецоперации (NPC‑цели)
    // ===========================

    @FormUrlEncoded
    @POST("special_targets_get.php")
    suspend fun getSpecialTargets(
        @Field("ruler_name") rulerName: String
    ): SpecialTargetsResponse

    @FormUrlEncoded
    @POST("special_target_add.php")
    suspend fun addSpecialTarget(
        @Field("ruler_name") rulerName: String,
        @Field("country_name") countryName: String,
        @Field("perimeter") perimeter: Int,
        @Field("security") security: Int,
        @Field("money") money: Int,
        @Field("is_ally") isAlly: Int
    ): SpecialTargetAddResponse

    @FormUrlEncoded
    @POST("special_target_delete.php")
    suspend fun deleteSpecialTarget(
        @Field("target_id") targetId: Long,
        @Field("ruler_name") rulerName: String
    ): SimpleResponse

    // ===========================
    //   Администрирование / NPC
    // ===========================

    /**
     * Получить список всех NPC-стран. Возвращает объекты CountryDto
     * (может быть урезанными), но важно, что is_npc = true.
     */
    @FormUrlEncoded
    @POST("npc_list.php")
    suspend fun getNpcList(
        @Field("secret") secret: String = ""
    ): NpcListResponse

    /**
     * Получить список всех пользователей (как администраторов, так и обычных).
     * Используется в админ-панели для просмотра игроков. Параметр secret может
     * быть использован в будущем для авторизации. Сейчас не используется.
     */
    @FormUrlEncoded
    @POST("users_get.php")
    suspend fun getUsers(
        @Field("secret") secret: String = ""
    ): UsersListResponse
}