plugins {
    alias(libs.plugins.loom)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.minotaur)
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
}

base {
    archivesName = "afk-fabric"
}

// Modrinth publishing through Modrinth's own plugin; CurseForge stays on mc-publish in the workflow.
// `./gradlew modrinth` uploads, MODRINTH_DEBUG=1 prints the request instead.
modrinth {
    token = providers.environmentVariable("MODRINTH_TOKEN").orElse("")
    projectId = providers.environmentVariable("MODRINTH_ID").orElse("")
    debugMode = providers.environmentVariable("MODRINTH_DEBUG").isPresent
    versionNumber = project.version.toString()
    versionName = "afkmon-mc ${project.version} (Fabric)"
    versionType = "release"
    changelog = provider { rootProject.file("CHANGELOG.md").readText() }
    gameVersions.add(libs.versions.minecraft.get())
    loaders.add("fabric")
    uploadFile.set(tasks.remapJar)
    dependencies {
        required.project("fabric-api")
        required.project("fabric-language-kotlin")
    }
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
