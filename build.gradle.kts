plugins {
    val kotlinVersion = "1.9.21"
    kotlin("multiplatform") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("com.android.library") version "8.3.0-alpha17"
}

group = "me.huizengek.vexilla.client"
version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

android {
    compileSdk = 34
    namespace = "me.huizengek.vexilla.client"

    kotlin {
        jvmToolchain(8)
    }
}

kotlin {
    jvmToolchain(8)

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnitPlatform()
            }
        }
    }

    js(IR) {
        // Removed for now because of the potential bug in the testing framework, not sure yet
        // browser()
        nodejs()
    }

    androidTarget()

    mingwX64()
    macosX64()
    linuxX64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val ktorVersion = "2.3.7"
        val coroutinesVersion = "1.7.3"

        commonMain {
            dependencies {
                implementation("io.ktor:ktor-client-core:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        commonTest {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
                implementation(kotlin("test"))
            }
        }
        jvmMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-cio:$ktorVersion")
            }
        }
        androidMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-android:$ktorVersion")
            }
        }
        jsMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-js:$ktorVersion")
            }
        }
        macosMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-darwin:$ktorVersion")
            }
        }
        linuxMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-curl:$ktorVersion")
            }
        }
        mingwMain {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-winhttp:$ktorVersion")
            }
        }
    }

    explicitApi()
}
