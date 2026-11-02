package com.example.ui.screens.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReportSourceDialog(
    sourceLabel: String,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String?) -> Unit
) {
    val reasons = listOf(
        "broken" to "Broken Video / Link",
        "wrong_episode" to "Wrong Audio / Episode",
        "copyright" to "DMCA / Copyright",
        "buffering" to "Buffering / Freeze",
        "other" to "Other Issue"
    )

    var selectedReason by remember { mutableStateOf("broken") }
    var detailsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Report Problem",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Server: $sourceLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Select reason:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                // Reason Filter Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reasons.forEach { (key, label) ->
                        val isSelected = selectedReason == key
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedReason = key },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ReeloYellow,
                                selectedLabelColor = ReeloBlack
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = detailsText,
                    onValueChange = { if (it.length <= 1000) detailsText = it },
                    label = { Text("Details (optional)") },
                    placeholder = { Text("Describe what happened...") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ReeloYellow,
                        cursorColor = ReeloYellow
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(selectedReason, detailsText.trim().ifBlank { null })
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReeloYellow,
                    contentColor = ReeloBlack
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
