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

# ========== TENSORFLOW LITE RULES ==========

# Keep TensorFlow Lite classes
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# Keep TensorFlow Lite GPU delegate
-keep class org.tensorflow.lite.gpu.GpuDelegate { *; }
-keep class org.tensorflow.lite.gpu.CompatibilityList { *; }

# Keep TensorFlow Lite operations
-keep class org.tensorflow.lite.support.common.** { *; }
-keep class org.tensorflow.lite.support.image.** { *; }
-keep class org.tensorflow.lite.support.tensorbuffer.** { *; }

# Prevent obfuscation of model files
-keep class **.tflite { *; }

# ========== ML KIT RULES ==========

# Keep ML Kit Face Detection classes
-keep class com.google.mlkit.vision.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-keep class com.google.mlkit.common.** { *; }

# ========== FACE RECOGNITION RULES ==========

# Keep FaceNet model classes
-keep class com.example.facedetectionapp.FaceNetModel { *; }
-keep class com.example.facedetectionapp.FaceNetModel$** { *; }

# Keep FaceRecognitionManager classes
-keep class com.example.facedetectionapp.FaceRecognitionManager { *; }
-keep class com.example.facedetectionapp.FaceRecognitionManager$** { *; }

# Keep AntiSpoofingDetector classes
-keep class com.example.facedetectionapp.AntiSpoofingDetector { *; }
-keep class com.example.facedetectionapp.AntiSpoofingDetector$** { *; }

# Keep BitmapUtils
-keep class com.example.facedetectionapp.BitmapUtils { *; }

# Keep MainActivity.FaceData class and its fields
-keep class com.example.facedetectionapp.MainActivity$FaceData { *; }
-keepclassmembers class com.example.facedetectionapp.MainActivity$FaceData {
    public *;
}

# ========== SERIALIZATION RULES ==========

# Keep Serializable classes for face database storage
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep PersonEmbedding class for serialization
-keep class com.example.facedetectionapp.FaceRecognitionManager$PersonEmbedding {
    <fields>;
    <methods>;
}

# ========== CAMERA AND GRAPHICS RULES ==========

# Keep CameraX classes
-keep class androidx.camera.** { *; }

# Keep graphics and bitmap classes
-keep class android.graphics.** { *; }
-keep class android.media.Image { *; }
-keep class android.media.Image$Plane { *; }

# ========== PERFORMANCE OPTIMIZATION RULES ==========

# Keep classes used by performance monitoring
-keep class android.os.Handler { *; }
-keep class android.os.HandlerThread { *; }

# Keep concurrent utilities
-keep class java.util.concurrent.** { *; }
-keep class java.util.concurrent.atomic.** { *; }

# ========== ANNOTATION RULES ==========

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions

# Keep annotations for Camera2/CameraX
-keep class androidx.annotation.** { *; }
-keep class androidx.camera.core.ExperimentalGetImage { *; }

# ========== REFLECTION RULES ==========

# Keep classes that might be accessed via reflection
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ========== NATIVE CODE RULES ==========

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep classes with JNI
-keep class * {
    native <methods>;
}

# ========== CUSTOM VIEW RULES ==========

# Keep custom view constructors
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep FaceOverlayView
-keep class com.example.facedetectionapp.FaceOverlayView {
    <init>(...);
    public *;
}

# ========== THREAD SAFETY RULES ==========

# Keep volatile fields
-keepclassmembernames class * {
    volatile <fields>;
}

# Keep synchronized methods
-keepclassmembernames class * {
    synchronized <methods>;
}

# ========== DEBUGGING RULES ==========

# Keep source file names and line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep parameter names for debugging
-keepparameternames

# ========== ADDITIONAL OPTIMIZATION RULES ==========

# Optimize but don't remove logging in debug builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove System.out.println calls
-assumenosideeffects class java.io.PrintStream {
    public void println(...);
    public void print(...);
}

# Optimize string concatenation
-optimizations !code/simplification/string

# ========== SPECIFIC FACE RECOGNITION OPTIMIZATIONS ==========

# Don't obfuscate face embedding arrays
-keepclassmembers class * {
    float[] embedding;
    float[] *embedding*;
}

# Keep face metrics and analysis classes
-keep class **.FaceMetrics { *; }
-keep class **.StaticAnalysis { *; }
-keep class **.LivenessAnalysis { *; }
-keep class **.DetectionResult { *; }
-keep class **.MatchResult { *; }
-keep class **.AddResult { *; }
-keep class **.RecognitionResult { *; }

# Keep model configuration constants
-keepclassmembers class com.example.facedetectionapp.FaceNetModel {
    public static final *;
    private static final *;
}

# Keep threshold values
-keepclassmembers class * {
    **_THRESHOLD;
    **_CONFIDENCE*;
    **_SIZE;
    **_DIM*;
}

# ========== ERROR PREVENTION ==========

# Don't warn about missing classes that might not be available on all devices
-dontwarn org.tensorflow.**
-dontwarn com.google.android.gms.**
-dontwarn androidx.camera.**

# Don't warn about reflection warnings for TensorFlow Lite
-dontwarn java.lang.invoke.StringConcatFactory