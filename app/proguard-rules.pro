# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the Android SDK tools ProGuard configuration.

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep model classes
-keep class com.deepseek.balance.model.** { *; }

# Keep JSON
-keep class org.json.** { *; }

# okio（OkHttp 依赖）
-keep class okio.** { *; }
-dontwarn okio.**

# JankStats / androidx.metrics（R8 下保留，避免监控失效）
-keep class androidx.metrics.** { *; }
-dontwarn androidx.metrics.**

# Baseline Profile / profileinstaller
-keep class androidx.profileinstaller.** { *; }
-dontwarn androidx.profileinstaller.**

# security-crypto / tink（API Key 加密存储）
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# datastore（偏好存储）
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.**
-dontwarn org.jetbrains.annotations.**

# Compose 运行时（R8 已感知，仅去警告）
-dontwarn androidx.compose.**
