plugins {
	java
	id("org.springframework.boot") version "4.0.3"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "6.25.0"
	id("org.openrewrite.rewrite") version "7.23.0"
}

group = "cringe"
version = "0.0.1-SNAPSHOT"
description = "Baza"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:2.0.0-M1")
    }
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai-embedding")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-configuration-processor")
	compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    implementation("com.github.pengrad:java-telegram-bot-api:9.6.0")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	rewrite("org.openrewrite.recipe:rewrite-static-analysis:2.37.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

spotless {
	java {
		palantirJavaFormat("2.90.0")
		removeUnusedImports()
		trimTrailingWhitespace()
		endWithNewline()
	}
}

configure<org.openrewrite.gradle.RewriteExtension> {
	activeRecipe("org.openrewrite.staticanalysis.CommonStaticAnalysis")
}

