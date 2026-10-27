# Keep JNI entry points and reflection-heavy libraries used by the tools.
-keep class com.arthenica.ffmpegkit.** { *; }
-keep class com.awxkee.jxlcoder.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.github.junrar.** { *; }
-keep class net.lingala.zip4j.** { *; }
-keep class org.yaml.snakeyaml.** { *; }
-keep class org.tomlj.** { *; }
-keep class com.caverock.androidsvg.** { *; }
-keep class org.tukaani.xz.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class com.example.toolkit.ui.tools.RawDecoder { *; }
-keepclasseswithmembernames class * { native <methods>; }

# Libraries occasionally reference desktop-only classes; they are unused on Android.
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.slf4j.**
-dontwarn org.apache.**
-dontwarn com.tom_roush.pdfbox.**
