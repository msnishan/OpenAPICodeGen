package io.github.msnishan.gen

import io.github.msnishan.gen.OpenApiJpaGeneratorTask
import org.gradle.api.Plugin
import org.gradle.api.Project

class CodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("generateOpenApiJpaEntities", OpenApiJpaGeneratorTask::class.java) {
            group = "codegen"
            description = "Generates JPA Entities from OpenAPI."
        }
    }
}