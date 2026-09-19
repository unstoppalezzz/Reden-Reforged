@file:Suppress("DEPRECATION")

package com.github.unstoppalezzz.reden.utils

import com.github.unstoppalezzz.reden.Reden

const val CAPTURE_LAG_DIAGNOSTICS_ENABLED = true

@JvmField
var debugLogger: (String) -> Unit = {
    if (CAPTURE_LAG_DIAGNOSTICS_ENABLED) Reden.LOGGER.info("[Reden debug] $it")
}

val isDebug: Boolean get() = CAPTURE_LAG_DIAGNOSTICS_ENABLED

val isDevVersion: Boolean = listOf("dev", "alpha", "beta").any { Reden.MOD_VERSION.contains(it) }

fun startDebugAppender() {
    // Debug logging is  disabled.
}

fun stopDebugAppender() {
    // Debug logging is  disabled 
}
