package com.example.ui.screens.watch.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReeloYellow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShieldSettingsSheet(
    stayOnOriginalSite: Boolean,
    adCleanerEnabled: Boolean,
    popupGatedPlayers: Boolean,
    isShieldDisabledForCurrentHost: Boolean,
    currentHost: String?,
    blockedRedirectsCount: Int,
    onToggleStayOnOriginalSite: (Boolean) -> Unit,
    onToggleAdCleaner: (Boolean) -> Unit,
    onTogglePopupGatedPlayers: (Boolean) -> Unit,
    onToggleShieldForCurrentHost: () -> Unit,
    onResetBlockedCount: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ReeloYellow,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Shield & Ad Protection",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Stats Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "BLOCKED AD REDIRECTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ReeloYellow
                        )
                        Text(
                            text = "$blockedRedirectsCount attempts blocked this session",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (blockedRedirectsCount > 0) {
                        TextButton(onClick = onResetBlockedCount) {
                            Text("Reset", color = ReeloYellow)
                        }
                    }
                }
            }

            // 1. Stay on original site (Auto-Return)
            ListItem(
                headlineContent = { Text("Stay on original site", fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(
                        "Automatically blocks ad redirects and keeps stream on the player domain",
                        fontSize = 12.sp
                    )
                },
                trailingContent = {
                    Switch(
                        checked = stayOnOriginalSite,
                        onCheckedChange = onToggleStayOnOriginalSite,
                        colors = SwitchDefaults.colors(checkedThumbColor = ReeloYellow)
                    )
                }
            )

            // 2. Ad Overlay Cleaner
            ListItem(
                headlineContent = { Text("Ad overlay cleaner", fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(
                        "Automatically removes invisible click-traps & fake close overlays without breaking video",
                        fontSize = 12.sp
                    )
                },
                trailingContent = {
                    Switch(
                        checked = adCleanerEnabled,
                        onCheckedChange = onToggleAdCleaner,
                        colors = SwitchDefaults.colors(checkedThumbColor = ReeloYellow)
                    )
                }
            )

            // 3. Popup-gated players
            ListItem(
                headlineContent = { Text("Popup-gated players", fontWeight = FontWeight.SemiBold) },
                supportingContent = {
                    Text(
                        "For streams requiring a popup to unlock, loads popup invisibly in background and closes it after 2s",
                        fontSize = 12.sp
                    )
                },
                trailingContent = {
                    Switch(
                        checked = popupGatedPlayers,
                        onCheckedChange = onTogglePopupGatedPlayers,
                        colors = SwitchDefaults.colors(checkedThumbColor = ReeloYellow)
                    )
                }
            )

            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            // Per-site exception toggle
            if (!currentHost.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Player broken on $currentHost?",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = if (isShieldDisabledForCurrentHost) {
                                    "Protection currently PAUSED for this host"
                                } else {
                                    "Turn off auto-return & cleaner for this site"
                                },
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Button(
                            onClick = onToggleShieldForCurrentHost,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isShieldDisabledForCurrentHost) ReeloYellow else MaterialTheme.colorScheme.error,
                                contentColor = if (isShieldDisabledForCurrentHost) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(
                                text = if (isShieldDisabledForCurrentHost) "Resume" else "Disable",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
