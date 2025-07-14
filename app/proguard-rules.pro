# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }


# AppCompat
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Lifecycle
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# LiveData observers
-keepclassmembers class * {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}

# Core KTX
-dontwarn androidx.core.**
-keep class androidx.core.** { *; }

# ConstraintLayout
-dontwarn androidx.constraintlayout.**

# Transition
-dontwarn androidx.transition.**
-keep class androidx.transition.** { *; }

# Activity KTX
-dontwarn androidx.activity.**


# ExifInterface
-keep class androidx.exifinterface.** { *; }

# PhotoView
-dontwarn uk.co.senab.photoview.**
-keep class uk.co.senab.photoview.** { *; }


-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.firebase.components.ComponentRegistrar
-keep class com.google.firebase.** { *; }


-dontwarn org.junit.**
-keep class org.junit.** { *; }

-dontwarn androidx.test.**
-keep class androidx.test.** { *; }


# Firebase common
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Crashlytics
-keepattributes SourceFile,LineNumberTable
-keep class com.google.firebase.crashlytics.** { *; }

# Firebase Messaging
-keep class com.google.firebase.messaging.FirebaseMessagingService { *; }

# Remote Config
-keep class com.google.firebase.remoteconfig.** { *; }

# Firestore
-keep class com.google.firebase.firestore.** { *; }

# Firebase Database
-keep class com.google.firebase.database.** { *; }

# Analytics
-keep class com.google.firebase.analytics.** { *; }

-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * implements com.bumptech.glide.module.LibraryGlideModule
-dontwarn com.bumptech.glide.**

-dontwarn com.google.android.gms.**
-keep class com.google.android.gms.** { *; }

# Room Annotations
-keepclassmembers class * {
    @androidx.room.* <methods>;
}

# Keep entities, daos and database
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

-dontwarn androidx.room.**

-keep class com.skydoves.balloon.** { *; }
-dontwarn com.skydoves.balloon.**

# RecyclerViewFastScroller
-keep class com.quiph.ui.recyclerviewfastscroller.** { *; }

# Intuit SDP/SSP
-dontwarn com.intuit.sdp.**
-dontwarn com.intuit.ssp.**
-keep class com.intuit.sdp.** { *; }
-keep class com.intuit.ssp.** { *; }

# Maven Gradle Plugin – build time only
-dontwarn com.github.dcendents.**

# Flexbox Layout
-keep class com.google.android.flexbox.** { *; }
-dontwarn com.google.android.flexbox.**


# Keep annotations
-keepattributes *Annotation*

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

-keep class com.photogallery.model.** { *; }
-keep class com.photogallery.crop.model.** { *; }
-keep class com.photogallery.db.model.** { *; }

-keepattributes Signature
-keepattributes *Annotation*

# --- Core runtime ------------------------------------------------------------
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.** { native <methods>; }
-dontwarn org.tensorflow.**

# --- FlatBuffers (model metadata) -------------------------------------------
-keep class com.google.flatbuffers.** { *; }
-dontwarn com.google.flatbuffers.**

# --- Optional delegates ------------------------------------------------------
-keep class org.tensorflow.lite.gpu.**     { *; }   # GPU
-keep class org.tensorflow.lite.nnapi.**   { *; }   # NNAPI
-keep class org.tensorflow.lite.hexagon.** { *; }   # Hexagon DSP
-keep class org.tensorflow.lite.xnnpack.** { *; }   # XNNPACK
-dontwarn org.tensorflow.lite.gpu.**
-dontwarn org.tensorflow.lite.nnapi.**
-dontwarn org.tensorflow.lite.hexagon.**
-dontwarn org.tensorflow.lite.xnnpack.**

# --- Support & Task libraries (only if you add these AARs) -------------------
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.task.**    { *; }
-dontwarn org.tensorflow.lite.support.**
-dontwarn org.tensorflow.lite.task.**

# --- Logging helpers that TFLite references via reflection -------------------
-keep class com.google.flogger.** { *; }
-keep class com.google.errorprone.annotations.** { *; }
-keepclassmembers class * {
    @com.google.errorprone.annotations.FormatMethod <methods>;
    @com.google.errorprone.annotations.FormatString <fields>;
}

# --- Misc. community issues --------------------------------------------------
-dontwarn org.checkerframework.**        # fixes R8 warning seen in TFLite 2.x :contentReference[oaicite:0]{index=0}