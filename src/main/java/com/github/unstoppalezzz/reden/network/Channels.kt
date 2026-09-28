package com.github.unstoppalezzz.reden.network

fun registerChannelServer() {
    registerHello()
    //? if >=26.1 {
    Undo.register()
    //?} else {
    /*try {
        val loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader")
        val getInstance = loaderClass.getMethod("getInstance")
        val instance = getInstance.invoke(null)
        val envTypeClass = Class.forName("net.fabricmc.api.EnvType")
        val getEnv = instance.javaClass.getMethod("getEnvironmentType")
        val env = getEnv.invoke(instance)
        if (env.toString() == envTypeClass.getField("SERVER").get(null).toString()) {
            Undo.register()
        }
    } catch (ignored: Throwable) {
        try {
            Undo.register()
        } catch (_: Throwable) {
        }
    }
    *///?}
}
