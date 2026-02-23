package com.example.aredlapp

import kotlinx.serialization.Serializable

@Serializable
data class Demon(
    val id: String,
    val name: String,
    val position: Int,
    val points: Int,
    val description: String? = null,
    val tags: List<String>? = null,
    val song: Int? = null,
    val edel_enjoyment: Double? = null,
    val gddl_tier: Double? = null,
    val nlw_tier: Double? = null
)
