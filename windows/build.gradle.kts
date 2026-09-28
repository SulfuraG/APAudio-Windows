import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService

plugins {
    kotlin("jvm")
    application
}

val windowsApplicationName = "APAudio"
val windowsApplicationVersion = "0.3.0"
val windowsMainClass = "com.example.apaudio.WindowsAppKt"
val windowsVendor = "SulfuraG"
val windowsUpgradeUuid = "6557da66-105d-43c0-80ae-0414ebb6f267"
val windowsRuntimeModules = "java.base,java.desktop,java.prefs"

version = windowsApplicationVersion

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.jmdns:jmdns:3.6.3")
    testImplementation(kotlin("test"))
}

application {
    mainClass.set(windowsMainClass)
}

tasks.named<Jar>("jar") {
    archiveFileName.set("windows.jar")
}

val javaToolchains = extensions.getByType<JavaToolchainService>()
val jpackageExecutable = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(17))
}.map { launcher ->
    launcher.metadata.installationPath.file("bin/jpackage.exe").asFile
}

val jpackageRoot = layout.buildDirectory.dir("jpackage")
val generatedIcon = jpackageRoot.map { it.file("resources/APAudio.ico") }
val appImageDestination = jpackageRoot.map { it.dir("app-image") }
val appImageOutput = appImageDestination.map { it.dir(windowsApplicationName) }
val installerDestination = jpackageRoot.map { it.dir("installer") }
val installerOutput = installerDestination.map {
    it.file("$windowsApplicationName-$windowsApplicationVersion.exe")
}
val installDistLib = layout.buildDirectory.dir("install/windows/lib")

val jpackageExecutablePath = jpackageExecutable.get().absolutePath
val generatedIconFile = generatedIcon.get().asFile
val appImageDestinationFile = appImageDestination.get().asFile
val appImageOutputFile = appImageOutput.get().asFile
val installerDestinationFile = installerDestination.get().asFile
val installerOutputFile = installerOutput.get().asFile
val installDistLibFile = installDistLib.get().asFile

val generateWindowsIcon by tasks.registering(JavaExec::class) {
    group = "distribution"
    description = "Generates the APAudio multi-resolution Windows ICO from WindowsAppIcon."
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.example.apaudio.WindowsIconExporterKt")
    outputs.file(generatedIcon)
    args(generatedIconFile.absolutePath)
}

fun jpackageBaseArguments(destination: File): List<String> = listOf(
    "--name", windowsApplicationName,
    "--app-version", windowsApplicationVersion,
    "--vendor", windowsVendor,
    "--description", "AirPlay 1 audio receiver for Windows",
    "--copyright", "Copyright (c) $windowsVendor",
    "--input", installDistLibFile.absolutePath,
    "--main-jar", "windows.jar",
    "--main-class", windowsMainClass,
    "--dest", destination.absolutePath,
    "--icon", generatedIconFile.absolutePath,
    "--add-modules", windowsRuntimeModules,
    "--java-options", "-Dfile.encoding=UTF-8"
)

val cleanJpackageAppImage by tasks.registering(Delete::class) {
    delete(appImageOutputFile)
}

val jpackageAppImage by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the portable APAudio Windows application image with a bundled runtime."
    dependsOn(tasks.named("installDist"), generateWindowsIcon, cleanJpackageAppImage)
    inputs.dir(installDistLib)
    inputs.file(generatedIcon)
    inputs.property("applicationVersion", windowsApplicationVersion)
    outputs.dir(appImageDestination)
    commandLine(
        listOf(jpackageExecutablePath, "--type", "app-image") +
            jpackageBaseArguments(appImageDestinationFile)
    )
}

val cleanJpackageInstaller by tasks.registering(Delete::class) {
    delete(installerOutputFile)
}

val jpackageInstaller by tasks.registering(Exec::class) {
    group = "distribution"
    description = "Builds the APAudio Windows EXE installer with Start Menu integration."
    dependsOn(jpackageAppImage, cleanJpackageInstaller)
    inputs.dir(installDistLib)
    inputs.file(generatedIcon)
    inputs.property("applicationVersion", windowsApplicationVersion)
    outputs.file(installerOutput)
    val wixHome = providers.gradleProperty("wixHome").orNull
        ?: providers.environmentVariable("WIX_HOME").orNull
        ?: layout.buildDirectory.dir("tools/wix314").get().asFile.absolutePath
    val configuredWix = File(wixHome)
    val wixBin = when {
        configuredWix.resolve("candle.exe").isFile -> configuredWix
        configuredWix.resolve("bin/candle.exe").isFile -> configuredWix.resolve("bin")
        else -> error(
            "WiX candle.exe/light.exe were not found. " +
                "Set -PwixHome=<WiX bin directory> or WIX_HOME."
        )
    }
    require(wixBin.resolve("light.exe").isFile) {
        "WiX light.exe was not found in ${wixBin.absolutePath}"
    }

    environment("PATH", "${wixBin.absolutePath};${providers.environmentVariable("PATH").orNull.orEmpty()}")
    commandLine(
        listOf(jpackageExecutablePath, "--type", "exe") +
            jpackageBaseArguments(installerDestinationFile) +
            listOf(
                "--install-dir", windowsApplicationName,
                "--win-menu",
                "--win-menu-group", windowsApplicationName,
                "--win-dir-chooser",
                "--win-upgrade-uuid", windowsUpgradeUuid
            )
    )
}
