package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RatingGold
import com.example.ui.theme.ReeloBlack
import com.example.ui.theme.ReeloYellow

@Composable
fun RatingDialog(
    initialRating: Int = 8,
    onDismiss: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    var selectedRating by remember { mutableStateOf(initialRating.coerceIn(1, 10)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Rate this Series",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "$selectedRating / 10",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = ReeloYellow
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2 rows of 5 stars
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 1..5) {
                            Icon(
                                imageVector = if (i <= selectedRating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= selectedRating) RatingGold else Color.Gray,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedRating = i }
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (i in 6..10) {
                            Icon(
                                imageVector = if (i <= selectedRating) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = "Star $i",
                                tint = if (i <= selectedRating) RatingGold else Color.Gray,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clickable { selectedRating = i }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = selectedRating.toFloat(),
                    onValueChange = { selectedRating = it.toInt() },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = ReeloYellow,
                        activeTrackColor = ReeloYellow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedRating) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ReeloYellow,
                    contentColor = ReeloBlack
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("submit_rating_button")
            ) {
                Text("Submit Rating", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
