package com.omnitest.virtual_sim.data.models

import kotlinx.serialization.Serializable

@Serializable
data class SmsMessage(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long
)