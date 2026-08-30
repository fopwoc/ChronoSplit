package dev.fopwoc.chronosplit.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateBuildIdentityTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Input
    abstract val numericVersion: Property<String>

    @get:Input
    abstract val buildNumber: Property<Int>

    @get:Input
    abstract val commitHash: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        directory.resolve("build-identity.properties").writeText(
            "version=${version.get()}\n" +
                "numericVersion=${numericVersion.get()}\n" +
                "buildNumber=${buildNumber.get()}\n" +
                "commitHash=${commitHash.get()}\n",
        )
        directory.resolve("BuildIdentity.xcconfig").writeText(
            "MARKETING_VERSION=${numericVersion.get()}\n" +
                "CURRENT_PROJECT_VERSION=${buildNumber.get()}\n",
        )
    }
}
