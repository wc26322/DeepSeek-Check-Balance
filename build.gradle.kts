// Top-level build file
plugins {
    // 9.4.0：支持 compileSdk 37 的最低 AGP（9.1.0 只认到 36.1，无法解析新命名的 android-37.0 平台包）
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
}
