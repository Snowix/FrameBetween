plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.framebetween.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.framebetween.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        // 只产出 arm64-v8a 安装包：现代设备全部支持，体积可大幅缩小。
        // 若将来需要兼容 32 位设备，在这里追加 "armeabi-v7a" 即可。
        ndk { abiFilters += listOf("arm64-v8a") }
        // 应用只提供中英双语，剔除依赖库自带的其它语言资源
        resourceConfigurations += listOf("zh", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }

    // 排除依赖库里用不到的许可证 / 调试元数据，减小 APK 体积（不触碰业务资源）
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0",
                "META-INF/LGPL2.1",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.documentfile)
    implementation(libs.kotlinx.coroutines.android)

    // FFmpeg 9.0.2 AAR, built from ffmpeg-kit-next v9.0.2 for arm64-v8a only.
    // The AAR pom declares two runtime dependencies that files() does not carry over;
    // kotlin-stdlib comes from the Kotlin plugin, so only the other one is added below.
    implementation(files("libs/ffmpeg-kit-next-9.0.2.aar"))
    implementation(libs.smart.exception.java)

    testImplementation(libs.junit)
}
