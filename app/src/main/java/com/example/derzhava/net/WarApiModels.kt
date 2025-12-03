package com.example.derzhava.net

import com.google.gson.annotations.SerializedName

data class WarDto(
    val id: Long,

    @SerializedName("attacker_ruler")   val attackerRuler: String,
    @SerializedName("defender_ruler")   val defenderRuler: String,
    @SerializedName("attacker_country") val attackerCountry: String,
    @SerializedName("defender_country") val defenderCountry: String,

    @SerializedName("attacker_peh") val attackerPeh: Int,
    @SerializedName("attacker_kaz") val attackerKaz: Int,
    @SerializedName("attacker_gva") val attackerGva: Int,

    @SerializedName("total_raids")    val totalRaids: Int,
    @SerializedName("total_captures") val totalCaptures: Int,

    @SerializedName("start_at")       val startAt: Long,
    @SerializedName("can_raid_at")    val canRaidAt: Long,
    @SerializedName("can_capture_at") val canCaptureAt: Long,
    @SerializedName("last_demolition_at") val lastDemolitionAt: Long,

    val state: String,
    val isResolved: Boolean,
    val attackerWon: Boolean?,
    val reconAcc: Int
)

data class WarListResponse(
    val success: Boolean,
    val message: String?,
    val wars: List<WarDto>?
)

data class SimpleWarResponse(
    val success: Boolean,
    val message: String?,
    val war_id: Long? = null
)

// --- НОВОЕ: разведка и разрушение ---

data class ReconBuildingDto(
    val key: String,
    val name: String,
    val exists: Boolean,
    @SerializedName("can_demolish") val canDemolish: Boolean,
    @SerializedName("reward_type") val rewardType: String?,
    @SerializedName("reward_percent") val rewardPercent: Int?
)

data class WarReconResponse(
    val success: Boolean,
    val message: String?,
    val buildings: List<ReconBuildingDto>?
)

data class WarDemolishResponse(
    val success: Boolean,
    val message: String?
)