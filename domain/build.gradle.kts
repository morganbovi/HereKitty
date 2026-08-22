plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.koinCompiler)
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(libs.koin.core)
    api(libs.koin.annotations)
    api(libs.kotlinLogging)
    api(libs.slf4j.api)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

/*
 * The running app has to know its own version to compare it against a release, and a packaged app
 * cannot read gradle.properties. So the one value there is generated into a source file in `:domain`,
 * which every other module already depends on.
 */
val generateBuildInfo by tasks.registering {
    description = "Writes the project version into a Kotlin source file."

    val version = project.version.toString()
    val outputDirectory = layout.buildDirectory.dir("generated/buildInfo/kotlin")

    inputs.property("version", version)
    outputs.dir(outputDirectory)

    doLast {
        val file = outputDirectory.get().asFile.resolve("io/sweatshop/herekitty/domain/BuildInfo.kt")
        file.parentFile.mkdirs()
        file.writeText(
            """
            package io.sweatshop.herekitty.domain

            /** Generated from the version in gradle.properties. Do not edit. */
            object BuildInfo {
                const val VERSION: String = "$version"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateBuildInfo)
}
