# R8 is on for release builds. Everything below is reached reflectively, which the shrinker has no
# way to see for itself.

# Line numbers in crash reports, without leaking the original file names.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Moshi reads these annotations and the generated adapters at runtime.
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
# The reflective adapter factory serialises this one, so its property names have to survive.
-keep class kotlin.Metadata { *; }
-keepclassmembers class com.hiosdra.hreader.util.SyncPerformanceRecord { *; }

# Wire models are built from JSON by property name, never constructed by the app.
-keep class com.hiosdra.hreader.data.remote.**.dto.** { *; }
-keep class com.hiosdra.hreader.data.ai.OpenRouter** { *; }

# Retrofit resolves service interfaces and their generic return types reflectively.
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# OkHttp references optional platform classes that are simply absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Room instantiates the generated implementation by name.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# WorkManager constructs workers from the class name stored in its own database.
-keep class * extends androidx.work.ListenableWorker { *; }

-dontwarn org.jsoup.**
