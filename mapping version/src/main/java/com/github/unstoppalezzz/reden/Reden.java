package com.github.unstoppalezzz.reden;

import com.github.unstoppalezzz.reden.utils.UtilsKt;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.unstoppalezzz.reden.network.ChannelsKt;

public class Reden implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("template");
    public static final String MOD_VERSION = /*$ mod_version*/ "0.10.4";
    public static final String MOD_ID = "reden";
    public static final String MOD_NAME = "Reden";

    @Override
    public void onInitialize() {
        ChannelsKt.registerChannelServer();
        ServerLifecycleEvents.SERVER_STARTED.register(UtilsKt::setServer);
    }

    private ClassLoader hijackClassLoader() {
        ClassLoader classLoader = Reden.class.getClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException("Reden's class loader is null");
        }
        return new ClassLoader(classLoader) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("com.github.unstoppalezzz.reden")) {
                    try {
                        getClassLoadingLock("1").wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return super.loadClass(name);
                }
                return classLoader.loadClass(name);
            }
        };
    }

    public static Identifier identifier(String path) {
        Identifier id = Identifier.tryParse(MOD_ID + ":" + path);
        if (id == null) throw new IllegalArgumentException("Invalid identifier: " + path);
        return id;
    }
}
