plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

group = "fr.nathanaudvard"

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64()
        hostOs == "Linux" && isArm64 -> linuxArm64()
        hostOs == "Linux" -> linuxX64()
        hostOs.startsWith("Windows") -> mingwX64()
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                baseName = "emp"
                entryPoint = "fr.nathanaudvard.emp.main"
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.config.yaml)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.resources)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.client.resources)
        }
    }
}
