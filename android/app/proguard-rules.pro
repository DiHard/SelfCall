# Keep LiveKit native bridges
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
