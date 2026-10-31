# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/tiansvenja/Library/Android/sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.kts.

# WebView Javascript interface mapping (keep JavascriptInterface methods)
-keep class com.achtsamweg.remindful.MainActivity$WebAppInterface {
    *;
}
-keepclassmembers class com.achtsamweg.remindful.MainActivity$WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep all classes in our package to prevent any unexpected obfuscation crashes
-keep class com.achtsamweg.remindful.** { *; }
