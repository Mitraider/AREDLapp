package com.example.aredlapp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class LevelResponse(
    val id: String,
    val level_id: Int? = null,
    val name: String,
    val position: Int,
    val points: Double,
    val description: String? = null,
    val tags: List<String>? = null,
    val song: Int? = null,
    val song_id: Int? = null,
    val edel_enjoyment: Double? = null,
    val gddl_tier: Double? = null,
    val nlw_tier: JsonElement? = null,
    val video: String? = null,
    val thumbnail: String? = null,
    val publisher: UserInfo? = null,
    val creator: UserInfo? = null,
    val records: List<LevelRecord>? = null,
    val global_name: String? = null
)

@Serializable
data class LevelRecord(
    val player: UserInfo? = null,
    val user: UserInfo? = null,
    val video: String? = null,
    val video_url: String? = null,
    val points: Double? = null,
    val list_points: Double? = null,
    val id: String? = null,
    val level_id: Int? = null
)

@Serializable
data class UserInfo(
    val id: String? = null,
    val username: String? = null,
    val discord_id: String? = null,
    val global_name: String? = null,
    val discord_avatar: String? = null,
    val avatar: String? = null
)

@Serializable
data class LeaderboardResponse(
    val user: UserInfo? = null,
    val total_points: Double? = 0.0,
    val rank: Int? = 0,
    val country: String? = null,
    val extremes: Int? = null,
    val clan: ClanInfo? = null,
    val hardest: HardestLevelInfo? = null
)

@Serializable
data class HardestLevelInfo(
    val id: String? = null,
    val name: String? = null
)

@Serializable
data class ClanInfo(
    val id: String? = null,
    val name: String? = null,
    val global_name: String? = null
)

@Serializable
data class PaginatedLeaderboardResponse(
    val page: Int? = null,
    val per_page: Int? = null,
    val data: List<LeaderboardResponse> = emptyList(),
    val pages: Int = 1,
    val last_refreshed: String? = null
)

@Serializable
data class RecordInfo(
    val level: LevelResponse? = null,
    val points: Double? = null,
    val list_points: Double? = null,
    val video: String? = null,
    val video_url: String? = null,
    val id: String? = null,
    val level_id: Int? = null,
    val name: String? = null,
    val position: Int? = null,
    var achieved_percent: Int? = null
)

@Serializable
data class ProfileResponse(
    val user: UserInfo? = null,
    val records: List<RecordInfo> = emptyList(),
    val background_level: String? = null
)

@Serializable
data class RoleResponse(
    val id: JsonElement? = null,
    val name: String = "Role",
    val color: String? = null,
    val users: List<JsonElement> = emptyList(),
    val hide: Boolean = false,
    val privilegeLevel: Int = 0
)

@Serializable
data class BasePackTierResponse(
    val id: String,
    val name: String,
    val color: String
)

@Serializable
data class LevelPackResponse(
    val id: String,
    val name: String,
    val tier: BasePackTierResponse
)

@Serializable
data class PackLevelResponse(
    val id: String,
    val name: String,
    val level_id: Int? = null,
    val position: Int = 0,
    val points: Double = 0.0,
    val legacy: Boolean? = null,
    val completed_by_user: Boolean? = null
)

@Serializable
data class PackResponse(
    val id: String,
    val name: String,
    val points: Int = 0,
    val levels: List<PackLevelResponse> = emptyList()
)

@Serializable
data class PackTierResolvedResponse(
    val id: String,
    val name: String,
    val color: String,
    val placement: Int = 0,
    val packs: List<PackResponse> = emptyList()
)

data class UserSubmissionInfo(
    val submissionId: String,
    val levelId: String,
    val rawStatus: String,
    val updatedAt: String? = null
) {
    val displayStatus: String
        get() = when (rawStatus) {
            "Accepted" -> "Accepted"
            "Denied" -> "Refused"
            else -> "Pending"
        }
}

data class UserSubmissionLevelItem(
    val level: LevelResponse,
    val submission: UserSubmissionInfo
)

data class SubmissionDetailResponse(
    val id: String,
    val level: LevelResponse,
    val rawStatus: String,
    val mobile: Boolean,
    val ldmId: Int?,
    val videoUrl: String,
    val rawUrl: String?,
    val modMenu: String?,
    val userNotes: String?,
    val priority: Boolean,
    val locked: Boolean,
    val reviewerNotes: String?,
    val createdAt: String?,
    val updatedAt: String?
) {
    val displayStatus: String
        get() = when (rawStatus) {
            "Accepted" -> "Accepted"
            "Denied" -> "Refused"
            else -> "Pending"
        }

    val isActivelyReviewed: Boolean
        get() = rawStatus == "Claimed" || rawStatus == "UnderReview" || rawStatus == "UnderConsideration"
}

data class SubmissionQueuePosition(
    val position: Int,
    val priority: Boolean
)

data class SubmissionQueueSummary(
    val regularSubmissionsInQueue: Int,
    val prioritySubmissionsInQueue: Int,
    val underConsiderationSubmissions: Int
)

enum class SubmissionScreenMode {
    VIEW,
    CREATE
}

data class SubmissionDetailUiState(
    val mode: SubmissionScreenMode = SubmissionScreenMode.VIEW,
    val detail: SubmissionDetailResponse?,
    val queuePosition: SubmissionQueuePosition?,
    val queueSummary: SubmissionQueueSummary?,
    val submissionsOpen: Boolean? = null,
    val isLoading: Boolean = false
)

data class SubmissionEditForm(
    val levelId: String,
    val mobile: Boolean,
    val ldmId: String,
    val videoUrl: String,
    val rawUrl: String,
    val modMenu: String,
    val userNotes: String
)
