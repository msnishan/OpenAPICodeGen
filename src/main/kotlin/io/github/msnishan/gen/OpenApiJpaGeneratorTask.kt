package io.github.msnishan.gen

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.parser.OpenAPIV3Parser
import org.apache.commons.lang3.StringUtils
import org.apache.velocity.Template
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.runtime.RuntimeConstants
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.FileWriter
import java.nio.file.Files

abstract class OpenApiJpaGeneratorTask : DefaultTask() {

    @Input
    var specFile: String = ""

    @Input
    var packageName: String = ""

    @OutputDirectory
    lateinit var outputDir: File

    private val velocityEngine: VelocityEngine = VelocityEngine().apply {
        setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath")
        setProperty("classpath.resource.loader.class", ClasspathResourceLoader::class.java.name)
        init()
    }

    @TaskAction
    fun generate() {
        logger.lifecycle("Generating entities from: $specFile")
        val openAPI: OpenAPI = OpenAPIV3Parser().read(specFile)
            ?: run {
                logger.warn("No schemas found in spec.")
                return
            }

        val schemas = openAPI.components?.schemas ?: return

        val embeddables = mutableMapOf<String, String>()
        processComponents(openAPI.components, embeddables)
        Files.createDirectories(outputDir.toPath())

        val template: Template = velocityEngine.getTemplate("templates/entity.vm")

        for ((className, schema) in schemas) {
            val persistenceType = schema.extensions?.get("x-persist") as? String ?: continue
            val superClass = schema.extensions?.get("x-extends") as? String
            val tableName = schema.extensions?.get("x-table") as? String
            val uniqueConstraints = createTableLevelUniqueKeyViolations(schema.extensions?.get("x-unique"))
            generateEntity(className, schema, template, persistenceType, superClass, embeddables, tableName, uniqueConstraints)
        }
    }

    private fun createTableLevelUniqueKeyViolations(uniqueConstraintsRaw: Any?): List<List<String>> {
        val uniqueConstraints = mutableListOf<List<String>>()
        if (uniqueConstraintsRaw is List<*>) {
            for (group in uniqueConstraintsRaw) {
                if (group is List<*>) {
                    val colNames = group.mapNotNull { it?.toString() }
                    uniqueConstraints.add(colNames)
                }
            }
        }
        return uniqueConstraints
    }

    // most of the code is hack here as open api does not allow extensions for a property which has a $ref
    private fun processComponents(components: Components, embeddables: MutableMap<String, String>) {
        val schemas = components.schemas
        schemas?.forEach { (className, schema) ->
            var updatedSchema = schema
            if (schema.properties == null && schema.allOf != null) {
                updatedSchema = getNonRefSchema(schema)
            }

            val xPersist = updatedSchema.extensions?.get("x-persist") as? String
            if (xPersist == "Embeddable") {
                val fullSourceName = "#/components/schemas/$className"
                embeddables[fullSourceName] = className
            }

            updatedSchema.properties?.forEach inner@{ (prop, propSchema) ->
                if (propSchema !is ArraySchema) return@inner
                if (propSchema.extensions?.get("x-rel") != "one-to-many") return@inner

                val itemSchema = propSchema.items
                val targetRef = itemSchema?.`$ref` ?: return@inner
                val target = getRefName(targetRef)
                val targetSchema = schemas[target] ?: return@inner

                val backRefName = propSchema.extensions?.get("x-mapped-by") as? String
                    ?: className.replaceFirstChar { it.lowercaseChar() }

                val fullSourceName = "#/components/schemas/$className"
                val resolvedTargetSchema = if (targetSchema.allOf != null) getNonRefSchema(targetSchema) else targetSchema

                resolvedTargetSchema.properties?.entries
                    ?.firstOrNull { it.value.`$ref` == fullSourceName }
                    ?.let { entry ->
                        if (entry.value.extensions == null) {
                            entry.value.extensions = mutableMapOf()
                        }
                        entry.value.extensions!!["x-rel"] = "many-to-one"
                    }
            }
        }
    }

    private fun getNonRefSchema(schema: Schema<*>): Schema<*> {
        return schema.allOf.first { (it as Schema<*>).`$ref` == null } as Schema<*>
    }

