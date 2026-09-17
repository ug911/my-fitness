# Keep kotlinx.serialization metadata for the AI request/response models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ug911.myfitness.** {
    *** Companion;
}
