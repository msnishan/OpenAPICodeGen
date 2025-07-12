# OpenAPI JPA Entity Generator Plugin

A Gradle plugin that parses OpenAPI 3.x schema definitions and generates annotated JPA entity classes using Velocity templates.

---

## ✨ Features

- Supports `@Entity`, `@Embeddable`, and relationship annotations.
- Reads `x-persist`, `x-rel`, `x-table`, `x-unique`, `x-column-name` extensions.
- Uses Apache Velocity for templating.
- Compatible with Kotlin and Groovy build scripts.

---

## 🛠 Usage

### 1. Apply the Plugin

If published locally:

```groovy
plugins {
    id "com.msnishan.gen" version "0.0.1"
}
```

### 2. Use the Plugin Task
#### Groovy
```groovy
tasks.named("generateOpenApiJpaEntities") {
    specFile = "$rootDir/openapi.yaml"
    packageName = "com.example.generated"
    outputDir = file("$buildDir/generated-entities/src/main/java")
}
```
#### Kotlin
```kotlin
import com.msnishan.gen.openapi.OpenApiJpaGeneratorTask

tasks.named<OpenApiJpaGeneratorTask>("generateOpenApiJpaEntities") {
    specFile = "$rootDir/openapi.yaml"
    packageName = "com.example.generated"
    outputDir = file("$buildDir/generated-entities/src/main/java")
}
```


### 3. Alternatively, Register the Task

#### Groovy
```groovy
import com.msnishan.gen.openapi.OpenApiJpaGeneratorTask

tasks.register("generateEntities", OpenApiJpaGeneratorTask) {
    specFile = "$rootDir/openapi.yaml"
    packageName = "com.example.generated"
    outputDir = file("$buildDir/generated-entities/src/main/java")
}

sourceSets.main.java.srcDirs += "$buildDir/generated-entities"
```
#### Kotlin
```kotlin
import com.msnishan.gen.openapi.OpenApiJpaGeneratorTask

tasks.register<OpenApiJpaGeneratorTask>("generateEntities") {
    specFile = "$rootDir/openapi.yaml"
    packageName = "com.example.generated"
    outputDir = file("$buildDir/generated-entities/src/main/java")
}

sourceSets["main"].java.srcDirs += "$buildDir/generated-entities"

```
## 🔧 Supported OpenAPI Extensions

| Extension       | Description                                                      |
|-----------------|------------------------------------------------------------------|
| `x-persist`     | Specifies class type: `Entity` or `Embeddable`                   |
| `x-rel`         | Defines JPA relationship: `one-to-many`, `many-to-one`, etc.     |
| `x-mapped-by`   | Name of the inverse relationship field for bidirectional mapping |
| `x-table`       | Overrides the JPA table name                                     |
| `x-column-name` | Overrides the column name in the DB                              |
| `x-ignore`      | Prevents the property from being added to the generated entity   |
| `x-unique`      | Adds uniqueness constraint (can be boolean or array of fields)   |
| `x-extends`     | Fully qualified superclass name to extend                        |

## 📦 Required Dependencies

Make sure your project includes the following dependencies to work with the generated JPA entities:

<details>
<summary>Gradle (Kotlin DSL)</summary>

```kotlin
dependencies {
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
}
```
</details>

## 🔗 Example Project

Check out a complete working example here:

👉 [OpenAPI JPA Generator Example Project](https://github.com/msnishan/openapi-jpa-generator-example)

> Includes:
> - Sample `src/main/resources/openapi/example.yaml` spec
> - Gradle setup with the plugin
> - Generated JPA entities
> - Lombok + JPA dependencies
