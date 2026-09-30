import net.fabricmc.loom.api.LoomGradleExtensionAPI
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `maven-publish`
    kotlin("jvm")
    kotlin("plugin.serialization")
    //id("dev.kikugie.j52j")
    id("me.modmuss50.mod-publish-plugin")
}

val mcVersion = stonecutter.current.version

val obfuscated = stonecutter.eval(mcVersion, "<26.1")
apply(plugin = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
val loom = extensions.getByType<LoomGradleExtensionAPI>()

class ModData {
    val id = property("mod.id").toString()
    val name = property("mod.name").toString()
    val version = property("mod.version").toString()
    val group = property("mod.group").toString()
}

val mod = ModData()
val mcDep = property("mod.mc_dep").toString()

version = "${mod.version}+$mcVersion"
group = mod.group
base { archivesName.set(mod.id) }

loom.splitEnvironmentSourceSets()
loom.mods.create("template") {
    sourceSet(sourceSets["main"])
    sourceSet(sourceSets["client"])
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
    val modImpl = if (obfuscated) "modImplementation" else "implementation"

    "minecraft"("com.mojang:minecraft:$mcVersion")
    if (obfuscated) {
        "mappings"(loom.officialMojangMappings())
    }

    if (stonecutter.eval(mcVersion, "=1.21.1")) {
        modImpl("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}") {
            exclude(group = "net.fabricmc.fabric-api")
        }
    }

    modImpl("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImpl("net.fabricmc:fabric-language-kotlin:${property("deps.fabric_language_kotlin")}")
    modImpl("io.wispforest:owo-lib:${property("deps.owo")}") {
        exclude(group = "net.fabricmc.fabric-api")
        exclude(group = "it.unimi.dsi")
    }
    modImpl("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    modImpl("maven.modrinth:malilib:${property("deps.malilib")}")
    modImpl("maven.modrinth:litematica:${property("deps.litematica")}")
}

loom.decompilers {
    getByName("vineflower").options.put("mark-corresponding-synthetics", "1")
}

loom.runConfigs.all {
    ideConfigGenerated(true)
    vmArgs("-Dmixin.debug.export=true")
    runDir = "../../run"
}

loom.accessWidenerPath.set(project.file("src/main/resources/reden.accesswidener"))

if (stonecutter.eval(mcVersion, ">=1.21.9 <26.1")) {
    val chatMenuFiles = setOf("QuickMenuWidget", "ChatMixinHelper", "ChatHudMixin", "ChatScreenMixin")
    val isChatMenuFile = { f: java.io.File -> f.nameWithoutExtension in chatMenuFiles }
    tasks.named<JavaCompile>("compileClientJava") {
        exclude { !it.isDirectory && isChatMenuFile(it.file) }
    }
    tasks.named<KotlinCompile>("compileClientKotlin") {
        exclude { !it.isDirectory && isChatMenuFile(it.file) }
    }
}

val java = if (stonecutter.eval(mcVersion, ">=26.1")) 25 else if (stonecutter.eval(mcVersion, ">=1.20.6")) 21 else 17
java {
    withSourcesJar()
    targetCompatibility = JavaVersion.toVersion(java)
    sourceCompatibility = JavaVersion.toVersion(java)
}
kotlin.jvmToolchain(java)

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(java.toString()))
        if (obfuscated) {
            apiVersion.set(KotlinVersion.KOTLIN_2_1)
            jvmDefault.set(JvmDefaultMode.DISABLE)
        }
    }
}
if (obfuscated) {
    tasks.compileKotlin { compilerOptions.moduleName.set("reden") }
    tasks.named<KotlinCompile>("compileClientKotlin") { compilerOptions.moduleName.set("reden_client") }
}

