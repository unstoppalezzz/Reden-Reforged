plugins {
    `maven-publish`
    id("fabric-loom")
    kotlin("jvm")
    kotlin("plugin.serialization")
    //id("dev.kikugie.j52j")
    id("me.modmuss50.mod-publish-plugin")
}

class ModData {
    val id = property("mod.id").toString()
    val name = property("mod.name").toString()
    val version = property("mod.version").toString()
    val group = property("mod.group").toString()
}

val mod = ModData()
val mcVersion = stonecutter.current.version
val mcDep = property("mod.mc_dep").toString()

version = "${mod.version}+$mcVersion"
group = mod.group
base { archivesName.set(mod.id) }

loom {
    splitEnvironmentSourceSets()

    mods {
        create("template") {
            sourceSet(sourceSets["main"])
            sourceSet(sourceSets["client"])
        }
    }
}

tasks.compileKotlin {
    outputs.upToDateWhen { false }
}

repositories {
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    maven {
        name = "Masa Maven"
        url = uri("https://masa.dy.fi/maven")
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    maven {
        url = uri("https://maven.wispforest.io")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    fun fapi(vararg modules: String) = modules.forEach {
        modImplementation(fabricApi.module(it, property("deps.fabric_api") as String))
    }

    minecraft("com.mojang:minecraft:$mcVersion")
    mappings(loom.officialMojangMappings())

    if (stonecutter.eval(mcVersion, "=1.21.1")) {
        modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}") {
            exclude(group = "net.fabricmc.fabric-api")
        }
    }

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_language_kotlin")}")
    modImplementation("io.wispforest:owo-lib:${property("deps.owo")}") {
        exclude(group = "net.fabricmc.fabric-api")
        exclude(group = "it.unimi.dsi")
    }
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    modImplementation("maven.modrinth:malilib:${property("deps.malilib")}")
    if (stonecutter.eval(mcVersion, "<=1.21.8")) {
        modImplementation("maven.modrinth:litematica:${property("deps.litematica")}")
    }

    // Add Fabric mapping-io for name mapping functionality
    // implementation("net.fabricmc:mapping-io:0.5.0")

    // Also add tiny-mappings-parser for better compatibility
    // implementation("net.fabricmc:tiny-mappings-parser:0.3.0+build.17")

//    fapi(
//        // Add modules from https://github.com/FabricMC/fabric
//        "fabric-lifecycle-events-v1",
//        "fabric-networking-api-v1",
//        "fabric-resource-loader-v0",
//        "fabric-registry-sync-v0",
//        "fabric-content-registries-v0",
//        "fabric-loot-api-v2",
//        "fabric-command-api-v2",
//        "fabric-screen-api-v1",
//        "fabric-screen-handler-api-v1",
//    )
}

loom {
    decompilers {
        get("vineflower").apply { // Adds names to lambdas - useful for mixins
            options.put("mark-corresponding-synthetics", "1")
        }
    }

    runConfigs.all {
        ideConfigGenerated(true)
        vmArgs("-Dmixin.debug.export=true")
        runDir = "../../run"
    }

    accessWidenerPath.set(project.file("src/main/resources/reden.accesswidener"))
}

if (stonecutter.eval(mcVersion, ">=1.21.9")) {
    val keep = { f: java.io.File -> f.name.startsWith("RedenClientMinimal") || f.path.contains("multiver") }
    tasks.named<org.gradle.api.tasks.compile.JavaCompile>("compileClientJava") {
        exclude { !it.isDirectory && !keep(it.file) }
    }
    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileClientKotlin") {
        exclude { !it.isDirectory && !keep(it.file) }
    }
}

val java = if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17
java {
    withSourcesJar()
    targetCompatibility = JavaVersion.toVersion(java)
    sourceCompatibility = JavaVersion.toVersion(java)
}
kotlin.jvmToolchain(java)

tasks.processResources {
    inputs.property("id", mod.id)
    inputs.property("name", mod.name)
    inputs.property("version", mod.version)
    inputs.property("mcdep", mcDep)
    inputs.property("mcVersion", mcVersion)

    val map = mapOf(
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "mcdep" to mcDep,
        "cliententry" to "com.github.unstoppalezzz.reden." +
                if (stonecutter.eval(mcVersion, ">=1.21.9")) "RedenClientMinimal" else "RedenClient",
        "malilib" to project.property("deps.malilib") as String
    )

    filesMatching("fabric.mod.json") { expand(map) }

    // For MC >= 1.21.9 we disabled the client source set; remove client entrypoint and client mixin config
    filesMatching("fabric.mod.json") {
        if (stonecutter.eval(mcVersion, ">=1.21.9")) {
            filter { text ->
                text
                    .replace(Regex("""\s*"client"\s*:\s*\[\s*"com.github.unstoppalezzz.reden.RedenClient"\s*]\s*,?"""), "")
                    .replace(Regex("""\s*\{\s*"config"\s*:\s*"reden.client.mixins.json"\s*,\s*"environment"\s*:\s*"client"\s*\}\s*,?"""), "")
            }
        }
    }
}

tasks.compileKotlin {
//    outputs.upToDateWhen { false }
}

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(tasks.remapJar.get().archiveFile)
    into(rootProject.layout.buildDirectory.file("libs/${mod.version}"))
    dependsOn("build")
}
publishMods {
    file = tasks.remapJar.get().archiveFile
    displayName = "${mod.name} ${mod.version} for $mcVersion"
    version = "${mod.version}+$mcVersion"
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

//    dryRun = providers.environmentVariable("MODRINTH_TOKEN")
//        .getOrNull() == null || providers.environmentVariable("CURSEFORGE_TOKEN").getOrNull() == null

    modrinth {
        projectId = property("publish.modrinth").toString()
        accessToken = providers.environmentVariable("MODRINTH_TOKEN")
        minecraftVersions.addAll(
            property("mod.mc_targets").toString().split(" ")
                .filter { it.isNotBlank() }
                .plus(mcVersion)
                .distinct()
        )
        requires("fabric-api", "fabric-language-kotlin", "malilib")
    }

    curseforge {
        projectId = property("publish.curseforge").toString()
        accessToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        minecraftVersions.addAll(
            property("mod.mc_targets").toString().split(" ")
                .filter { it.isNotBlank() }
                .plus(mcVersion)
                .distinct()
        )
        requires("fabric-api", "fabric-language-kotlin", "malilib")
    }
}
