package com.example.derzhava.net

import com.example.derzhava.data.CommandCenterState
import com.example.derzhava.data.GeneralState
import com.example.derzhava.data.BuildTaskEntity
import com.example.derzhava.data.TrainingJobEntity
import com.example.derzhava.data.ResearchJobEntity
import com.example.derzhava.data.ScientistTrainingJobEntity
import com.example.derzhava.data.MarketOfferEntity
import com.example.derzhava.data.MessageEntity
import com.example.derzhava.data.ChatMessageEntity
import com.example.derzhava.data.SpecialTargetEntity
import com.google.gson.annotations.SerializedName

/**
 * DTO для состояния командного центра. Соответствует структуре, возвращаемой
 * файлом command_get.php и принимаемой command_save.php. Поля времени
 * представлены в миллисекундах от эпохи Unix.
 */
data class CommandCenterDto(
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("intel") val intel: Int,
    @SerializedName("sabotage") val sabotage: Int,
    @SerializedName("theft") val theft: Int,
    @SerializedName("agitation") val agitation: Int,
    @SerializedName("last_recon_time") val lastReconTime: Long,
    @SerializedName("last_sabotage_time") val lastSabotageTime: Long,
    @SerializedName("last_theft_time") val lastTheftTime: Long,
    @SerializedName("last_alliance_time") val lastAllianceTime: Long
)

data class CommandGetResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("command_center") val commandCenter: CommandCenterDto?
)

data class CommandSaveResponse(
    val success: Boolean,
    val message: String?
)

/**
 * DTO для состояния генерала. Используется в general_get.php/general_save.php.
 */
data class GeneralDto(
    @SerializedName("ruler_name") val rulerName: String,
    val level: Int,
    val attack: Int,
    val defense: Int,
    val leadership: Int,
    val experience: Long,
    val battles: Int,
    val wins: Int
)

data class GeneralGetResponse(
    val success: Boolean,
    val message: String?,
    val general: GeneralDto?
)

data class GeneralSaveResponse(
    val success: Boolean,
    val message: String?
)

/**
 * Простая структура ответа API без вложенных данных. Многие скрипты возвращают
 * только флаги success/message. Используйте её в качестве возврата для
 * операций удаления и прочих простых действий.
 */
data class SimpleResponse(
    val success: Boolean,
    val message: String?
)

/**
 * DTO для задания строительства. Соответствует BuildTaskEntity, но
 * именование полей подстроено под ожидаемые ключи PHP (start_time_millis,
 * end_time_millis). id может быть null при добавлении новой записи.
 */
data class BuildTaskDto(
    val id: Long? = null,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("building_type") val buildingType: Int,
    val workers: Int,
    @SerializedName("start_time_millis") val startTimeMillis: Long,
    @SerializedName("end_time_millis") val endTimeMillis: Long
)

data class BuildTasksResponse(
    val success: Boolean,
    val message: String?,
    val tasks: List<BuildTaskDto>?
)

/**
 * Ответ на запрос одной задачи строительства. Поле task может быть null,
 * если задача не найдена или произошла ошибка.
 */
data class BuildTaskGetResponse(
    val success: Boolean,
    val message: String?,
    val task: BuildTaskDto?
)

data class BuildTaskAddData(
    val id: Long
)

data class BuildTaskAddResponse(
    val success: Boolean,
    val message: String?,
    val data: BuildTaskAddData?
)

/**
 * DTO для задачи обучения войск (kmb). Поле durationSeconds передаётся
 * отдельным числом, как ожидается сервером.
 */
data class TrainingJobDto(
    val id: Long? = null,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("unit_type") val unitType: Int,
    val workers: Int,
    val scientists: Int,
    @SerializedName("start_time_millis") val startTimeMillis: Long,
    @SerializedName("duration_seconds") val durationSeconds: Int
)

data class TrainingJobsResponse(
    val success: Boolean,
    val message: String?,
    val jobs: List<TrainingJobDto>?
)

data class TrainingJobAddData(
    val id: Long
)

data class TrainingJobAddResponse(
    val success: Boolean,
    val message: String?,
    val data: TrainingJobAddData?
)

/**
 * DTO для научных исследований. progressPoints представляет очки прогресса,
 * вычисляемые клиентом.
 */
data class ResearchJobDto(
    val id: Long? = null,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("science_type") val scienceType: Int,
    @SerializedName("start_time_millis") val startTimeMillis: Long,
    @SerializedName("duration_seconds") val durationSeconds: Int,
    val scientists: Int,
    @SerializedName("progress_points") val progressPoints: Int
)

data class ResearchJobsResponse(
    val success: Boolean,
    val message: String?,
    val jobs: List<ResearchJobDto>?
)

