package android.content
open class Context { fun <T> getSystemService(c: Class<T>): T = throw UnsupportedOperationException(); val packageManager: android.content.pm.PackageManager get() = throw UnsupportedOperationException()
  fun startActivity(i: Intent) {} }
class Intent(var action: String? = null, uri: android.net.Uri? = null) { constructor(a: String) : this(a, null); fun addFlags(f: Int): Intent = this; fun putExtra(k: String, v: Int): Intent = this; fun putExtra(k: String, v: Boolean): Intent = this; fun putExtra(k: String, v: String): Intent = this; fun setPackage(p: String): Intent = this; fun addCategory(c: String): Intent = this
  companion object { const val ACTION_VIEW = "v"; const val FLAG_ACTIVITY_NEW_TASK = 1; const val ACTION_MAIN = "m"; const val CATEGORY_LAUNCHER = "l" } }
