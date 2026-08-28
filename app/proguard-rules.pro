# ClipNest release rules
# Room and Moshi use generated code; retain the runtime entry points that may
# be discovered by generated names or reflection.

# Keep Moshi generated adapters and annotated backup models.
-keep class com.clipnest.data.model.**JsonAdapter { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }

# Keep Room generated database implementation and DAO contracts.
-keep class com.clipnest.data.local.AppDatabase_Impl { *; }
-keep interface com.clipnest.data.local.**Dao { *; }

# Keep Android manifest entry points and provider metadata.
-keep class com.clipnest.MainActivity { *; }
-keep class com.clipnest.service.** { *; }
-keep class androidx.core.content.FileProvider { *; }

# Preserve useful release stack-trace locations without exposing source paths.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
