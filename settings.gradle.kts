rootProject.name = "ChronoSplit"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(
    ":app:appShared",
    ":app:androidApp",
    ":app:iosApp",
    ":app:jvmApp",
    ":app:webApp",
    ":backend",
    ":shared:models",
    ":shared:compose",
    ":shared:server",
)
