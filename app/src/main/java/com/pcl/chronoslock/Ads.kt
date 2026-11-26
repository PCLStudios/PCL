package com.pcl.chronoslock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val COFFEE_URL = "https://buymeacoffee.com/pclstudios"
const val GITHUB_URL = "https://github.com/PCLStudios"
const val TWITTER_URL = "https://x.com/pclglobal"

object AdConfig {
    const val BANNER_LIVE = "ca-app-pub-8535931150252475/6883327215"
    const val REWARDED_LIVE = "ca-app-pub-8535931150252475/9277943307"
    // Google's own sample ad units: always safe to tap, never earn money
    const val BANNER_TEST = "ca-app-pub-3940256099942544/6300978111"
    const val REWARDED_TEST = "ca-app-pub-3940256099942544/5224354917"
    fun banner(test: Boolean) = if (test) BANNER_TEST else BANNER_LIVE
    fun rewarded(test: Boolean) = if (test) REWARDED_TEST else REWARDED_LIVE
}

fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Power Pass: 24 hours of unlimited widgets and program steps, and no banner ads. Earned by watching one rewarded ad. */
object Pass {
    const val FREE_WIDGETS = 8
    const val FREE_STEPS = 6
    private const val DAY = 24L * 60 * 60 * 1000
    var until by mutableLongStateOf(0L)
        private set
    private var prefs: SharedPreferences? = null

    fun init(p: SharedPreferences) { prefs = p; until = p.getLong("pass_until", 0L) }
    val active: Boolean get() = System.currentTimeMillis() < until
    fun grant() {
        until = maxOf(until, System.currentTimeMillis()) + DAY
        prefs?.edit()?.putLong("pass_until", until)?.apply()
    }
    fun endsAt(): String = SimpleDateFormat("EEE h:mm a", Locale.US).format(Date(until))
}

/** AdMob: consent form first (required in the EU/UK), then banner + rewarded ads. Never touches the microphone side of the app. */
object Ads {
    val ready = MutableStateFlow(false)
    val rewardReady = MutableStateFlow(false)
    val message = MutableStateFlow("")
    /** Test ads are safe to tap while you try the app on your own phone. Live ads are the default for everyone else. */
    var testMode by mutableStateOf(false)
        private set

    var devUnlocked by mutableStateOf(false)
        private set
    fun unlockDev(on: Boolean) { devUnlocked = on; prefs?.edit()?.putBoolean("dev_unlocked", on)?.apply() }

    private var rewarded: RewardedAd? = null
    private var loading = false
    private var started = false
    private var inited = false
    private var prefs: SharedPreferences? = null

    fun start(activity: Activity) {
        if (started) return
        started = true
        prefs = activity.getSharedPreferences("pcl", Context.MODE_PRIVATE)
        testMode = prefs?.getBoolean("ads_test", false) ?: false
        devUnlocked = prefs?.getBoolean("dev_unlocked", false) ?: false
        runCatching {
            val info = UserMessagingPlatform.getConsentInformation(activity)
            info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> if (info.canRequestAds()) init(activity) }
            }, { _ -> if (info.canRequestAds()) init(activity) })
            if (info.canRequestAds()) init(activity)
        }
    }

    private fun init(ctx: Context) {
        if (inited) return
        inited = true
        MobileAds.initialize(ctx.applicationContext) { }
        ready.value = true
        loadRewarded(ctx.applicationContext)
    }

    fun setTestMode(on: Boolean, ctx: Context) {
        testMode = on
        prefs?.edit()?.putBoolean("ads_test", on)?.apply()
        rewarded = null; rewardReady.value = false; loading = false
        loadRewarded(ctx)
    }

    fun loadRewarded(ctx: Context) {
        if (!ready.value || loading || rewarded != null) return
        loading = true
        RewardedAd.load(ctx, AdConfig.rewarded(testMode), AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; loading = false; rewardReady.value = true; message.value = "" }
            override fun onAdFailedToLoad(e: LoadAdError) {
                rewarded = null; loading = false; rewardReady.value = false
                message.value = "No ad available right now. Try again in a minute."
            }
        })
    }

    fun showReward(activity: Activity, onEarned: () -> Unit) {
        val ad = rewarded
        if (ad == null) {
            message.value = if (ready.value) "No ad available yet. Try again in a few seconds." else "Ads are not ready yet."
            loadRewarded(activity); return
        }
        ChronosEngine.suspendMic(true)            // the mic must not fight the ad for audio
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { rewarded = null; rewardReady.value = false; ChronosEngine.suspendMic(false); loadRewarded(activity) }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                rewarded = null; rewardReady.value = false; ChronosEngine.suspendMic(false); message.value = "No ad available right now. Try again in a minute."; loadRewarded(activity)
            }
        }
        ad.show(activity) { onEarned(); message.value = "" }
    }
}

/** Small banner. Hidden while a Power Pass is active. Only used on Guide, Style and About, never on the listening screen. */
@Composable
fun BannerAd() {
    val ready by Ads.ready.collectAsState()
    if (!ready || Pass.active) return
    val test = Ads.testMode
    Spacer(Modifier.height(14.dp))
    Text2("SPONSORED · A POWER PASS HIDES ADS", C.Dim, 9, spacing = 2, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
    key(test) {
        Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { c -> AdView(c).apply { setAdSize(AdSize.BANNER); adUnitId = AdConfig.banner(test); loadAd(AdRequest.Builder().build()) } },
                onRelease = { it.destroy() }
            )
        }
    }
}

/** "Hit a free limit": offers the Power Pass. */
@Composable
fun PassNudge(text: String, onGranted: () -> Unit) {
    val act = LocalContext.current.findActivity()
    val msg by Ads.message.collectAsState()
    val ready by Ads.rewardReady.collectAsState()
    Panel(accent = C.Amber) {
        Text2("POWER PASS", C.Amber, 12, bold = true, spacing = 2)
        Spacer(Modifier.height(4.dp))
        Text2(text, C.Soft, 12, mono = false)
        Spacer(Modifier.height(8.dp))
        PillButton(if (ready) "WATCH AN AD · 24H POWER PASS" else "GET AN AD READY…", C.Amber, filled = true, modifier = Modifier.fillMaxWidth()) {
            act?.let { a -> Ads.showReward(a) { Pass.grant(); onGranted() } }
        }
        if (msg.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text2(msg, C.Red, 11, mono = false) }
    }
}
