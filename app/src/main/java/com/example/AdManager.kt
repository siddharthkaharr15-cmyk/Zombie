package com.example

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    private const val REWARDED_AD_UNIT_ID = "ca-app-pub-2441058946302843/6998515589" // Real ID

    private var rewardedAd: RewardedAd? = null
    var isAdLoading = false

    private var retryAttempt = 0

    fun loadRewardedAd(context: Context) {
        if (rewardedAd != null || isAdLoading) {
            return
        }
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            context,
            REWARDED_AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Ad failed to load: ${adError.message}. Retrying...")
                    rewardedAd = null
                    isAdLoading = false
                    retryAttempt++
                    val delayMs = (2000L * retryAttempt)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        loadRewardedAd(context)
                    }, delayMs)
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "Ad was loaded.")
                    rewardedAd = ad
                    isAdLoading = false
                    retryAttempt = 0
                }
            }
        )
    }

    fun showRewardedAd(activity: Activity, onRewarded: () -> Unit, onAdDismissed: () -> Unit) {
        if (rewardedAd != null) {
            var rewardEarned = false
            
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad was dismissed.")
                    rewardedAd = null
                    loadRewardedAd(activity) // load next one
                    // Only dismiss the flow if the user did not earn the reward or we want to dismiss anyway
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show: ${adError.message}")
                    rewardedAd = null
                    android.widget.Toast.makeText(activity, "Ad not available. Please try again later.", android.widget.Toast.LENGTH_SHORT).show()
                    onAdDismissed()
                    loadRewardedAd(activity)
                }
                
                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed.")
                }
            }

            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned the reward: ${rewardItem.amount} ${rewardItem.type}")
                Log.d(TAG, "Revive/Reward granted.")
                rewardEarned = true
                onRewarded()
            }
        } else {
            if (isAdLoading) {
                Log.d(TAG, "The rewarded ad is still loading.")
                android.widget.Toast.makeText(activity, "Ad is loading, please wait...", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                Log.d(TAG, "The rewarded ad wasn't ready yet. Trying to load again.")
                android.widget.Toast.makeText(activity, "Ad not available. Please try again later.", android.widget.Toast.LENGTH_SHORT).show()
                loadRewardedAd(activity)
            }
            onAdDismissed()
        }
    }
}
