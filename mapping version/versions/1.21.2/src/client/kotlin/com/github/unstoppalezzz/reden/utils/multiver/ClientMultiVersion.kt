package com.github.unstoppalezzz.reden.utils.multiver

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping

fun createMiscKeyMapping(name: String, key: Int) =
    KeyMapping(name, InputConstants.Type.KEYSYM, key, "key.categories.misc")
