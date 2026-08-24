import org.gradle.api.tasks.compile.JavaCompile

// Prioritize version-local sources by excluding the conflicting main `ChatScreenMixin.java` from the client source set
project.afterEvaluate {
    try {
        // Force the 'client' source set to only use the version-local source directory
        sourceSets.named("client") {
            java {
                setSrcDirs(listOf(file("src/client/java")))
            }
        }
    } catch (e: Exception) {
        // fallback: also try to exclude at task level
        tasks.withType(JavaCompile::class.java).configureEach {
            doFirst {
                val conflict = rootProject.file("src/client/java/com/github/unstoppalezzz/reden/mixin/client/chat/ChatScreenMixin.java")
                if (conflict.exists()) {
                    source = source.filter { f -> f.absoluteFile != conflict.absoluteFile }
                }
            }
        }
    }
}

// If the root `src` contains the original ChatScreenMixin, temporarily move it
// out of the way while building 26.1.2 so the version-local mixin is used.
tasks.withType(JavaCompile::class.java).configureEach {
    val rootMixin = rootProject.file("src/client/java/com/github/unstoppalezzz/reden/mixin/client/chat/ChatScreenMixin.java")
    val moved = rootProject.file("src/client/java/com/github/unstoppalezzz/reden/mixin/client/chat/ChatScreenMixin.java.bak26")
    doFirst {
        if (rootMixin.exists() && !moved.exists()) {
            rootMixin.renameTo(moved)
        }
    }
    doLast {
        if (moved.exists() && !rootMixin.exists()) {
            moved.renameTo(rootMixin)
        }
    }
}
