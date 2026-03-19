package com.example.aredlapp.utils

import com.example.aredlapp.models.LevelResponse

object LevelUtils {
    fun resolveCreatorName(level: LevelResponse): String? {
        return listOf(
            level.global_name,
            level.creator?.global_name,
            level.creator?.username,
            level.publisher?.global_name,
            level.publisher?.username
        ).firstOrNull { !it.isNullOrBlank() && !it.equals("AREDL", ignoreCase = true) }
    }
}
