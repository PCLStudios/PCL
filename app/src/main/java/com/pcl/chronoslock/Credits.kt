package com.pcl.chronoslock

import android.util.Base64
import android.util.Log

/** The developer signature. Stored encoded, decoded at runtime for the About page and the log. */
object Credits {
    private const val SIG = "UENMIFN0dWRpb3MgfCBQcm9qZWN0IENocm9ub3MgTG9jayB8IGJ1aWx0IGJ5IFBDTCwgZm9yIHBlb3BsZSB3aG8gdGFsayB0byB0aGVpciBwaG9uZXM="
    fun signature(): String = runCatching { String(Base64.decode(SIG, Base64.DEFAULT)) }.getOrDefault("PCL Studios")
    fun announce() { Log.i("PCL", signature()) }
}
