# HangulMate ProGuard rules — R8 is ON for the debug build (APK size).
# Libraries (Hilt, Room, Compose, CameraX, ML Kit, TFLite, WorkManager,
# DataStore) ship their own consumer rules; these keeps cover the app's
# own code that R8 cannot see is used (generated Room/Hilt code paths,
# ML Kit + TFLite native entry points).

# Room entities, DAOs and the database (generated _Impl is referenced
# reflectively by Room at runtime).
-keep class com.hangulmate.data.local.** { *; }

# Domain + repository models (kept whole: small, and avoids any
# reflection surprise in mappers/quiz code).
-keep class com.hangulmate.domain.model.** { *; }
-keep class com.hangulmate.domain.repository.** { *; }

# ML Kit text recognition + TFLite (native libs + model loading).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }

# CameraX (lifecycle-bound camera code).
-keep class androidx.camera.** { *; }
