package com.glicokids.prototype.domain.model

/**
 * Where a glucose reading came from — the child typing it in ([MANUAL]) or a
 * connected sensor pushing it ([SENSOR]). Module 6 reuses this distinction to
 * decide whether an alert is allowed to fire on its own (see `ShouldAutoAlertUseCase`).
 */
enum class ReadingSource { MANUAL, SENSOR }
