package com.example.ui.screens.watch.helper

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

data class PrivateDnsStatus(
    val isActive: Boolean,
    val serverName: String?,
    val isGoogleDns: Boolean
)

object DnsHelper {
    const val GOOGLE_DNS_HOSTNAME = "dns.google"

    fun getPrivateDnsStatus(context: Context): PrivateDnsStatus {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return PrivateDnsStatus(isActive = false, serverName = null, isGoogleDns = false)
        }

        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            val linkProperties = activeNetwork?.let { cm.getLinkProperties(it) }

            if (linkProperties != null) {
                val isDnsActive = linkProperties.isPrivateDnsActive
                val serverName = linkProperties.privateDnsServerName
                val isGoogle = isDnsActive && serverName?.equals(GOOGLE_DNS_HOSTNAME, ignoreCase = true) == true
                PrivateDnsStatus(isActive = isDnsActive, serverName = serverName, isGoogleDns = isGoogle)
            } else {
                PrivateDnsStatus(isActive = false, serverName = null, isGoogleDns = false)
            }
        } catch (e: Exception) {
            PrivateDnsStatus(isActive = false, serverName = null, isGoogleDns = false)
        }
    }

    fun openNetworkSettings(context: Context) {
        val intents = listOf(
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (e: Exception) {
                // Try next fallback
            }
        }

        Toast.makeText(context, "Could not open settings automatically. Please open Settings manually.", Toast.LENGTH_LONG).show()
    }
}
