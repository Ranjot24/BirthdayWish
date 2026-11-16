plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.eternelle.shiba"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eternelle.shiba"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.2"
    }

    // One fixed key so every build installs as an update over the previous one.
    signingConfigs {
        create("eternelle") {
            storeFile = file("eternelle.keystore")
            storePassword = "eternelle2026"
            keyAlias = "eternelle"
            keyPassword = "eternelle2026"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("eternelle") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("io.github.sceneview:arsceneview:4.18.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
