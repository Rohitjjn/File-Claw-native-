# Apache POI
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Coil
-keep class coil.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

# PDFBox
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn org.apache.pdfbox.**

# FastExcel
-keep class org.dhatim.fastexcel.** { *; }
-dontwarn org.dhatim.fastexcel.**

# Zip4j
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# Apache Commons Compress & XZ
-keep class org.apache.commons.compress.** { *; }
-keep class org.tukaani.xz.** { *; }
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**

# Markdown
-keep class org.intellij.markdown.** { *; }
-dontwarn org.intellij.markdown.**

# Coroutines and WorkManager
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# Missing optional dependencies for PDFBox and XML Parsing
-dontwarn com.gemalto.jp2.**
-dontwarn javax.xml.stream.**
-dontwarn org.codehaus.stax2.**
-dontwarn com.fasterxml.aalto.**
