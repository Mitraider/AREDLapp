package com.example.aredlapp.utils

import android.graphics.Color
import com.example.aredlapp.models.LevelResponse
import com.example.aredlapp.models.PackResponse

object PackUtils {

    fun resolveTierColor(raw: String?): Int {
        if (raw.isNullOrBlank()) return Color.GRAY

        val value = raw.trim()
        parseHex(value)?.let { return it }
        parseRgba(value)?.let { return it }

        val hexMatch = Regex("#[0-9a-fA-F]{6,8}").find(value)?.value
        parseHex(hexMatch)?.let { return it }

        val rgbaMatch = Regex("""rgba?\([^)]+\)""").find(value)?.value
        parseRgba(rgbaMatch)?.let { return it }

        return Color.GRAY
    }

    fun completedGdIds(levels: List<LevelResponse>, completedLevels: Set<String>): Set<Int> {
        return levels.asSequence()
            .filter { completedLevels.contains(it.id) }
            .mapNotNull { it.level_id }
            .toSet()
    }

    fun completedCount(pack: PackResponse, completedLevels: Set<String>, completedGdIds: Set<Int>): Int {
        return pack.levels.count { level ->
            level.completed_by_user == true ||
                completedLevels.contains(level.id) ||
                (level.level_id != null && completedGdIds.contains(level.level_id))
        }
    }

    fun completionPercent(pack: PackResponse, completedLevels: Set<String>, completedGdIds: Set<Int>): Int {
        if (pack.levels.isEmpty()) return 0
        return (completedCount(pack, completedLevels, completedGdIds) * 100f / pack.levels.size).toInt()
    }

    fun isPackCompleted(pack: PackResponse, completedLevels: Set<String>, completedGdIds: Set<Int>): Boolean {
        return pack.levels.isNotEmpty() && completedCount(pack, completedLevels, completedGdIds) >= pack.levels.size
    }

    fun packRank(pack: PackResponse): Int {
        return pack.levels.map { it.position }.filter { it > 0 }.minOrNull() ?: Int.MAX_VALUE
    }

    private fun parseHex(value: String?): Int? {
        if (value.isNullOrBlank() || !value.startsWith("#")) return null
        return try { Color.parseColor(value) } catch (_: Exception) { null }
    }

    private fun parseRgba(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val match = Regex("""rgba?\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)(?:\s*,\s*([\d.]+))?\s*\)""").find(value) ?: return null
        val r = match.groupValues[1].toFloatOrNull()?.toInt() ?: return null
        val g = match.groupValues[2].toFloatOrNull()?.toInt() ?: return null
        val b = match.groupValues[3].toFloatOrNull()?.toInt() ?: return null
        val aFloat = match.groupValues.getOrNull(4)?.takeIf { it.isNotBlank() }?.toFloatOrNull() ?: 1f
        val a = (aFloat.coerceIn(0f, 1f) * 255).toInt()
        return Color.argb(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }
}
