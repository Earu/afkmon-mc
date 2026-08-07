plugins {
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

val commonProject = project(":common")

dependencies {
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.flk)
}

loom {
    mixin {
        // Refmap-free mixin remapping: tiny-remapper rewrites targets at remapJar time.
        useLegacyMixinAp = false
    }
    runConfigs.named("client") {
        findProperty("mcUsername")?.let { programArgs("--username", it.toString()) }
        findProperty("quickJoin")?.let { programArgs("--quickPlayMultiplayer", it.toString()) }
        // Separate dir so a dev client and dev server can run at once.
        runDir = "run-client"
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
}

base {
    archivesName = "afk-fabric"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// MultiLoader pattern: common's sources compile directly into this module's jar.
sourceSets.main {
    kotlin.srcDir(commonProject.file("src/main/kotlin"))
    java.srcDir(commonProject.file("src/main/java"))
    resources.srcDir(commonProject.file("src/main/resources"))
}
