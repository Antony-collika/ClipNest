# ClipNest release rules
# Flexmark 0.42.14 builds a runtime dependency graph from renderer factory
# classes. R8 optimization/obfuscation changes that graph and can cause:
# "Dependent class ... is duplicated" during HtmlRenderer initialization.
# Keep Flexmark stable first; narrow these rules only after device smoke tests.
-keep class com.vladsch.flexmark.** { *; }
-keep interface com.vladsch.flexmark.** { *; }
-keep enum com.vladsch.flexmark.** { *; }

# flexmark-util also ships optional desktop image/UI helpers. Android does not
# provide java.awt, javax.imageio, javax.swing, or sun.misc BASE64 classes;
# suppress only their missing-class diagnostics because these helpers are not
# part of ClipNest's Markdown rendering path.
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn sun.misc.**

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
