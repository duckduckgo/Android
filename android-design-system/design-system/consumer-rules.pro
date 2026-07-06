# Consumer ProGuard rules for module: design-system
# Keep everything in this module to prevent R8 obfuscation issues
# This file is automatically applied when building release variants

# Keep all classes, interfaces, enums and their members in the design-system module
-keep class com.duckduckgo.mobile.android.** { *; }

# ViewBinding is resolved reflectively by this module's ActivityViewBindingDelegate /
# FragmentViewBindingDelegate (bindingClass.getMethod("inflate"/"bind", ...)). Without
# this, generated *Binding.inflate/bind get renamed and `by viewBinding()` throws
# NoSuchMethodException at Activity/Fragment/View construction. Lives here because this
# module owns the reflective delegate; applies app-wide to every ViewBinding consumer.
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static ** inflate(android.view.LayoutInflater);
    public static ** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static ** bind(android.view.View);
}

# This module pulls in the Compose lint checks via `api lintChecks(compose-lint-checks)`,
# which leaks slack.lint.compose.* (referencing com.android.tools.lint.*) onto the R8
# input classpath. Those classes are lint-time only and never present at runtime.
-dontwarn com.android.tools.lint.client.api.IssueRegistry
-dontwarn com.android.tools.lint.client.api.Vendor
-dontwarn com.android.tools.lint.detector.api.BooleanOption
-dontwarn com.android.tools.lint.detector.api.Category
-dontwarn com.android.tools.lint.detector.api.Detector
-dontwarn com.android.tools.lint.detector.api.Implementation
-dontwarn com.android.tools.lint.detector.api.Issue$Companion
-dontwarn com.android.tools.lint.detector.api.Issue
-dontwarn com.android.tools.lint.detector.api.Option
-dontwarn com.android.tools.lint.detector.api.Scope
-dontwarn com.android.tools.lint.detector.api.Severity
-dontwarn com.android.tools.lint.detector.api.SourceCodeScanner
-dontwarn com.android.tools.lint.detector.api.StringOption
