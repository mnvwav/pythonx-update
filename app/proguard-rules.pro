# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-keep,allowobfuscation class android.** { *; }
-keepnames class android.** { *; }

-keep,allowobfuscation class androidx.** { *; }
-keepnames class androidx.** { *; }

-keep,allowobfuscation class com.** { *; }
-keepnames class com.** { *; }

-keep,allowobfuscation class io.** { *; }
-keepnames class io.** { *; }

-keep,allowobfuscation class javax.** { *; }
-keepnames class javax.** { *; }

-keep,allowobfuscation class kotlin.** { *; }
-keepnames class kotlin.** { *; }

-keep,allowobfuscation class kotlinx.** { *; }
-keepnames class kotlinx.** { *; }

-keep,allowobfuscation class org.checkerframework.** { *; }
-keepnames class org.checkerframework.** { *; }

-keep,allowobfuscation class org.eclipse.** { *; }
-keepnames class org.eclipse.** { *; }

-keep,allowobfuscation class org.intellij.** { *; }
-keepnames class org.intellij.** { *; }

-keep,allowobfuscation class org.jcodings.** { *; }
-keepnames class org.jcodings.** { *; }

-keep,allowobfuscation class org.jetbrains.** { *; }
-keepnames class org.jetbrains.** { *; }

-keep,allowobfuscation class org.joni.** { *; }
-keepnames class org.joni.** { *; }

-keep,allowobfuscation class org.yaml.** { *; }
-keepnames class org.yaml.** { *; }


-optimizationpasses 700
-allowaccessmodification
-mergeinterfacesaggressively
-flattenpackagehierarchy
-dontpreverify
-keepattributes *Annotation*

# Other required classes for Google Play Services
# Read more at http://developer.android.com/google/play-services/setup.html
-keep class * extends java.util.ListResourceBundle {
   protected Object[][] getContents();
}

-keep public class com.google.android.gms.common.internal.safeparcel.SafeParcelable {
   public static final *** NULL;
}

-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keepclassmembernames class * {
   @com.google.android.gms.common.annotation.KeepName *;
}

-keepnames class * implements android.os.Parcelable {
   public static final ** CREATOR;
}

#-keep public class org.softbuddy.pythonx.** {
#    public *;
# }
# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.FeatureDescriptor
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.reflect.AnnotatedType