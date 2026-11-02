package com.example.ui.screens.watch.dialogs

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.ui.screens.watch.helper.BrowserAppInfo
import com.example.ui.screens.watch.helper.BrowserHelper
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenInBrowserSheet(
    originalUrl: String,
    currentUrl: String?,
    onDismiss: () -> Unit,
    onOpenSecureDnsGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedBrowsers = remember { BrowserHelper.getInstalledBrowsers(context) }
    val isBraveInstalled = remember { BrowserHelper.isBraveInstalled(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sheet Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = ReeloYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Open in Browser",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // 1. Original Source Link Card (Truncated)
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "ORIGINAL SOURCE LINK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = ReeloYellow
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = originalUrl.ifBlank { "No stream URL found" },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Recommended Browser Card (Brave)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ReeloYellow,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = ReeloBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Brave Browser",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Use Brave browser for fast & ad-free viewing",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            BrowserHelper.openOrInstallBrave(context, originalUrl)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReeloYellow,
                            contentColor = ReeloBlack
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (isBraveInstalled) "Open" else "Get Brave",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // 3. Installed Browsers
            if (installedBrowsers.isNotEmpty()) {
                Text(
                    text = "Installed Browsers on Phone",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(installedBrowsers, key = { it.packageName }) { browser ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(74.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    BrowserHelper.openInBrowser(context, originalUrl, browser.packageName)
                                    onDismiss()
                                }
                                .padding(vertical = 6.dp)
                        ) {
                            if (browser.icon != null) {
                                val bitmap = remember(browser.packageName) {
                                    runCatching { browser.icon.toBitmap(96, 96).asImageBitmap() }.getOrNull()
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = browser.name,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                    )
                                } else {
                                    DefaultBrowserIcon()
                                }
                            } else {
                                DefaultBrowserIcon()
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = browser.name,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // 4. Actions: Copy Original URL, Copy Current Page URL, Share Link
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Copy Original Source URL
                ListItem(
                    headlineContent = { Text("Copy Original Stream Link", fontSize = 14.sp) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = ReeloYellow)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            BrowserHelper.copyToClipboard(context, originalUrl, "Original URL")
                            onDismiss()
                        }
                )

                // Copy Current Page URL (if redirected)
                val cur = currentUrl
                if (!cur.isNullOrBlank() && cur != originalUrl) {
                    ListItem(
                        headlineContent = { Text("Copy Current Page URL", fontSize = 14.sp) },
                        supportingContent = { Text(cur, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        leadingContent = {
                            Icon(imageVector = Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                BrowserHelper.copyToClipboard(context, cur, "Current Page URL")
                                onDismiss()
                            }
                    )
                }

                // Share link
                ListItem(
                    headlineContent = { Text("Share Link", fontSize = 14.sp) },
                    leadingContent = {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = ReeloYellow)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            BrowserHelper.shareLink(context, originalUrl, "Watch stream")
                            onDismiss()
                        }
                )
            }

            // 5. Watch here with Secure DNS
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDismiss()
                        onOpenSecureDnsGuide()
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Dns,
                            contentDescription = null,
                            tint = ReeloYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Watch here with Secure DNS",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Configure Android Private DNS (dns.google) to block ads & ISP limits",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultBrowserIcon() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Language,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
