package com.github.unstoppalezzz.reden.access

import net.minecraft.core.BlockPos

@Suppress("INAPPLICABLE_JVM_NAME")
interface ChunkSectionInterface {
    @JvmName("getModifyTime\$reden")
    fun getModifyTime(pos: BlockPos): Int

    @JvmName("setModifyTime\$reden")
    fun setModifyTime(pos: BlockPos, time: Int)
}
