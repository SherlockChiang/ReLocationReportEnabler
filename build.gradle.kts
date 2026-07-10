plugins {
    alias(libs.plugins.agp.app) apply false
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "HEAD", "--count")
}.standardOutput.asText.get().trim().toInt()
val tagVersion = providers.environmentVariable("GITHUB_REF_NAME").orNull
    ?.takeIf {
        providers.environmentVariable("GITHUB_REF_TYPE").orNull == "tag"
                && it.matches(Regex("v\\d+(\\.\\d+)*"))
    }

val verName by extra(tagVersion ?: "v1")
val verCode by extra(gitCommitCount)

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
