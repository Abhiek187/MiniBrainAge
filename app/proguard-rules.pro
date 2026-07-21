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
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
-renamesourcefileattribute SourceFile

# Fixes a bug where TF Lite fails to get the system or application interpreter using reflection
# See: https://github.com/Abhiek187/MiniBrainAge/wiki/Post-Mortem-1.2.5-15:-The-TF-Lite-Google-Play-Services-runtime-fiasco
-keep class com.google.android.gms.tflite.InterpreterFactoryImpl {
    <init>();
}
-keep class org.tensorflow.lite.InterpreterFactoryImpl {
    <init>();
}
