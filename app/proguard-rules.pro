# Vosk talks to native code through JNA, which needs its classes intact.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**

# Keep exception class names readable in error messages and logs.
-keepnames class * extends java.lang.Throwable
# Keep Media3's class and method names (still shrunk) so a failed reel says where it failed.
-keepnames class androidx.media3.** { *; }