tasks.processResources {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    inputs.property("id", mod.id)
    inputs.property("name", mod.name)
    inputs.property("version", mod.version)
    inputs.property("mcdep", mcDep)
    inputs.property("mcVersion", mcVersion)
    inputs.property("malilib", project.property("deps.malilib").toString())

    val map = mapOf(
        "id" to mod.id,
        "name" to mod.name,
        "version" to mod.version,
        "mcdep" to mcDep,
        "malilib" to project.property("deps.malilib") as String,
        "cliententry" to "com.github.unstoppalezzz.reden." +
                "RedenClient",
        "clientmixins" to if (obfuscated) "" else """,
    {
      "config": "reden.client.mixins.json",
      "environment": "client"
    }""",
    )

    filesMatching("fabric.mod.json") { expand(map) }

    if (obfuscated) {
        filesMatching("fabric.mod.json") {
            filter { line -> if (line.contains("\"homepage\"")) null else line }
        }
    }
}

tasks.withType<Jar>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

val releaseJar = tasks.named<AbstractArchiveTask>(if (obfuscated) "remapJar" else "jar")

tasks.register<Copy>("buildAndCollect") {
    group = "build"
    from(releaseJar.flatMap { it.archiveFile })
    into(rootProject.layout.buildDirectory.file("libs/${mod.version}"))
    dependsOn("build")
}

if (stonecutter.current.project == stonecutter.versions.last().project) {
    val multiversionId = "${mod.id}-multiversion"
    val multiversionTargets = stonecutter.versions.map { it.project to it.version }

    val generateMultiversionModJson by tasks.registering {
        group = "build"
        val out = rootProject.layout.buildDirectory.file("multiversion/fabric.mod.json")
        inputs.property("targets", multiversionTargets.map { it.first })
        inputs.property("version", mod.version)
        outputs.file(out)
        doLast {
            val jars = multiversionTargets.joinToString(",\n") { (_, v) ->
                """    { "file": "META-INF/jars/${mod.id}-${mod.version}+$v.jar" }"""
            }
            out.get().asFile.apply { parentFile.mkdirs() }.writeText(
                """
                |{
                |  "schemaVersion": 1,
                |  "id": "$multiversionId",
                |  "version": "${mod.version}",
                |  "name": "Reden Reforged (all versions)",
                |  "description": "Container that loads the Reden Reforged build matching the running Minecraft version.",
                |  "license": "LGPL-v3.0-only",
                |  "environment": "*",
                |  "jars": [
                |$jars
                |  ],
                |  "depends": {
                |    "fabricloader": ">=0.15",
                |    "${mod.id}": "*"
                |  },
                |  "custom": {
                |    "modmenu": { "badges": ["library"], "parent": "${mod.id}" }
                |  }
                |}
                |""".trimMargin()
            )
        }
    }

    val multiversionJar = tasks.register<Jar>("multiversionJar") {
        group = "build"
        description = "Builds every version and bundles them into one jar that works on all supported Minecraft versions."
        archiveBaseName.set(mod.id)
        archiveVersion.set("${mod.version}")
        archiveClassifier.set("")
        destinationDirectory.set(rootProject.layout.buildDirectory.dir("libs"))
        from(generateMultiversionModJson)
        multiversionTargets.forEach { (targetProject, targetVersion) ->
            val targetObfuscated = stonecutter.eval(targetVersion, "<26.1")
            dependsOn(":$targetProject:" + if (targetObfuscated) "remapJar" else "jar")
            from(rootProject.file("versions/$targetProject/build/libs/${mod.id}-${mod.version}+$targetVersion.jar")) {
                into("META-INF/jars")
            }
        }
    }

    // `./gradlew build` also produces the multiversion jar.
    tasks.named("build") { dependsOn(multiversionJar) }
}

if (!obfuscated) {
    tasks.register<com.github.unstoppalezzz.reden.build.MapMojangToIntermediaryTask>("mapMojangToIntermediary") {
        inputFile.set(rootProject.file("src/methods.txt"))
        outputFile.set(project.file("build/mapped-methods.txt"))
        minecraftVersion.set(stonecutter.current.version)

        outputs.upToDateWhen {
            false
        }
    }
}

publishMods {
    file = releaseJar.flatMap { it.archiveFile }
    displayName = "${mod.name} ${mod.version} for $mcVersion"
    version = "${mod.version}+$mcVersion"
    changelog = rootProject.file("CHANGELOG.md").readText()
    type = STABLE
    modLoaders.add("fabric")

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
