plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.orba.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.orba.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 11
        versionName = "4.1"
        resValue("string", "app_name", "Orba")
        // Por ahora solo existe la versión completa (videos y música juntos)
        buildConfigField("boolean", "MUSIC_ONLY", "false")
        buildConfigField("boolean", "ALL_IN_ONE", "true")
        // Contraseña del dueño (pantalla de bloqueo → "Soy el dueño"). Cámbiala aquí si quieres.
        buildConfigField("String", "ADMIN_PASSWORD", "\"Orba2026ByHunter**\"")
        // Secreto con el que se generan las claves de activación. No lo compartas.
        buildConfigField("String", "LICENSE_SECRET", "\"oRb4-k9Qm2-Vx4Lp-8sTb\"")
    }

    // Un APK por arquitectura: el motor de descarga trae Python y FFmpeg nativos,
    // así que separar por ABI reduce mucho el tamaño. Tu Xiaomi 14T usa arm64-v8a.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    // Clave fija: así cada versión nueva se instala encima de la anterior sin perder tus videos.
    signingConfigs {
        create("personal") {
            storeFile = file("orba.jks")
            storePassword = "orba2026key"
            keyAlias = "orba"
            keyPassword = "orba2026key"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("personal")
            isMinifyEnabled = false
            isDebuggable = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Media3 marca muchas funciones como "inestables"; aquí se usan a propósito.
        disable += "UnsafeOptInUsageError"
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        // youtubedl-android necesita extraer sus librerías nativas al instalar
        jniLibs { useLegacyPackaging = true }
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // Reproductor (ExoPlayer) + sesión multimedia para segundo plano
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-database:$media3")

    // Respaldo en Google Drive (iniciar sesión con tu cuenta de Google)
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    // Base de datos local
    val room = "2.7.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Descargas en segundo plano
    implementation("androidx.work:work-runtime-ktx:2.10.1")

    // Motor de extracción (yt-dlp) + FFmpeg para unir audio y video
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.10.2")
}

// El APK sale con el nombre de la app: Orba-arm64-v8a-release.apk
base {
    archivesName.set("Orba")
}
