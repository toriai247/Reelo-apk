package com.example.ui.screens.watch.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.watch.helper.BrowserHelper
import com.example.ui.screens.watch.helper.DnsHelper
import com.example.ui.screens.watch.helper.PrivateDnsStatus
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow
import com.example.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureDnsGuideSheet(
    dnsStatus: PrivateDnsStatus,
    onDismiss: () -> Unit,
    onReloadStream: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
                .navigationBarsPadding()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = ReeloYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Secure DNS Setup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Real-Time Status Banner
            Surface(
                color = if (dnsStatus.isGoogleDns) Color(0xFF14301A) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                border = if (dnsStatus.isGoogleDns) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SuccessGreen)) else null,
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
                        Icon(
                            imageVector = if (dnsStatus.isGoogleDns) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (dnsStatus.isGoogleDns) SuccessGreen else ReeloYellow,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (dnsStatus.isGoogleDns) {
                                    "Secure DNS ON (dns.google) ✓"
                                } else if (dnsStatus.isActive) {
                                    "Private DNS Active (${dnsStatus.serverName ?: "Custom"})"
                                } else {
                                    "Private DNS is currently OFF"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (dnsStatus.isGoogleDns) SuccessGreen else MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                            Text(
                                text = if (dnsStatus.isGoogleDns) "Stream connection encrypted via Google DNS" else "Setup dns.google to bypass DNS blocks & ads",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (dnsStatus.isGoogleDns) {
                        Button(
                            onClick = {
                                onReloadStream()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen, contentColor = Color.White),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Reload", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { BrowserHelper.copyToClipboard(context, DnsHelper.GOOGLE_DNS_HOSTNAME, "DNS Hostname") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReeloYellow,
                        contentColor = ReeloBlack
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy dns.google", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { DnsHelper.openNetworkSettings(context) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Settings")
                }
            }

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Step by Step Setup Instructions
            Text(
                text = "How to enable Private DNS on Android:",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StepItem(step = "1", text = "Tap 'Copy dns.google' above to copy the hostname.")
                StepItem(step = "2", text = "Tap 'Open Settings' to open Android Network Settings.")
                StepItem(step = "3", text = "Look for 'Private DNS' setting.")
                
                // Brand specific paths
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Navigation paths vary by phone brand:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ReeloYellow)
                        Text("• Pixel / Stock: Settings > Network & internet > Private DNS", fontSize = 11.sp)
                        Text("• Samsung: Settings > Connections > More connection settings > Private DNS", fontSize = 11.sp)
                        Text("• Xiaomi / MIUI: Settings > Connection & sharing > Private DNS", fontSize = 11.sp)
                        Text("• OnePlus / Oppo: Settings > Connection & sharing > Private DNS", fontSize = 11.sp)
                    }
                }

                StepItem(step = "4", text = "Select 'Private DNS provider hostname', paste dns.google, and tap Save.")
                StepItem(step = "5", text = "Return to Reelo — the status will update to 'Secure DNS ON' automatically.")
            }
        }
    }
}

@Composable
private fun StepItem(step: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = ReeloYellow,
            modifier = Modifier.size(22.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = step, color = ReeloBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, fontSize = 13.sp)
    }
}
