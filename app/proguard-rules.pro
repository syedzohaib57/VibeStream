# MovieHub R8 / ProGuard rules.

# --- Room -------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# --- Moshi ------------------------------------------------------------------
# Generated adapters are looked up reflectively by class name.
-keep class **JsonAdapter { *; }
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * { <init>(...); }
-keepclasseswithmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-dontwarn okio.**

# --- Retrofit / OkHttp ------------------------------------------------------
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Media3 / ExoPlayer -----------------------------------------------------
# DownloadService and the render/extractor factories are instantiated by name.
-keep class com.example.streamingappzb.data.download.MhDownloadService { *; }
-dontwarn androidx.media3.**

# --- Lottie -----------------------------------------------------------------
-dontwarn com.airbnb.lottie.**

# --- Koin -------------------------------------------------------------------
-keepnames class kotlin.jvm.internal.DefaultConstructorMarker
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# --- App models kept for Moshi + Room reflection ----------------------------
-keep class com.example.streamingappzb.data.catalog.dto.** { *; }
-keep class com.example.streamingappzb.data.db.entity.** { *; }
-keep class com.example.streamingappzb.domain.model.** { *; }

# Custom views are inflated from XML by name.
-keep class com.example.streamingappzb.ui.widget.** {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
