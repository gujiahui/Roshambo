# Project-specific ProGuard rules.
# Referenced by app/build.gradle.kts (release build type).
#
# Minify is currently disabled for release (isMinifyEnabled = false), so these
# rules only take effect if minification is turned on later.
# MediaPipe tasks-vision ships its own consumer rules inside the AAR; keep the
# gesture/game enums explicit so reflection-free Kotlin code stays intact anyway.
-keep class com.roshambo.app.gesture.** { *; }
-keep class com.roshambo.app.game.** { *; }
