package com.glicokids.prototype.domain.repository

/**
 * Module 6 — requirement 1: sends a plain SMS through the device's native SMS stack.
 * Kotlin-only contract so the domain layer never depends on `android.telephony`.
 */
interface SmsGateway {
    fun sendTextMessage(phone: String, message: String): Boolean
}
