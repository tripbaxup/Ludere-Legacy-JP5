# Ludere Legacy Autogen — ProGuard rules
# Keep all JNI-called methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep LibretroRuntime callbacks (called from native)
-keep class com.ludere.legacy.libretro.LibretroRuntime {
    void onVideoFrame(int[], int, int, int);
    void onAudioBatch(short[], int);
    void onInputPoll();
    int  onInputState(int, int, int, int);
}

# Keep SaveManager native methods
-keep class com.ludere.legacy.saves.SaveManager {
    native <methods>;
}

# Keep all payload config fields (accessed by reflection via ConfigParser)
-keep class com.ludere.legacy.PayloadConfig { *; }
