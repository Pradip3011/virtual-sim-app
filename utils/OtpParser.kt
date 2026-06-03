package com.omnitest.virtual_sim.utils

object OtpParser {
    // Regex looking strictly for boundary-isolated 4 to 6 digit numeric profiles
    private val otpRegex = Regex("\\b\\d{4,6}\\b")

    fun extractOtp(messageBody: String): String? {
        return otpRegex.find(messageBody)?.value
    }
}
