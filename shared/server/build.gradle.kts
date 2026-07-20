plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.java.get().toInt())

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.models)
            implementation(libs.coroutines)
            implementation(libs.ktorServerCore)
            implementation(libs.ktorServerContentNegotiation)
            implementation(libs.ktorServerWebsockets)
            implementation(libs.ktorSerializationJson)
        }
        commonTest.dependencies {
            implementation(libs.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktorClientWebsockets)
            implementation(libs.ktorServerTestHost)
        }
    }
}
