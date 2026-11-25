package android.content.pm
class ResolveInfo { val activityInfo: ActivityInfo = ActivityInfo(); fun loadLabel(pm: PackageManager): CharSequence = "x" }
class ActivityInfo { val packageName = "p" }
class PackageManager { fun queryIntentActivities(i: android.content.Intent, f: Int): List<ResolveInfo> = emptyList(); fun getLaunchIntentForPackage(p: String): android.content.Intent? = null }
