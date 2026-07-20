plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    jvm()
    jvmToolchain(libs.versions.java.get().toInt())
    sourceSets {
        jvmMain.dependencies {
            implementation(projects.shared.server)
            implementation(libs.ktorServerNetty)
        }
        jvmTest.dependencies {
            implementation(libs.test)
            implementation(libs.ktorServerTestHost)
        }
    }
}

tasks.register<Sync>("installBackend") {
    dependsOn("jvmJar")
    from(tasks.named("jvmJar"))
    from(configurations.named("jvmRuntimeClasspath"))
    into(layout.buildDirectory.dir("install/backend/lib"))
}
