plugins {
    java
}

group = "dev.angelocordon"
// CI passes the release tag (e.g. -Pversion=0.1.0); local builds fall back to "dev".
version = (findProperty("version") as String?)?.takeIf { it != "unspecified" } ?: "dev"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

val paperApi = "io.papermc.paper:paper-api:26.3.build.223-stable"

dependencies {
    compileOnly(paperApi)

    // Unit tests cover pure logic only. The API is on the test classpath for types like Material,
    // but there is no running server, so registry-backed calls fail there (see CONTRIBUTING.md).
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Smoke-test only: a second plugin that drives a golem from the console. It has its own jar and
// never goes into the release jar.
val testSupport = sourceSets.create("testSupport")

dependencies {
    "testSupportCompileOnly"(paperApi)
    "testSupportCompileOnly"(sourceSets.main.get().output)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    // Fixed name so releases/latest/download/golden-golem.jar always points at the newest build.
    archiveFileName = "golden-golem.jar"
}

tasks.named<ProcessResources>("processTestSupportResources") {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

val testSupportJar by tasks.registering(Jar::class) {
    archiveFileName = "golden-golem-testsupport.jar"
    from(testSupport.output)
}

tasks.build {
    dependsOn(testSupportJar)
}
