package com.github.unstoppalezzz.reden.network

fun registerChannelServer() {
    registerHello()
    // Only register Undo packet on the physical server side to avoid duplicate registration
    try {
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
        // Fallback: attempt to register but avoid crashing if reflection fails
        try {
            Undo.register()
        } catch (_: Throwable) {
        }
    }
}
