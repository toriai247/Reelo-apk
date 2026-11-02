package com.example.ui.screens.watch.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.screens.watch.helper.BrowserHelper
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow

@Composable
fun BraveRecommendationDialog(
    originalUrl: String,
    onDismiss: () -> Unit,
    onDontShowAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isBraveInstalled = remember { BrowserHelper.isBraveInstalled(context) }
    var dontShowChecked by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        if (dontShowChecked) onDontShowAgain()
        onDismiss()
    }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = ReeloYellow,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ReeloBlack,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(onClick = {
                        if (dontShowChecked) onDontShowAgain()
                        onDismiss()
                    }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Fast & Ad-Free Viewing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Use Brave browser for fast & ad-free viewing. Third-party streaming servers contain aggressive redirects that Brave blocks natively.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Action Button
                Button(
                    onClick = {
                        if (dontShowChecked) onDontShowAgain()
                        BrowserHelper.openOrInstallBrave(context, originalUrl)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReeloYellow,
                        contentColor = ReeloBlack
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isBraveInstalled) "Open in Brave" else "Get Brave",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // "Don't show again" Checkbox Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = dontShowChecked,
                        onCheckedChange = { dontShowChecked = it }
                    )
                    Text(
                        text = "Don't show again",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
