package com.pcl.chronoslock

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Ad-free stand-ins used by the F-Droid ("foss") build: no Google libraries, no ads, and no Power Pass limits.
 * Selected with the Gradle property -Pfoss (see app/build.gradle.kts).
 */
object Pass {
    const val FREE_WIDGETS = Int.MAX_VALUE
    const val FREE_STEPS = Int.MAX_VALUE
    val active: Boolean get() = true
    fun init(p: SharedPreferences) {}
    fun grant() {}
    fun endsAt(): String = ""
}

object Ads {
    const val enabled = false
    val ready = MutableStateFlow(false)
    val rewardReady = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val testMode: Boolean get() = false
    val devUnlocked: Boolean get() = false
    fun start(activity: Activity) {}
    fun unlockDev(on: Boolean) {}
    fun setTestMode(on: Boolean, ctx: Context) {}
    fun loadRewarded(ctx: Context) {}
    fun showReward(activity: Activity, onEarned: () -> Unit) { onEarned() }
}

@Composable
fun BannerAd() {}

@Composable
fun PassNudge(text: String, onGranted: () -> Unit) {}
