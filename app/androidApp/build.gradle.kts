plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.serialization)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "dev.fopwoc.chronosplit.android"
    compileSdk = libs.versions.androidSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.fopwoc.chronosplit.android"
        minSdk = 26
        targetSdk = libs.versions.androidSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(projects.app.appShared)
    implementation(libs.androidxActivityCompose)
    implementation(libs.androidxComposeUi)
    implementation(libs.androidxComposeUiToolingPreview)
    implementation(libs.androidxMaterial3)
    implementation(libs.androidxNavigation3Runtime)
    implementation(libs.androidxNavigation3Ui)
    implementation(libs.androidxLifecycleViewModelCompose)
    implementation(libs.ktorClientCio)
}
