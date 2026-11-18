# Keep LiveKit native bridges
-keep class io.livekit.** { *; }
-keep class livekit.** { *; }
-keep class org.webrtc.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# WebRTC внутри LiveKit ссылается на класс, которого нет в сборке (генерируется не всегда)
-dontwarn livekit.org.jni_zero.JniZeroJni
