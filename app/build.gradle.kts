plugins {
    id("com.android.application")
    // AGP 9 内置 Kotlin 支持，无需 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.deepseek.balance"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.deepseek.balance"
        minSdk = 26
        targetSdk = 34
        versionCode = 5
        versionName = "1.3.1"

        // 网络安全：允许明文传输（仅用于 API 调用）
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            // 开启 R8 代码优化/压缩 + 资源压缩：显著降低冷启动 JIT 编译量、加快组合与动画
            isMinifyEnabled = true
            isShrinkResources = true
            // 测试用：与 debug 同签名，可直接覆盖安装同包名（正式发布时再换正式签名）
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.02.01")
    implementation(composeBom)
    // Material3 Expressive（最新 alpha：新形状语言 + MotionScheme + 新组件形态）
    implementation("androidx.compose.material3:material3:1.5.0-alpha25")
    // Expressive 形状系统依赖（RoundedPolygon 等有机形状）
    implementation("androidx.graphics:graphics-shapes:1.0.1")

    // Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    // Compose UI
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // 网络请求 - OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // JSON 解析
    implementation("org.json:json:20231013")

    // 本地数据存储（用于保存 API Key）
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // 性能分析 - JankStats（帧率/掉帧监控，输出到 Logcat）
    implementation("androidx.metrics:metrics-performance:1.0.0-beta02")

    // Baseline Profile 运行时安装：让 Compose 等库内置的 AOT 预编译规则生效，
    // 冷启动时组合/动画/渲染路径直接跑编译后的机器码，无需 JIT，根治冷启动掉帧
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
