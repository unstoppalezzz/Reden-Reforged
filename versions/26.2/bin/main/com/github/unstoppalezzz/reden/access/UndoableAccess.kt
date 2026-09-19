package com.github.unstoppalezzz.reden.access

interface UndoableAccess {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @get:JvmName("getUndoId\$reden")
    @set:JvmName("setUndoId\$reden")
    var undoId: Long
}
