package com.github.unstoppalezzz.reden.utils

import net.minecraft.nbt.CompoundTag

interface DataHolder {
    fun load(): CompoundTag
    fun set(nbt: CompoundTag)
}
