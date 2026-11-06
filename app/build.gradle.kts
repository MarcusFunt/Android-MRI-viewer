plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseKeystorePath = providers.environmentVariable("NEUROVIEW_RELEASE_KEYSTORE_PATH").orNull
val releaseStorePassword = providers.environmentVariable("NEUROVIEW_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("NEUROVIEW_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("NEUROVIEW_RELEASE_KEY_PASSWORD").orNull
val releaseSigningConfigured = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.marcusfunt.neuroview"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.marcusfunt.neuroview"
        minSdk = 26
        targetSdk = 36
        versionCode = 26
        versionName = "1.6.0-beta.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "BRAIN_SUITE_ENABLED", "true")
    }

    if (releaseSigningConfigured) {
        signingConfigs {
            create("neuroViewRelease") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("neuroViewRelease")
        }
        create("serverless") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".serverless"
            versionNameSuffix = "-serverless"
            isDebuggable = false
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("boolean", "BRAIN_SUITE_ENABLED", "false")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
