package com.omnitest.virtual_sim.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamRegistration(
    val action: String,
    val clientId: String
)
