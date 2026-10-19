# LSPosed loads HookEntry by its name in META-INF/xposed/java_init.list.
# Keep hook implementations and helpers intact: callbacks and diagnostic stack
# traces must remain stable, including classes instantiated by the framework.
-keep class com.lijinmu.arashi.xposed.** { *; }

# This method is replaced at runtime. It must not be renamed, removed, inlined
# or folded into a constant false result in the UI.
-keep class com.lijinmu.arashi.Main {
    public boolean isModuleActive();
}

# Preserve the service bridge and its Binder/Parcelable protocol boundaries.
-keep class io.github.libxposed.** { *; }
-keep interface io.github.libxposed.** { *; }

# The libxposed API is supplied by LSPosed at runtime, not bundled in the APK.
-dontwarn io.github.libxposed.api.**

-keepattributes Signature,InnerClasses,EnclosingMethod
