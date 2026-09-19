package com.github.unstoppalezzz.reden.access

@Suppress("INAPPLICABLE_JVM_NAME")
interface BlockEntityInterface {
    @get:JvmName("getLastSavedNbt\$reden")
    val lastSavedNbt: Any?

    @JvmName("saveLastNbt\$reden")
    fun saveLastNbt()
}
