plugins {
    java
}

group = "me.darkz70"
version = "0.1.0"

java {
    toolchain {
        // Paper 26.2 собран на Java 25 — целимся туда же
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2-R0.1-SNAPSHOT")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    // Подставляем версию проекта в plugin.yml
    filesMatching("plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    archiveFileName.set("${rootProject.name}-${project.version}.jar")
}