data class ResearchJobAddData(
    val id: Long
)

data class ResearchJobAddResponse(
    val success: Boolean,
    val message: String?,
    val data: ResearchJobAddData?
)

/**
 * DTO для обучения учёных. Совпадает с таблицей scientist_training_jobs.
 */
data class ScientistTrainingJobDto(
    val id: Long? = null,
    @SerializedName("ruler_name") val rulerName: String,
    val workers: Int,
    val scientists: Int,
    @SerializedName("start_time_millis") val startTimeMillis: Long,
    @SerializedName("duration_seconds") val durationSeconds: Int
)

data class ScientistTrainingJobsResponse(
    val success: Boolean,
    val message: String?,
    val jobs: List<ScientistTrainingJobDto>?
)

data class ScientistTrainingJobAddData(
    val id: Long
)

data class ScientistTrainingJobAddResponse(
    val success: Boolean,
    val message: String?,
    val data: ScientistTrainingJobAddData?
)

/**
 * DTO для лота на бирже. resourceType соответствует типу ресурса
 * (1‑металл,2‑камень,3‑дерево,4‑зерно,5‑рабочие,6‑учёные).
 */
data class MarketOfferDto(
    val id: Long,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("resource_type") val resourceType: Int,
    val amount: Int,
    @SerializedName("price_per_unit") val pricePerUnit: Int
)

data class MarketResponse(
    val success: Boolean,
    val message: String?,
    val offers: List<MarketOfferDto>?
)

data class MarketAddResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("offer_id") val offerId: Long?
)

/**
 * DTO для личного сообщения. Флаг isRead передаётся как boolean для удобства.
 */
data class MessageDto(
    val id: Long,
    @SerializedName("ruler_name") val rulerName: String,
    val text: String,
    @SerializedName("timestamp_millis") val timestampMillis: Long,
    @SerializedName("is_read") val isRead: Boolean,
    val type: String?,
    @SerializedName("payload_ruler") val payloadRuler: String?
)

data class MessagesResponse(
    val success: Boolean,
    val message: String?,
    val messages: List<MessageDto>?
)

data class MessageSendResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("message_id") val messageId: Long?
)

/**
 * DTO для сообщения общего чата. Флаги isPrivate и isSystem передаются как
 * boolean. medalPath может быть null, если нет медали.
 */
data class ChatMessageDto(
    val id: Long,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("country_name") val countryName: String,
    val text: String,
    @SerializedName("timestamp_millis") val timestampMillis: Long,
    @SerializedName("is_private") val isPrivate: Boolean,
    @SerializedName("target_ruler_name") val targetRulerName: String?,
    @SerializedName("is_system") val isSystem: Boolean,
    @SerializedName("medal_path") val medalPath: String?
)

data class ChatResponse(
    val success: Boolean,
    val message: String?,
    val messages: List<ChatMessageDto>?
)

data class ChatSendResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("message_id") val messageId: Long?
)

/**
 * DTO для NPC-целей спецопераций. money и security передаются как Int.
 */
data class SpecialTargetDto(
    val id: Long,
    @SerializedName("ruler_name") val rulerName: String,
    @SerializedName("country_name") val countryName: String,
    val perimeter: Int,
    val security: Int,
    val money: Int,
    @SerializedName("is_ally") val isAlly: Boolean
)

data class SpecialTargetsResponse(
    val success: Boolean,
    val message: String?,
    val targets: List<SpecialTargetDto>?
)

data class SpecialTargetAddResponse(
    val success: Boolean,
    val message: String?,
    @SerializedName("target_id") val targetId: Long?
)

// -------------------------------------------------------------------------
//   Мапперы из DTO в локальные сущности и обратно
// -------------------------------------------------------------------------

fun CommandCenterState.toDto(): CommandCenterDto = CommandCenterDto(
    rulerName = rulerName,
    intel = intel,
    sabotage = sabotage,
    theft = theft,
    agitation = agitation,
    lastReconTime = lastReconTime,
    lastSabotageTime = lastSabotageTime,
    lastTheftTime = lastTheftTime,
    lastAllianceTime = lastAllianceTime
)

fun CommandCenterDto.toState(): CommandCenterState = CommandCenterState(
    rulerName = rulerName,
    intel = intel,
    sabotage = sabotage,
    theft = theft,
    agitation = agitation,
    lastReconTime = lastReconTime,
    lastSabotageTime = lastSabotageTime,
    lastTheftTime = lastTheftTime,
    lastAllianceTime = lastAllianceTime
)

