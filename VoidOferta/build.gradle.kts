plugins {
    java
}

group = "com.voidsmp"
version = "1.0.0"

// Версия Paper API. Для Paper 26.2 значение по умолчанию ниже.
// Если Maven-репозиторий Paper отдаёт другую нумерацию, переопределите:
//   gradle build -PpaperVersion=<версия>
val paperVersion = providers.gradleProperty("paperVersion").orElse("26.2-R0.1-SNAPSHOT").get()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVersion")
}

java {
    // Paper 26.2 запускается на Java 21+; байткод Java 21 работает и на более новых JVM
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
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
