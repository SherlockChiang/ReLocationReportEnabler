plugins {
    alias(libs.plugins.agp.app) apply false
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "HEAD", "--count")
}.standardOutput.asText.get().trim().toInt()

val verName by extra("v1")
val verCode by extra(gitCommitCount)

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
