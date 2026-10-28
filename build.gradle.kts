plugins { java }

group = "ru.voidsmp"
version = "1.0.0"

// API привязан к конкретному билду Paper. Динамический диапазон удобнее (сам тянет
// свежий стабильный билд), но сборка от него не воспроизводима, а Gradle при
// истёкшем кэше метаданных снова идёт в репозиторий Paper — и падает, если тот
// временно недоступен. Обновляйте версию вместе с ядром сервера.
val paperApi = "io.papermc.paper:paper-api:26.2.build.135-stable"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(paperApi)
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// SQLite JDBC (org.sqlite.JDBC) тащить в jar не нужно: Paper кладёт sqlite-jdbc
// на родительский classloader сервера, поэтому плагин работает через java.sql.*
// и не требует ни shade, ни ни одной внешней зависимости.
java { toolchain.languageVersion.set(JavaLanguageVersion.of(25)) }

tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }

tasks.test { useJUnitPlatform() }

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.jar { archiveFileName.set("${rootProject.name}-${project.version}.jar") }
