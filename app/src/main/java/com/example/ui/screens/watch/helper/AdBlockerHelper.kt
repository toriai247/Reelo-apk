package com.example.ui.screens.watch.helper

import android.content.Context
import android.util.Log
import android.webkit.WebResourceResponse
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

object AdBlockerHelper {
    private const val TAG = "ReeloAds"
    private val blockedDomains = mutableSetOf<String>()
    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        synchronized(blockedDomains) {
            if (isLoaded) return
            try {
                context.assets.open("ad_blocklist.txt").use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream)).useLines { lines ->
                        lines.map { it.trim() }
                            .filter { it.isNotEmpty() && !it.startsWith("#") }
                            .forEach { blockedDomains.add(it.lowercase()) }
                    }
                }
                Log.d(TAG, "Loaded ${blockedDomains.size} blocked domains from assets")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load ad_blocklist.txt: ${e.message}")
            }
            isLoaded = true
        }
    }

    fun isAdHost(host: String?): Boolean {
        if (host.isNullOrBlank()) return false
        val cleanHost = host.lowercase().trim()
        if (blockedDomains.contains(cleanHost)) return true

        // Subdomain matching (e.g. static.doubleclick.net endsWith .doubleclick.net)
        for (domain in blockedDomains) {
            if (cleanHost.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
