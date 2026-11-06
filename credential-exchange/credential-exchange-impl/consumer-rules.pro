# providerevents loads its Play Services implementation by name from manifest metadata, so nothing in code keeps it from being shrunk.
-keep class androidx.credentials.providerevents.playservices.ProviderEventsApiProviderPlayServices { <init>(...); }
