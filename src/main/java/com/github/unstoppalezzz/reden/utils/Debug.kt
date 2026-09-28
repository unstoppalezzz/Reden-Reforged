package com.github.unstoppalezzz.reden.utils

import com.github.unstoppalezzz.reden.Reden
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.appender.RollingRandomAccessFileAppender
import org.apache.logging.log4j.core.appender.rolling.OnStartupTriggeringPolicy
import org.apache.logging.log4j.core.layout.PatternLayout
import java.time.Instant

// Uses its own logger so debug output works even where Reden.LOGGER is a no-op.
private val DEBUG_LOGGER = LogManager.getLogger("${Reden.MOD_NAME}/Debug")

@Volatile
var isDebug: Boolean = false
    private set

@JvmField
var debugLogger: (String) -> Unit = { if (isDebug) DEBUG_LOGGER.info(it) }

val isDevVersion: Boolean = listOf("dev", "alpha", "beta").any { Reden.MOD_VERSION.contains(it) }

private val debugAppender by lazy {
    RollingRandomAccessFileAppender.Builder()
        .withFileName("logs/reden-debug.log")
        .setLayout(
            PatternLayout.newBuilder()
                .withPattern("[%d{HH:mm:ss}] [%t/%level] (%logger{1}) %msg{nolookups}%n")
                .build()
        )
        .withPolicy(OnStartupTriggeringPolicy.createPolicy(1))
        .withFilePattern("logs/reden-debug-%i.log.gz")
        .setName("RedenDebugAppender")
        .setImmediateFlush(true)
        .build()
        .apply { start() }
}

fun setDebug(enabled: Boolean) {
    if (enabled == isDebug) return
    isDebug = enabled
    if (enabled) startDebugAppender() else stopDebugAppender()
}

fun startDebugAppender() {
    try {
        (DEBUG_LOGGER as? Logger)?.addAppender(debugAppender)
    } catch (t: Throwable) {
        DEBUG_LOGGER.warn("Failed to start Reden debug log file", t)
    }
    DEBUG_LOGGER.info("Debug logging enabled")
}

fun stopDebugAppender() {
    DEBUG_LOGGER.info("Debug logging disabled")
    try {
        (DEBUG_LOGGER as? Logger)?.removeAppender(debugAppender)
    } catch (t: Throwable) {
        DEBUG_LOGGER.warn("Failed to stop Reden debug log file", t)
    }
}

fun pauseHere(exception: Throwable? = null) {
    val now = Instant.now()
    if (exception == null) {
        DEBUG_LOGGER.error("Paused.")
    } else {
        DEBUG_LOGGER.error("Paused because ", exception)
    }
    if (Instant.now().toEpochMilli() - now.toEpochMilli() < 300) {
        DEBUG_LOGGER.error("Did u forget to place a breakpoint here?")
        Thread.sleep(3000)
    }
}
