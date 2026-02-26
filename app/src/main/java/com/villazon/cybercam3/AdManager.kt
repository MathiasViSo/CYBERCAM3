package com.villazon.cybercam3

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class AdManager(private val context: Context) {

    private var rewardedAd: RewardedAd? = null
    // TU ID DE ANUNCIO REAL "INTERSTELLAR"
    private val adUnitId = "ca-app-pub-3940256099942544/5224354917" // ID DE PRUEBA OFICIAL
    var isAdLoaded = false
        private set

    fun loadRewardedAd() {
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(context, adUnitId, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.e("AdManager", "Error al cargar anuncio: ${adError.message}")
                rewardedAd = null
                isAdLoaded = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d("AdManager", "¡Anuncio cargado con éxito!")
                rewardedAd = ad
                isAdLoaded = true
                setAdCallbacks()
            }
        })
    }

    private fun setAdCallbacks() {
        rewardedAd?.fullScreenContentCallback = object: FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // El usuario cerró el anuncio, pre-cargamos el siguiente
                rewardedAd = null
                isAdLoaded = false
                loadRewardedAd()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdManager", "Fallo al mostrar anuncio: ${adError.message}")
                rewardedAd = null
            }
        }
    }

    // Llama a esto para mostrar el anuncio y dar los puntos
    fun showRewardedAd(activity: Activity, onRewardEarned: (Int) -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.show(activity) { rewardItem ->
                // ¡Vio el anuncio completo!
                val puntosGanados = 10 // Puntos otorgados por ver el anuncio
                Toast.makeText(activity, "¡Anuncio completado! +$puntosGanados puntos", Toast.LENGTH_SHORT).show()
                onRewardEarned(puntosGanados)
            }
        } else {
            Toast.makeText(activity, "El anuncio 'Interstellar' aún se está cargando. Intenta en unos segundos.", Toast.LENGTH_SHORT).show()
            loadRewardedAd() // Forzamos carga si falló antes
        }
    }
}