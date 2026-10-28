import org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipInputStream

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

group = "fr.nathanaudvard"

// sqliter (SQLDelight native driver) expects a system libsqlite3, which Windows does not ship:
// the amalgamation is compiled into the binary instead
val sqliteRelease = "2026/sqlite-amalgamation-3540100"
val sqliteSha3 = "92281c86e2957f9b9305890f9ac04204caa7b677cc868dc326d942689df28e30"
val sqliteDir = layout.buildDirectory.dir("sqlite")

val downloadSqlite = tasks.register("downloadSqlite") {
    val url = "https://sqlite.org/$sqliteRelease.zip"
    val expectedSha3 = sqliteSha3
    val outputDir = sqliteDir
    inputs.property("url", url)
    outputs.dir(outputDir)
    doLast {
        val zip = URI(url).toURL().readBytes()
        val sha3 = MessageDigest.getInstance("SHA3-256").digest(zip).joinToString("") { "%02x".format(it) }
        check(sha3 == expectedSha3) { "SQLite archive checksum mismatch: $sha3" }

        val dir = outputDir.get().asFile.apply { mkdirs() }
        ZipInputStream(zip.inputStream()).use { entries ->
            generateSequence { entries.nextEntry }
                .first { it.name.endsWith("/sqlite3.c") }
                .let { dir.resolve("sqlite3.c").writeBytes(entries.readBytes()) }
        }
    }
}

// Kotlin/Native has no resources: the stylesheets are compiled into the binary, named after their content so that
// browsers can cache them for good
val generateStylesheets = tasks.register("generateStylesheets") {
    val sourceDir = layout.projectDirectory.dir("src/nativeMain/assets")
    val outputDir = layout.buildDirectory.dir("generated/stylesheets")
    inputs.dir(sourceDir)
    outputs.dir(outputDir)
    doLast {
        val sheets = sourceDir.asFile.walkTopDown().filter { it.extension == "css" }.sortedBy { it.name }.map { file ->
            val content = file.readText().replace("\r\n", "\n")
            val hash = MessageDigest.getInstance("SHA-256").digest(content.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(10)
            val literal = content.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
            "internal val ${file.nameWithoutExtension}Css = Stylesheet(\"${file.nameWithoutExtension}-$hash.css\", \"$literal\")"
        }
        val dir = outputDir.get().asFile.apply { deleteRecursively() }.resolve("fr/nathanaudvard/emp/web/assets")
        dir.mkdirs()
        dir.resolve("Stylesheets.kt").writeText(
            "package fr.nathanaudvard.emp.web.assets\n\n${sheets.joinToString("\n")}\n"
        )
    }
}

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
        compilations.getByName("main").cinterops.create("sqlite") {
            definitionFile = file("src/nativeInterop/cinterop/sqlite.def")
            extraOpts(
                // -Xcompile-source needs the legacy call mode: https://youtrack.jetbrains.com/issue/KT-79749
                "-Xccall-mode", "indirect",
                "-Xcompile-source", sqliteDir.get().file("sqlite3.c").asFile.path,
                "-Xsource-compiler-option", "-xc",
                "-Xsource-compiler-option", "-O2",
                "-Xsource-compiler-option", "-DSQLITE_DQS=0",
                "-Xsource-compiler-option", "-DSQLITE_OMIT_LOAD_EXTENSION",
            )
            tasks.named(interopProcessingTaskName) { dependsOn(downloadSqlite) }
        }

        binaries {
            all {
                // The amalgamation is optimised together with the program, but with compiler caches sqliter is a
                // prebuilt archive: nothing left in the program calls sqlite3_*, so the symbols are stripped and the
                // link fails. Release builds use no caches. Must be renewed for each Kotlin version.
                @OptIn(KotlinNativeCacheApi::class)
                disableNativeCache(
                    DisableCacheInKotlinVersion.`2_4_21`,
                    "SQLite compiled with -Xcompile-source is stripped when sqliter comes from the compiler cache",
                )
            }
            executable {
                baseName = "emp"
                entryPoint = "fr.nathanaudvard.emp.main"
            }
        }
    }

    sourceSets {
        nativeMain {
            kotlin.srcDir(generateStylesheets)
        }
        nativeMain.dependencies {
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.config.yaml)
            implementation(libs.ktor.server.di)
            implementation(libs.ktor.server.content.negotiation)
            implementation(libs.ktor.server.status.pages)
            implementation(libs.ktor.server.resources)
            implementation(libs.ktor.server.html.builder)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(
                when {
                    hostOs.startsWith("Windows") -> libs.ktor.client.winhttp
                    hostOs == "Mac OS X" -> libs.ktor.client.darwin
                    else -> libs.ktor.client.curl
                }
            )
            implementation(libs.sqldelight.native.driver)
            implementation(libs.kotlinx.io.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        nativeTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.ktor.server.test.host)
            implementation(libs.ktor.client.resources)
        }
    }
}

sqldelight {
    databases {
        create("EmpDatabase") {
            packageName = "fr.nathanaudvard.emp.db"
            dialect(libs.sqldelight.dialect)
            deriveSchemaFromMigrations = true
        }
    }
    linkSqlite = false
}
