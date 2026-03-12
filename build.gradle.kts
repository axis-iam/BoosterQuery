plugins {
    id("java-library")
    id("maven-publish")
    id("signing")
    id("jacoco")
    id("org.owasp.dependencycheck") version "10.0.4"
    id("com.github.spotbugs") version "6.0.26"
    kotlin("jvm") version "2.1.10"
}

group = "com.chaosguide"
version = "1.0.0"

// ===== Lock Java 21 compilation target =====
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withJavadocJar()
    withSourcesJar()
}

// ===== JaCoCo test coverage report =====
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        showStandardStreams = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// ===== OWASP Dependency-Check: CVE scanning =====
dependencyCheck {
    // Fail build on HIGH+ severity CVEs (CVSS >= 7.0)
    failBuildOnCVSS = 7.0f

    // Output formats
    formats = listOf("HTML", "JSON", "SARIF")

    // Suppress known false positives (if any)
    suppressionFile = file("dependency-check-suppressions.xml").takeIf { it.exists() }?.absolutePath

    // Analyzers configuration
    analyzers.apply {
        assemblyEnabled = false  // .NET assemblies
        nuspecEnabled = false    // NuGet packages
        nodeEnabled = false      // Node.js packages
    }

    // NVD API configuration (optional, speeds up scanning)
    nvd.apply {
        apiKey = providers.gradleProperty("nvdApiKey").orNull
            ?: System.getenv("NVD_API_KEY")
    }
}

// ===== SpotBugs: Static code analysis =====
spotbugs {
    ignoreFailures = true  // Don't fail build on SpotBugs warnings (report only)
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("spotbugs-exclude.xml").takeIf { it.exists() }
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsMain") {
    reports.create("html") {
        required = true
        outputLocation = file("${layout.buildDirectory.get()}/reports/spotbugs/spotbugs-main.html")
    }
    reports.create("xml") { required = false }
}

tasks.named<com.github.spotbugs.snom.SpotBugsTask>("spotbugsTest") {
    reports.create("html") {
        required = true
        outputLocation = file("${layout.buildDirectory.get()}/reports/spotbugs/spotbugs-test.html")
    }
    reports.create("xml") { required = false }
}

// ===== Javadoc: UTF-8 encoding + lenient mode =====
tasks.withType<Javadoc> {
    options.encoding = "UTF-8"
    (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
}

// ===== Java compiler: -parameters flag for constructor parameter name reflection =====
tasks.withType<JavaCompile> {
    options.compilerArgs.add("-parameters")
}

// ===== Kotlin: 仅用于测试（data class 映射验证），main 无 Kotlin 源码 =====
kotlin {
    compilerOptions {
        javaParameters.set(true)
    }
}

// ===== JAR manifest: JPMS Automatic-Module-Name =====
tasks.withType<Jar> {
    manifest {
        attributes("Automatic-Module-Name" to "com.chaosguide.jpa.booster")
    }
}

// ===== Publishing =====
// Disable Gradle Module Metadata so all consumers (Maven + Gradle) use POM only, preventing BOM leakage via .module
tasks.withType<GenerateModuleMetadata> {
    enabled = false
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "booster-query"

            pom {
                name.set("BoosterQuery")
                description.set("Spring Data JPA native SQL enhancement library with smart SQL rewriting, result mapping, and parameter binding")
                url.set("https://github.com/axis-iam/BoosterQuery")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        id.set("chaosguide")
                        name.set("ChaosGuide")
                        url.set("https://github.com/axis-iam")
                    }
                }
                scm {
                    url.set("https://github.com/axis-iam/BoosterQuery")
                    connection.set("scm:git:git://github.com/axis-iam/BoosterQuery.git")
                    developerConnection.set("scm:git:ssh://github.com/axis-iam/BoosterQuery.git")
                }

                // Strip dependencyManagement from POM to prevent forcing consumers to import Spring Boot BOM
                withXml {
                    val root = asNode()
                    root.children().filterIsInstance<groovy.util.Node>()
                        .filter { it.name().toString().contains("dependencyManagement") }
                        .forEach { root.remove(it) }
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"
            val releasesRepoUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsRepoUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl
            credentials {
                username = findProperty("ossrhUsername") as String? ?: System.getenv("OSSRH_USERNAME") ?: ""
                password = findProperty("ossrhPassword") as String? ?: System.getenv("OSSRH_PASSWORD") ?: ""
            }
        }
    }
}

// ===== GPG signing =====
signing {
    // CI 环境使用环境变量中的 GPG 密钥
    val signingKey = findProperty("signingKey") as String? ?: System.getenv("GPG_SIGNING_KEY")
    val signingPassword = findProperty("signingPassword") as String? ?: System.getenv("GPG_SIGNING_PASSWORD")
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
    }
    sign(publishing.publications["maven"])
}

// 本地开发时 signing 不是必须的
tasks.withType<Sign>().configureEach {
    onlyIf { project.hasProperty("signingKey") || System.getenv("GPG_SIGNING_KEY") != null }
}

repositories {
    mavenCentral()
}

// ===== Dependencies =====
val springBootVersion = "4.0.0"

dependencies {
    // BOM for internal version alignment only, not propagated to consumers
    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

    // Public API types → api scope → POM compile scope (explicit version, no BOM propagation)
    api("org.springframework.boot:spring-boot-starter-data-jpa:$springBootVersion")

    // Internal implementation → implementation scope → POM runtime scope (not compile-visible to consumers)
    implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    implementation("com.github.jsqlparser:jsqlparser:5.3")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.3")

    // Compile-visible but not transitive (already provided by spring-boot-starter-data-jpa)
    compileOnly("jakarta.persistence:jakarta.persistence-api")
    compileOnly("org.jspecify:jspecify:1.0.0")

    // Micrometer 可观测性（可选依赖，不引入时所有指标操作静默跳过）
    compileOnly("io.micrometer:micrometer-core")
    testImplementation("io.micrometer:micrometer-core")

    // Spring Boot Configuration Processor for IDE autocomplete
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:$springBootVersion")

    // Test dependencies
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Force upgrade AssertJ to fix CVE-2026-24400 (affects < 3.27.7)
    testImplementation("org.assertj:assertj-core:3.27.7")

    testImplementation(platform("org.testcontainers:testcontainers-bom:1.21.3"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mysql")
    testImplementation("org.testcontainers:postgresql")

    testRuntimeOnly("com.mysql:mysql-connector-j")
    testRuntimeOnly("org.postgresql:postgresql")

    // Kotlin stdlib (仅测试用，验证 data class 映射)
    testImplementation(kotlin("stdlib"))
}
