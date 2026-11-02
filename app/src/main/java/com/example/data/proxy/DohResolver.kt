package com.example.data.proxy

import android.util.Log
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class DohResolver : Dns {

    private val tag = "DohResolver"
    private val cache = ConcurrentHashMap<String, CachedDnsRecord>()
    private val ttlMs = 60_000L // Minimum 60s TTL cache

    private data class CachedDnsRecord(
        val addresses: List<InetAddress>,
        val timestamp: Long
    )

    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    // Primary DoH: Cloudflare (bootstrap IPs: 1.1.1.1, 1.0.0.1)
    private val cloudflareDns: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("1.1.1.1"),
                InetAddress.getByName("1.0.0.1")
            )
            .includeIPv6(false)
            .build()
    }

    // Fallback DoH: Google (bootstrap IPs: 8.8.8.8, 8.8.4.4)
    private val googleDns: DnsOverHttps by lazy {
        DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url("https://dns.google/dns-query".toHttpUrl())
            .bootstrapDnsHosts(
                InetAddress.getByName("8.8.8.8"),
                InetAddress.getByName("8.8.4.4")
            )
            .includeIPv6(false)
            .build()
    }

    override fun lookup(hostname: String): List<InetAddress> {
        val cleanHost = hostname.trim()
        if (cleanHost.isBlank()) return emptyList()

        // Direct return if host is already an IP address
        if (isIpAddress(cleanHost)) {
            return runCatching { listOf(InetAddress.getByName(cleanHost)) }.getOrDefault(emptyList())
        }

        // 1. Check in-memory TTL cache
        val now = System.currentTimeMillis()
        val cached = cache[cleanHost]
        if (cached != null && (now - cached.timestamp) < ttlMs) {
            return cached.addresses
        }

        // 2. Try Cloudflare DoH
        var addresses = runCatching { cloudflareDns.lookup(cleanHost) }.getOrNull()

        // 3. Fallback to Google DoH if Cloudflare fails
        if (addresses.isNullOrEmpty()) {
            addresses = runCatching { googleDns.lookup(cleanHost) }.getOrNull()
        }

        // 4. Final fallback to System DNS
        if (addresses.isNullOrEmpty()) {
            addresses = runCatching { Dns.SYSTEM.lookup(cleanHost) }.getOrNull()
        }

        val result = addresses ?: emptyList()

        // Prioritize IPv4 addresses
        val sortedList = result.sortedByDescending { it is Inet4Address }

        if (sortedList.isNotEmpty()) {
            cache[cleanHost] = CachedDnsRecord(sortedList, now)
        }

        return sortedList
    }

    private fun isIpAddress(host: String): Boolean {
        return host.matches(Regex("""^(\d{1,3}\.){3}\d{1,3}$""")) || host.contains(":")
    }
}
