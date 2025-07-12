plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "1.3.1"
}

group = "io.github.msnishan"
version = "1.0.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    // Swagger OpenAPI Parser
    implementation("io.swagger.parser.v3:swagger-parser:2.1.16")

    // Velocity Template Engine
    implementation("org.apache.velocity:velocity-engine-core:2.3")

    // Optional logging and annotations
    implementation("org.slf4j:slf4j-api:2.0.9")
}
gradlePlugin {
    website.set("https://github.com/msnishan/OpenAPICodeGen")
    vcsUrl.set("https://github.com/msnishan/OpenAPICodeGen")
    plugins {
        create("generateOpenApiJpaEntities") {
            id = "io.github.msnishan.gen"
            implementationClass = "io.github.msnishan.gen.CodegenPlugin"
            displayName = "OPenAPI JPA Entity Generator"
            description = "A Gradle plugin that parses OpenAPI schema definitions and generates JPA entity classes."
            tags.set(listOf("opanapi", "jpa", "entity", "generator", "generate", "codegen"))
        }
    }
}