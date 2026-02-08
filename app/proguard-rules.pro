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

# Keep JNI native methods and their classes
-keepclassmembers class com.agcforge.videodownloader.utils.AppManager {
    public static native *;
    private static native *;
}

# Keep the AppManager class
-keep class com.agcforge.videodownloader.utils.AppManager {
    *;
}

# Keep all JNI-related classes and methods
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep the JNI_OnLoad function
-keepclasseswithmembernames class * {
    native <methods>;
}

# Untuk kelas yang memiliki metode native
-keepclasseswithmembernames class * {
    @androidx.annotation.Keep <methods>;
}

# Tambahkan untuk mencegah R8 mengubah nama native methods
-dontobfuscate
-keeppackagenames com.agcforge.videodownloader.utils