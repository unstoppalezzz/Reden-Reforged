package com.github.unstoppalezzz.reden.utils.multiver

//? if <26.1 {
/*import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping

fun createMiscKeyMapping(name: String, key: Int) =
    //? if >=1.21.9 {
    KeyMapping(name, InputConstants.Type.KEYSYM, key, KeyMapping.Category.MISC)
    //?} else {
    /^KeyMapping(name, InputConstants.Type.KEYSYM, key, "key.categories.misc")
    ^///?}
*///?}
