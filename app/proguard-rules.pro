# R8 / ProGuard rules for X11.
# Activities, services, the tile and the FileProvider are kept automatically through the manifest.

# The X server loads its bitmap fonts with XFont::class.java.getResourceAsStream("fonts/…"), a path relative to
# XFont's package: the class must keep its name and package or the fonts (and the display) won't load.
-keep class com.mnvstudio.x11.server.XFont
-keeppackagenames com.mnvstudio.x11.server

# Enum names are stored in settings and download history (valueOf by name).
-keepclassmembers enum com.mnvstudio.x11.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Readable crash reports in the app log.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
