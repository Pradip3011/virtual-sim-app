package com.omnitest.virtual_sim.utils

object OtpParser {
    // Looks strictly for standalone 4 to 6 digit numeric sequences (\b prevents matching phone numbers)
    private val otpRegex = Regex("\\b\\d{4,6}\\b")

    fun extractOtp(messageBody: String): String? {
        if (messageBody.isBlank()) return null
        return otpRegex.find(messageBody)?.value
    }
}