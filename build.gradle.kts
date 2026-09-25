plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.10.2"
}

group = "dev.solomaster"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// Resolved versions are committed in gradle.lockfile; refresh with `./gradlew dependencies --write-locks`.
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.STRICT
}

// Overrides Spring Boot 4.1.1's managed Tomcat 11.0.24 (GHSA-9xv2-5v5q-p794, GHSA-gcx9-497g-6cp6,
// GHSA-h3x4-894j-xpx5). Remove once a Spring Boot release manages 11.0.25 or later.
extra["tomcat.version"] = "11.0.26"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    java {
        googleJavaFormat("1.36.1")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}