fun GeneralState.toDto(): GeneralDto = GeneralDto(
    rulerName = rulerName,
    level = level,
    attack = attack,
    defense = defense,
    leadership = leadership,
    experience = experience,
    battles = battles,
    wins = wins
)

fun GeneralDto.toState(): GeneralState = GeneralState(
    rulerName = rulerName,
    level = level,
    attack = attack,
    defense = defense,
    leadership = leadership,
    experience = experience,
    battles = battles,
    wins = wins
)

fun BuildTaskEntity.toDto(): BuildTaskDto = BuildTaskDto(
    id = id,
    rulerName = rulerName,
    buildingType = buildingType,
    workers = workers,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis
)

fun BuildTaskDto.toEntity(): BuildTaskEntity = BuildTaskEntity(
    id = id ?: 0L,
    rulerName = rulerName,
    buildingType = buildingType,
    workers = workers,
    startTimeMillis = startTimeMillis,
    endTimeMillis = endTimeMillis
)

fun TrainingJobEntity.toDto(): TrainingJobDto = TrainingJobDto(
    id = id,
    rulerName = rulerName,
    unitType = unitType,
    workers = workers,
    scientists = scientists,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds
)

fun TrainingJobDto.toEntity(): TrainingJobEntity = TrainingJobEntity(
    id = id ?: 0L,
    rulerName = rulerName,
    unitType = unitType,
    workers = workers,
    scientists = scientists,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds
)

fun ResearchJobEntity.toDto(): ResearchJobDto = ResearchJobDto(
    id = id,
    rulerName = rulerName,
    scienceType = scienceType,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds,
    scientists = scientists,
    progressPoints = progressPoints
)

fun ResearchJobDto.toEntity(): ResearchJobEntity = ResearchJobEntity(
    id = id ?: 0L,
    rulerName = rulerName,
    scienceType = scienceType,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds,
    scientists = scientists,
    progressPoints = progressPoints
)

fun ScientistTrainingJobEntity.toDto(): ScientistTrainingJobDto = ScientistTrainingJobDto(
    id = id,
    rulerName = rulerName,
    workers = workers,
    scientists = scientists,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds
)

fun ScientistTrainingJobDto.toEntity(): ScientistTrainingJobEntity = ScientistTrainingJobEntity(
    id = id ?: 0L,
    rulerName = rulerName,
    workers = workers,
    scientists = scientists,
    startTimeMillis = startTimeMillis,
    durationSeconds = durationSeconds
)

fun MarketOfferEntity.toDto(): MarketOfferDto = MarketOfferDto(
    id = id,
    rulerName = rulerName,
    resourceType = resourceType,
    amount = amount,
    pricePerUnit = pricePerUnit
)

fun MarketOfferDto.toEntity(): MarketOfferEntity = MarketOfferEntity(
    id = id,
    rulerName = rulerName,
    resourceType = resourceType,
    amount = amount,
    pricePerUnit = pricePerUnit
)

fun MessageEntity.toDto(): MessageDto = MessageDto(
    id = id,
    rulerName = rulerName,
    text = text,
    timestampMillis = timestampMillis,
    isRead = isRead,
    type = type,
    payloadRuler = payloadRuler
)

fun MessageDto.toEntity(): MessageEntity = MessageEntity(
    id = id,
    rulerName = rulerName,
    text = text,
    timestampMillis = timestampMillis,
    isRead = isRead,
    type = type ?: "generic",
    payloadRuler = payloadRuler
)

fun ChatMessageEntity.toDto(): ChatMessageDto = ChatMessageDto(
    id = id,
    rulerName = rulerName,
    countryName = countryName,
    text = text,
    timestampMillis = timestampMillis,
    isPrivate = isPrivate,
    targetRulerName = targetRulerName,
    isSystem = isSystem,
    medalPath = medalPath
)

fun ChatMessageDto.toEntity(): ChatMessageEntity = ChatMessageEntity(
    id = id,
    rulerName = rulerName,
    countryName = countryName,
    text = text,
    timestampMillis = timestampMillis,
    isPrivate = isPrivate,
    targetRulerName = targetRulerName,
    isSystem = isSystem,
    medalPath = medalPath
)

fun SpecialTargetEntity.toDto(): SpecialTargetDto = SpecialTargetDto(
    id = id,
    rulerName = rulerName,
    countryName = countryName,
    perimeter = perimeter,
    security = security,
    money = money,
    isAlly = isAlly
)

fun SpecialTargetDto.toEntity(): SpecialTargetEntity = SpecialTargetEntity(
    id = id,
    rulerName = rulerName,
    countryName = countryName,
    perimeter = perimeter,
    security = security,
    money = money,
    isAlly = isAlly
)