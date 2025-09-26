# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep BuildConfig
-keep class com.video.vibetube.BuildConfig { *; }

# Keep data classes and models
-keep class com.video.vibetube.models.** { *; }

# Gson specific classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.stream.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Gson annotations
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit specific rules
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes EnclosingMethod
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface com.video.vibetube.network.** { *; }

# YouTube Player
-keep class com.pierfrancescosoffritti.androidyoutubeplayer.** { *; }

# Google Ads
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# OkHttp and Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Fragment and Activity classes
-keep public class * extends androidx.fragment.app.Fragment
-keep public class * extends androidx.appcompat.app.AppCompatActivity

# Keep custom cache classes
-keep class com.video.vibetube.utils.YouTubeCompliantCacheManager { *; }
-keep class com.video.vibetube.utils.QuotaManager { *; }
-keep class com.video.vibetube.utils.NetworkMonitor { *; }
-keep class com.video.vibetube.models.QuotaStatus { *; }

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all model classes used with Firestore
-keep class com.video.vibetube.models.** { *; }
-keep class com.video.vibetube.sync.CrossDeviceSyncManager$** { *; }

# Keep constructors for Firestore serialization
-keepclassmembers class com.video.vibetube.models.** {
    <init>();
    <init>(...);
}

# Keep Firebase/Firestore classes
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Gson classes (if using Gson)
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*