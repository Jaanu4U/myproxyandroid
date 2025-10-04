# MyProxy Android App ProGuard Rules

# Keep API models
-keep class com.myproxy.api.** { *; }

# Keep Ktor classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep WireGuard classes
-keep class com.wireguard.** { *; }
-dontwarn com.wireguard.**

# Keep Gson classes
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep service classes
-keep class com.myproxy.vpn.** { *; }
