# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers so crash reports from minified builds stay readable.
-keepattributes SourceFile,LineNumberTable

# Platform XML parsing is resolved at runtime by the framework.
-dontwarn org.xmlpull.v1.**
-dontwarn android.content.res.**

# Kotlin coroutines keep their dispatcher service lookups intact.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-dontwarn androidx.compose.**

# OkHttp/okio reference optional platform APIs.
-dontwarn okhttp3.**
-dontwarn okio.**

# Strip verbose logging from every build configuration.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
