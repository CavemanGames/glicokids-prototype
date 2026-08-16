package com.glicokids.prototype.domain.repository

/**
 * Module 6 — requirement 1: sends a plain SMS through the device's native SMS stack.
 * Kotlin-only contract so the domain layer never depends on `android.telephony`.
 *
 * A real send is asynchronous — the radio only reports whether
 * the carrier actually accepted the message some time after the call returns. `suspend`
 * lets an implementation wait for that real confirmation (or a timeout, treated as failure)
 * instead of reporting "no exception" as "delivered". Every call site already runs inside a
 * coroutine (`GlucoseAlertViewModel.send` hops to `Dispatchers.IO`), so this fits directly.
 */
interface SmsGateway {
    suspend fun sendTextMessage(phone: String, message: String): Boolean
}
