import java.util.Properties

plugins {
    id("com.android.application")
    id("kotlin-android")
}

// Version: the central Android releases (HereLiesAz/workflows android-play-release.yml and
// android-github-release.yml) rewrite version.properties; Play also passes
// -PversionCodeOverride/-PversionNameOverride. Local builds fall back to version.properties.
val versionProps = Properties().apply {
    rootProject.file("version.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun prop(vararg names: String): String? = names.firstNotNullOfOrNull { (findProperty(it) as String?)?.takeIf(String::isNotBlank) }
val appVersionCode = prop("versionCodeOverride", "releaseVersionCode")?.toIntOrNull()
    ?: versionProps.getProperty("versionBuild")?.trim()?.toIntOrNull()?.coerceAtLeast(1)
    ?: 1
val appVersionName = prop("versionNameOverride", "releaseVersionName", "versionName")
    ?: listOf("versionMajor", "versionMinor", "versionPatch").joinToString(".") { versionProps.getProperty(it, "0").trim() }

android {
    namespace = "com.hereliesaz.click"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hereliesaz.click"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        // Signing: the central releases inject the upload key as android.injected.signing.*.
        getByName("release") {
            // R8 on so the Play release has a mapping.txt to upload.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.7.0")
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.13.0") // Updated version
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:4.5.1")
    testImplementation("org.mockito:mockito-inline:4.5.1")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
// utils/Secrets.kt is gitignored (IDEaz secrets). Clean checkouts, including the central
// releases, get an empty placeholder so CrashReporter compiles; a real local file is never touched.
val secretsFile = file("src/main/kotlin/com/hereliesaz/click/utils/Secrets.kt")
if (!secretsFile.exists()) {
    secretsFile.writeText(
        """
        package com.hereliesaz.click.utils

        object Secrets {
            val API_KEY: String? = null
            const val GITHUB_USER: String = ""
            const val REPO_SOURCE: String = ""
        }
        """.trimIndent() + "\n"
    )
}
