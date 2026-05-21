# Day 13: R8 без обфускации — читаемые стек-трейсы, безопасная рефлексия.
-dontobfuscate

# --- kotlinx.serialization ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.cleancity.**$$serializer { *; }
-keep class com.example.cleancity.shared.** { *; }

# --- Ktor client ---
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# --- Koin ---
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# --- Yandex MapKit ---
-keep class com.yandex.** { *; }
-dontwarn com.yandex.**

# --- Voyager ---
-keep class cafe.adriel.voyager.** { *; }
