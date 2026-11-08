plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.19.4"
}

group = "me.darkz70.voidsmpsculk"
version = "0.1.1"

base {
    archivesName.set("VoidSculk-Fabric")
}

java {
    toolchain {
        // Minecraft 26.2 / Fabric Loom 1.19.x требуют JDK 25 — целимся туда же, что и Paper-плагин
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }
}

dependencies {
    // Minecraft 26.2 распространяется уже без обфускации — отдельные маппинги (Yarn/Mojang)
    // больше не нужны, Loom работает напрямую.
    minecraft("com.mojang:minecraft:26.2")
    // С 26.1+ Minecraft распространяется без обфускации — "modImplementation" в Loom
    // больше не нужен (и не существует), используем обычный "implementation".
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mutableMapOf("version" to project.version.toString()))
    }
}
