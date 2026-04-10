# GSON rules
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes *Annotation*
-dontwarn sun.misc.**

# Keep GSON classes
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

# Keep the data models and their members for JSON mapping
-keep class in.instantconnect.httpprobe.data.** { *; }
-keepclassmembers class in.instantconnect.httpprobe.data.** {
    <fields>;
    <init>(...);
}

# Keep the Fragment and its anonymous classes to preserve TypeToken signatures
-keep class in.instantconnect.httpprobe.SettingsFragment { *; }
-keep class in.instantconnect.httpprobe.SettingsFragment$* { *; }
