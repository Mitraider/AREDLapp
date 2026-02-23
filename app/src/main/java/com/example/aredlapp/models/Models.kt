package com.example.aredlapp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

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
    val data: List<LeaderboardResponse> = emptyList(),
    val pages: Int = 1
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
    val records: List<RecordInfo> = emptyList()
)
