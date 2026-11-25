package android.os
class BatteryManager { fun getIntProperty(i: Int) = 50; val isCharging = false; companion object { const val BATTERY_PROPERTY_CAPACITY = 4 } }
object Build { const val MANUFACTURER = "M"; const val MODEL = "P"; object VERSION { const val RELEASE = "14" } }
object Environment { fun getDataDirectory() = java.io.File("/") }
class StatFs(p: String) { val availableBytes = 1L; val totalBytes = 2L }
object SystemClock { fun elapsedRealtime() = System.nanoTime() / 1000000 }
