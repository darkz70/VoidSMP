plugins { java }

group = "ru.voidsmp"
version = "1.0.6"

repositories {
    mavenCentral()
    maven("https://repo.codemc.io/repository/maven-releases/") {
        content { includeGroup("com.github.retrooper") }
    }
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.+")
    compileOnly("com.github.retrooper:packetevents-api:2.13.0")
    testImplementation("io.papermc.paper:paper-api:26.2.+")
    testImplementation("com.github.retrooper:packetevents-api:2.13.0")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
tasks.test { useJUnitPlatform() }
tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}
