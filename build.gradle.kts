plugins {
    java
}

group = "me.darkz70"
version = "1.0.0"

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
    // Paper 26.2 публикует API как "26.2.build.<N>-stable"
    compileOnly("io.papermc.paper:paper-api:26.2.build.129-stable")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    // Подставляем версию проекта в paper-plugin.yml
    filesMatching("paper-plugin.yml") {
        expand("version" to project.version.toString())
    }
}

tasks.jar {
    archiveFileName.set("VoidSMP-${project.version}.jar")
}
