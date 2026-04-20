plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun String.toBuildConfigString(): String = "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

val onnxRuntimeNative by configurations.creating

dependencies {
    add(onnxRuntimeNative.name, libs.onnxruntime.android)
}

val onnxRuntimeAarPath = onnxRuntimeNative.singleFile.invariantSeparatorsPath
val piperVendorRoot = rootProject.file("_vendor/piper1-gpl").invariantSeparatorsPath

android {
    namespace = "com.example.bluex"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.bluex"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "PIPER_MODEL_ASSET_PATH",
            "piper/es_ES-carlfm-x_low.onnx".toBuildConfigString()
        )
        buildConfigField(
            "String",
            "PIPER_MODEL_CONFIG_ASSET_PATH",
            "piper/es_ES-carlfm-x_low.onnx.json".toBuildConfigString()
        )
        buildConfigField(
            "String",
            "PIPER_ESPEAK_DATA_ASSET_PATH",
            "piper/espeak-ng-data".toBuildConfigString()
        )

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += " -std=c++17"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DONNXRUNTIME_AAR=$onnxRuntimeAarPath",
                    "-DPIPER_VENDOR_ROOT=$piperVendorRoot"
                )
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.onnxruntime.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
