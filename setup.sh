#!/bin/bash

# MyProxy Android App Setup Script
echo "🚀 Setting up MyProxy Android App..."

# Create additional required directories
mkdir -p src/main/res/mipmap-hdpi
mkdir -p src/main/res/mipmap-mdpi
mkdir -p src/main/res/mipmap-xhdpi
mkdir -p src/main/res/mipmap-xxhdpi
mkdir -p src/main/res/mipmap-xxxhdpi
mkdir -p src/main/res/xml

# Create basic app icons (placeholder)
echo "📱 Creating app icons..."
cat > src/main/res/mipmap-hdpi/ic_launcher.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/primary_color"/>
    <foreground android:drawable="@drawable/ic_vpn"/>
</adaptive-icon>
EOF

# Copy to other densities
cp src/main/res/mipmap-hdpi/ic_launcher.xml src/main/res/mipmap-mdpi/
cp src/main/res/mipmap-hdpi/ic_launcher.xml src/main/res/mipmap-xhdpi/
cp src/main/res/mipmap-hdpi/ic_launcher.xml src/main/res/mipmap-xxhdpi/
cp src/main/res/mipmap-hdpi/ic_launcher.xml src/main/res/mipmap-xxxhdpi/

# Create round icon
cat > src/main/res/mipmap-hdpi/ic_launcher_round.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/primary_color"/>
    <foreground android:drawable="@drawable/ic_vpn"/>
</adaptive-icon>
EOF

cp src/main/res/mipmap-hdpi/ic_launcher_round.xml src/main/res/mipmap-mdpi/
cp src/main/res/mipmap-hdpi/ic_launcher_round.xml src/main/res/mipmap-xhdpi/
cp src/main/res/mipmap-hdpi/ic_launcher_round.xml src/main/res/mipmap-xxhdpi/
cp src/main/res/mipmap-hdpi/ic_launcher_round.xml src/main/res/mipmap-xxxhdpi/

# Create backup rules
echo "🔒 Creating backup and security rules..."
cat > src/main/res/xml/backup_rules.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="auth_prefs.xml"/>
    <exclude domain="database" path="myproxy.db"/>
</full-backup-content>
EOF

cat > src/main/res/xml/data_extraction_rules.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="auth_prefs.xml"/>
    </cloud-backup>
    <device-transfer>
        <exclude domain="sharedpref" path="auth_prefs.xml"/>
    </device-transfer>
</data-extraction-rules>
EOF

# Create ProGuard rules
echo "⚡ Creating ProGuard rules..."
cat > proguard-rules.pro << 'EOF'
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
EOF

# Create Gradle wrapper properties
echo "🔧 Creating Gradle wrapper..."
mkdir -p gradle/wrapper
cat > gradle/wrapper/gradle-wrapper.properties << 'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

# Create settings.gradle.kts
cat > settings.gradle.kts << 'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyProxy"
include(":app")

// Map current directory as app module
project(":app").projectDir = file(".")
EOF

# Create local.properties template
cat > local.properties.template << 'EOF'
# Location of the Android SDK
sdk.dir=/path/to/Android/Sdk

# MyProxy Configuration
myproxy.server.url=https://myproxy.co.in
myproxy.api.version=v1.0
EOF

echo "✅ Android app setup complete!"
echo ""
echo "📋 Next Steps:"
echo "1. Copy local.properties.template to local.properties"
echo "2. Update SDK path in local.properties"
echo "3. Open project in Android Studio"
echo "4. Sync Gradle and build"
echo "5. Run on device or emulator"
echo ""
echo "📱 Project Structure:"
echo "   /root/android/ - Complete Android app"
echo "   ├── src/main/java/com/myproxy/ - Source code"
echo "   ├── src/main/res/ - Resources"
echo "   ├── build.gradle.kts - Dependencies"
echo "   └── README.md - Documentation"
echo ""
echo "🔗 Related Files:"
echo "   /root/myproxy/MOBILE_APP_API_DOCUMENTATION.md - API docs"
echo "   /root/myproxy/ANDROID_READINESS_CHECKLIST.md - Checklist"
echo ""
echo "🚀 Ready to build MyProxy Android app!"
