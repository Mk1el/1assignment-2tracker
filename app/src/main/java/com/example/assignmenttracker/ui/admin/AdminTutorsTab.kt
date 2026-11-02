package com.example.assignmenttracker.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.UserAccount
import com.example.assignmenttracker.ui.components.LmsCard

@Composable
fun AdminTutorsTab(
    tutors: List<UserAccount>,
    onAddTutor: () -> Unit = {}
) {
    val context = LocalContext.current

    if (tutors.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "No Tutors Registered",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Register tutors and link them to their respective courses.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Button(
                    onClick = onAddTutor,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Tutor")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tutors, key = { it.uid }) { tutor ->
                LmsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tutor.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Email: ${tutor.email}", style = MaterialTheme.typography.bodySmall)
                                Text("Staff ID: ${tutor.staffId}", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    "Course: ${tutor.assignedCourseTitle.ifBlank { "Unassigned" }}",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(if (tutor.isActive) "Active" else "Deactivated") },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = if (tutor.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    FirebaseService.setUserActiveStatus(tutor.uid, !tutor.isActive) {
                                        Toast.makeText(context, "Status updated for ${tutor.fullName}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tutor.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                )
                            ) {
                                Text(if (tutor.isActive) "Deactivate" else "Activate")
                            }

                            OutlinedButton(onClick = {
                                FirebaseService.auth.sendPasswordResetEmail(tutor.email)
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Password setup link sent to ${tutor.email}", Toast.LENGTH_LONG).show()
                                    }
                            }) {
                                Text("Resend Credentials")
                            }
                        }
                    }
                }
            }
        }
    }
}