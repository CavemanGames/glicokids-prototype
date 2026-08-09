package com.glicokids.prototype.domain.model

/**
 * Module 6 — how an out-of-range glucose reading from the sensor is handled.
 * Hypo and hyper are configured independently — never a single switch for both.
 */
enum class AlertMode { AUTO, SUGGEST, OFF }
