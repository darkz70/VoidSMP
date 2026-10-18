plugins {
    java
}

group = "com.voidsmp"
version = "1.0.0"

// Версия Paper API. У Paper для Minecraft 26.x нумерация вида
// 26.2.build.<номер сборки>-<канал>, поэтому по умолчанию берётся
// последняя сборка 26.2. Точную версию можно задать явно:
//   gradle build -PpaperVersion=26.2.build.42-stable
val paperVersion = providers.gradleProperty("paperVersion").orElse("26.2.build.+").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    // Paper 26.2 сам собран на Java 25 (class file 69), поэтому и плагин
    // компилируем JDK 25 — более старая javac просто не прочитает paper-api.
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    // Подставляем версию из Gradle в plugin.yml
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName.set("VoidOferta")
}
