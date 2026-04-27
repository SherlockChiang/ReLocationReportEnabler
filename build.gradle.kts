import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.agp.app) apply false
}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    project.exec {
        workingDir = currentWorkingDir
        commandLine = split("\\s".toRegex())
        standardOutput = byteOut
    }
    return String(byteOut.toByteArray()).trim()
}

val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

val verName by extra("v1")
val verCode by extra(gitCommitCount)
val commitHash by extra(gitCommitHash)

tasks.register("Delete", Delete::class) {
    delete(rootProject.buildDir)
}
