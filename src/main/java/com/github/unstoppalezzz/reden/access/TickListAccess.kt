package com.github.unstoppalezzz.reden.access

import net.minecraft.world.level.levelgen.structure.BoundingBox

interface TickListAccess {
    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("clearArea\$reden")
    fun clearArea(box: BoundingBox)
}