    private fun generateEntity(
        className: String,
        schema: Schema<*>,
        template: Template,
        entityType: String,
        superClass: String?,
        embeddables: Map<String, String>,
        tableName: String?,
        uniqueConstraints: List<List<String>>
    ) {
        val fields = mutableListOf<Map<String, Any?>>()
        val schemaFinal = if (schema.allOf != null) getNonRefSchema(schema) else schema
        val requiredSet = schemaFinal.required?.toSet() ?: emptySet()

        schemaFinal.properties?.forEach { (name, prop) ->
            val propSchema = prop as Schema<*>
            val field = mutableMapOf<String, Any?>()
            if (ignoreProperty(prop)) return@forEach
            field["name"] = name
            field["required"] = requiredSet.contains(name)
            field["columnData"] = prepareColumnData(propSchema, requiredSet, name)
            field["type"] = getPropJavaType(propSchema)
            field["rel"] = getRelValue(propSchema, embeddables)
            fields.add(field)
        }

        val imports = generateImports(superClass)

        val context = VelocityContext().apply {
            put("importStatements", imports)
            put("tableName", tableName)
            put("packageName", packageName)
            put("className", className)
            put("uniqueConstraints", uniqueConstraints)
            put("persistenceType", if (StringUtils.isEmpty(entityType)) "Entity" else entityType)
            put("superClass", if (!superClass.isNullOrBlank()) " extends ${getSuperClassName(superClass)}" else "")
            put("fields", fields)
            put("StringUtils", StringUtils::class.java)
        }

        val packagePath = packageName.replace('.', File.separatorChar)
        val packageDirectory = File(outputDir, packagePath).toPath()
        Files.createDirectories(packageDirectory)
        val file = packageDirectory.resolve("$className.java")
        FileWriter(file.toFile()).use { writer ->
            template.merge(context, writer)
        }

        logger.lifecycle("Generated entity: $file")
    }

    private fun ignoreProperty(prop: Schema<*>): Boolean {
        val extensions = prop.extensions ?: emptyMap()
        if (extensions.containsKey("x-ignore"))
            return extensions["x-ignore"] is Boolean && extensions["x-ignore"] == true
        return false
    }

    private fun getSuperClassName(superClass: String): String =
        superClass.substringAfterLast('.')

    private fun generateImports(superClass: String?): String {
        var baseImports = """
            import jakarta.persistence.*;
            import java.util.*;
            import java.time.*;
            import java.math.*;
            import lombok.*;
        """.trimIndent()
        if (!superClass.isNullOrBlank() && superClass.contains('.')) {
            baseImports += "\nimport $superClass;"
        }
        return baseImports
    }

    private fun prepareColumnData(propSchema: Schema<*>, requiredSet: Set<String>, name: String): String {
        val extensions = propSchema.extensions ?: emptyMap()
        val columnSet = mutableSetOf<String>()
        if (requiredSet.contains(name)) columnSet += "nullable = false"
        if (extensions.containsKey("x-unique")) columnSet += "unique = true"
        (extensions["x-column-name"] as? String)?.let {
            columnSet += "name = \"$it\""
        }
        return columnSet.joinToString(", ")
    }

    private fun getRelValue(propSchema: Schema<*>, embeddables: Map<String, String>): String? {
        return when {
            propSchema.`$ref` != null && embeddables.containsKey(propSchema.`$ref`) -> "embedded"
            else -> propSchema.extensions?.get("x-rel") as? String
        }
    }

    private fun mapType(schema: Schema<*>): String = when (schema.type) {
        "string" -> "String"
        "integer" -> "Integer"
        "number" -> "BigDecimal"
        "boolean" -> "Boolean"
        "date-time" -> "LocalDateTime"
        else -> "Object"
    }

    private fun getPropJavaType(propSchema: Schema<*>): String = when {
        propSchema.type == "array" -> getRefName((propSchema as ArraySchema).items.`$ref`)
        propSchema.`$ref` != null -> getRefName(propSchema.`$ref`)
        else -> mapType(propSchema)
    }

    private fun getRefName(ref: String?): String = ref?.substringAfterLast('/') ?: "Object"
}
