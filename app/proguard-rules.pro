# R8 rules for the release build. Debug builds are not minified.

# --- kotlinx.serialization -------------------------------------------------
# The generated serializers are reached via Companion objects, which R8 cannot
# see being used. These are the rules from the kotlinx.serialization docs.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# --- WorkManager -----------------------------------------------------------
# Workers are instantiated by class name, so the constructor must survive.
-keep class com.ug911.myfitness.health.HealthSyncWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- Room ------------------------------------------------------------------
# The generated *_Impl classes are looked up reflectively from the database class.
-keep class com.ug911.myfitness.data.local.MyFitnessDatabase_Impl { <init>(); }
