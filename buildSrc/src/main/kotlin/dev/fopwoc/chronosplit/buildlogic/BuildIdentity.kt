package dev.fopwoc.chronosplit.buildlogic

import org.gradle.api.Project
import java.io.File

data class BuildIdentity(
    val version: String,
    val buildNumber: Int,
    val commitHash: String,
    val isRelease: Boolean,
) {
    val numericVersion: String = if (isRelease) version else "0.0.$buildNumber"
}

object BuildIdentityResolver {
    private val releaseVersion = Regex("\\d+\\.\\d+\\.\\d+")
    private val identities = mutableMapOf<File, BuildIdentity>()

    fun resolve(project: Project): BuildIdentity = synchronized(identities) {
        val repository = project.rootDir.canonicalFile
        identities.getOrPut(repository) { resolveUncached(project, repository) }
    }

    private fun resolveUncached(project: Project, repository: File): BuildIdentity {
        val commitHash = project.environment("COMMIT_HASH")
            ?: project.git(repository, "rev-parse", "--short=8", "HEAD")
        val buildNumber = project.environment("BUILD_NUMBER")?.toIntOrNull()
            ?: project.git(repository, "rev-list", "--count", "HEAD").toInt()
        val exactTag = project.environment("VERSION")
            ?: project.git(repository, "tag", "--points-at", "HEAD", "--sort=-version:refname")
                .lineSequence()
                .firstOrNull(releaseVersion::matches)
        val version = exactTag ?: run {
            val commitDate = project.git(repository, "show", "-s", "--format=%cs", "HEAD")
                .replace("-", "")
            "$commitDate-$commitHash"
        }

        return BuildIdentity(
            version = version,
            buildNumber = buildNumber,
            commitHash = commitHash,
            isRelease = releaseVersion.matches(version),
        )
    }

    private fun Project.environment(name: String): String? =
        providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)

    private fun Project.git(repository: File, vararg arguments: String): String =
        providers.exec {
            workingDir(repository)
            commandLine("git", *arguments)
        }.standardOutput.asText.get().trim()
}
