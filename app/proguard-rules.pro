# Room Entities & DAOs
-keep class g.p.cbb.data.entity.** { *; }
-keep class g.p.cbb.data.model.** { *; }
-keep interface g.p.cbb.data.dao.** { *; }

# Gson SerializedName fields
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Google API Services & HTTP Client
-keep class com.google.api.services.** { *; }
-keep class com.google.api.client.** { *; }

-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
-dontwarn org.apache.http.**
-dontwarn com.google.api.client.**
