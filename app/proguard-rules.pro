# Sparky IPTV R8 rules (2.8).
#
# R8 shrinks and optimises, but does not rename: the crash report in Diagnostics finds the app's
# own lines by "com.sparky" and must stay readable, and the activity log names classes.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

# The libraries (Media3, OkHttp, Coil, Compose, coroutines, ZXing) ship their own keep rules.
# NanoHTTPD has none and uses no reflection; it only needs its optional imports silenced.
-dontwarn fi.iki.elonen.**

# OkHttp's optional TLS providers, not present on Android TV.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
