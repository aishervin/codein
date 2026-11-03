# JSch ProGuard rules
-keep class com.jcraft.jsch.** { *; }
-keep class com.github.mwiede.jsch.** { *; }

# Room ProGuard rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Moshi rules
-keep class com.squareup.moshi.** { *; }
-keepclasseswithmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Coroutines rules
-keepclassmembers class kotlinx.coroutines.** { *; }
