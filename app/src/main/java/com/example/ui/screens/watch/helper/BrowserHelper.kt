package com.example.ui.screens.watch.helper

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.widget.Toast

data class BrowserAppInfo(
    val packageName: String,
    val name: String,
    val icon: Drawable?
)

object BrowserHelper {
    const val BRAVE_PACKAGE = "com.brave.browser"

    fun getInstalledBrowsers(context: Context): List<BrowserAppInfo> {
        val packageManager = context.packageManager
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PackageManager.MATCH_ALL
        } else {
            0
        }

        val resolveInfos = packageManager.queryIntentActivities(browserIntent, flags)
        val browsers = mutableListOf<BrowserAppInfo>()
        val seenPackages = mutableSetOf<String>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg != context.packageName && seenPackages.add(pkg)) {
                val label = info.loadLabel(packageManager)?.toString() ?: pkg
                val icon = info.loadIcon(packageManager)
                browsers.add(BrowserAppInfo(packageName = pkg, name = label, icon = icon))
            }
        }

        // Sort so popular browsers (Brave, Chrome, Firefox) appear first
        return browsers.sortedBy { browser ->
            when (browser.packageName) {
                BRAVE_PACKAGE -> 0
                "com.android.chrome" -> 1
                "org.mozilla.firefox" -> 2
                "com.microsoft.emmx" -> 3
                "com.opera.browser" -> 4
                "com.duckduckgo.mobile.android" -> 5
                "com.sec.android.app.sbrowser" -> 6
                else -> 10
            }
        }
    }

    fun isBraveInstalled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(BRAVE_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(BRAVE_PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openInBrowser(context: Context, url: String, packageName: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                if (!packageName.isNullOrBlank()) {
                    setPackage(packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openOrInstallBrave(context: Context, url: String) {
        if (isBraveInstalled(context)) {
            openInBrowser(context, url, BRAVE_PACKAGE)
        } else {
            try {
                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$BRAVE_PACKAGE")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(marketIntent)
            } catch (e: Exception) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$BRAVE_PACKAGE")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }

    fun copyToClipboard(context: Context, text: String, label: String = "Link") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun shareLink(context: Context, url: String, title: String? = null) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            if (!title.isNullOrBlank()) {
                putExtra(Intent.EXTRA_TITLE, title)
            }
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share link via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(shareIntent)
    }
}
