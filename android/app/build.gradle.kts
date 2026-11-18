import java.io.File
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
}

// Адрес сервиса: на нём и сайт, и API (/api), и ссылки на встречи (/m/...).
// Переопределяется в gradle.properties или так: ./gradlew assembleDebug -PSELFCALL_SERVER=http://10.0.2.2:8080
val server = ((findProperty("SELFCALL_SERVER") as String?) ?: "https://call.gamma.help").trimEnd('/')
val serverUri = URI(server)

val version = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
val signingDirectory = providers.environmentVariable("SELFCALL_SIGNING_DIR").orNull?.let(::File)
    ?: File(System.getProperty("user.home"), "Library/Application Support/SelfCall/android-signing")
val signingCredentials = Properties().apply {
    val file = signingDirectory.resolve("signing.properties")
    if (file.isFile) file.inputStream().use(::load)
}

android {
    namespace = "ru.ifx.selfcall"
    compileSdk = 37

    defaultConfig {
        applicationId = "ru.ifx.selfcall"
        minSdk = 26
        targetSdk = 36
        versionCode = (providers.gradleProperty("SELFCALL_VERSION_CODE").orNull
            ?: version.getProperty("firstVersionCode")).toInt()
        versionName = providers.gradleProperty("SELFCALL_VERSION_NAME").orNull ?: version.getProperty("versionName")

        buildConfigField("String", "SERVER", "\"$server\"")
        // Ссылки https://<домен>/m/... открываются в приложении
        manifestPlaceholders["linkScheme"] = serverUri.scheme
        manifestPlaceholders["linkHost"] = serverUri.host
        // Без HTTPS (локальный сервер на Mac) нужен открытый трафик
        manifestPlaceholders["cleartext"] = (serverUri.scheme == "http").toString()
    }

    signingConfigs {
        create("release") {
            storeFile = signingDirectory.resolve("selfcall-release.p12")
            storeType = "PKCS12"
            storePassword = signingCredentials.getProperty("storePassword")
            keyAlias = "selfcall-release"
            keyPassword = storePassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.livekit.android)
    implementation(libs.livekit.android.compose.components)
}
