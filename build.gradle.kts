val keycloakVersion: String by project
val kotlinVersion: String by project
val artifactVersion: String by project

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.jpa") version "2.1.0"
    kotlin("plugin.allopen") version "2.1.0"
    id("com.gradleup.shadow") version "9.0.0-beta12"
}

group = "com.weare5stones"
// Version is read from gradle.properties. Source of truth lives in package.json
// and is synced into gradle.properties by `npm version` (see bin/release.js).
// Can be overridden ad-hoc with -PartifactVersion=...
version = artifactVersion

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

allOpen {
    annotation("jakarta.persistence.Entity")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("org.keycloak:keycloak-core:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
    compileOnly("org.keycloak:keycloak-model-jpa:$keycloakVersion")
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // GroupRoleService references Keycloak/JPA types at the class level (in method
    // signatures), so the test JVM needs them on the runtime classpath even when the
    // tests only exercise the pure functions.
    testImplementation("org.keycloak:keycloak-core:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-server-spi:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-server-spi-private:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-services:$keycloakVersion")
    testImplementation("org.keycloak:keycloak-model-jpa:$keycloakVersion")
    testImplementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    testImplementation("jakarta.ws.rs:jakarta.ws.rs-api:3.1.0")
    // For data-layer tests that exercise GroupMemberRoleEntity queries against a
    // real (in-memory) DB to verify cross-(realm, group, user) isolation.
    testImplementation("org.hibernate.orm:hibernate-core:6.6.4.Final")
    testRuntimeOnly("com.h2database:h2:2.3.232")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
    // Always print a summary line, even when tests are up-to-date the next run will
    // re-emit per-test PASSED lines because we wire to the events above.
    afterSuite(KotlinClosure2({ desc: org.gradle.api.tasks.testing.TestDescriptor, result: org.gradle.api.tasks.testing.TestResult ->
        if (desc.parent == null) {
            val total = result.testCount
            val passed = result.successfulTestCount
            val failed = result.failedTestCount
            val skipped = result.skippedTestCount
            println("\n[test] $total tests — $passed passed, $failed failed, $skipped skipped")
        }
    }))
}

// ---------------------------------------------------------------------------
// Admin UI bundling — packages the admin SPA from `admin/` into the plugin
// JAR under `admin-ui/`, so it can be served at /realms/{realm}/group-mgmt/admin/.
//
// Opt-in: not part of the default `build`. Run `./gradlew bundleAdminUi build`
// (or `./gradlew bundleAdminUi shadowJar`) to produce a JAR with the UI included.
// Backend-only builds skip Node.js entirely.
// ---------------------------------------------------------------------------
val adminUiDir = file("admin")
val adminUiOutputDir = layout.buildDirectory.dir("admin-ui")

val installAdminUi = tasks.register<Exec>("installAdminUi") {
    group = "admin-ui"
    description = "Run npm install in admin/"
    workingDir = adminUiDir
    commandLine = listOf("npm", "install", "--silent")
    inputs.file("admin/package.json")
    outputs.dir("admin/node_modules")
    onlyIf { !file("admin/node_modules").exists() }
}

val buildAdminUi = tasks.register<Exec>("buildAdminUi") {
    group = "admin-ui"
    description = "Build the admin SPA via Vite"
    dependsOn(installAdminUi)
    workingDir = adminUiDir
    commandLine = listOf("npm", "run", "build")
    inputs.dir("admin/src")
    inputs.file("admin/index.html")
    inputs.file("admin/package.json")
    inputs.file("admin/vite.config.ts")
    inputs.file("admin/tsconfig.json")
    outputs.dir("admin/dist")
}

val bundleAdminUi = tasks.register<Copy>("bundleAdminUi") {
    group = "admin-ui"
    description = "Build the admin SPA and stage it for inclusion in the plugin JAR"
    dependsOn(buildAdminUi)
    from(layout.projectDirectory.dir("admin/dist"))
    into(adminUiOutputDir)
}

tasks.processResources {
    // Pick up staged SPA assets if bundleAdminUi has been run. Missing dir is OK
    // — backend-only builds simply produce a JAR without admin-ui/, and the
    // UiResource handler returns a friendly 404 with build instructions.
    from(adminUiOutputDir) {
        into("admin-ui")
    }
    // mustRunAfter (not dependsOn) so backend-only `./gradlew build` doesn't
    // trigger npm. When the operator opts in via `./gradlew bundleAdminUi build`,
    // this ensures bundleAdminUi runs before processResources picks up the staged dir.
    mustRunAfter(bundleAdminUi)
}

tasks.shadowJar {
    archiveBaseName.set("keycloak-group-management")
    archiveClassifier.set("")
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
