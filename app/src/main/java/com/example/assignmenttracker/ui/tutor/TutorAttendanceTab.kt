package com.example.assignmenttracker.ui.tutor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.AttendanceEntry
import com.example.assignmenttracker.ui.components.LmsCard

@Composable
fun TutorAttendanceTab(courseId: String, attendance: List<AttendanceEntry>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(attendance, key = { it.id }) { rec ->
            LmsCard {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(rec.studentName, fontWeight = FontWeight.Bold)
                        Text("Session Date: ${rec.date}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = rec.isPresent,
                        onCheckedChange = { checked ->
                            FirebaseService.db.collection("courses").document(courseId)
                                .collection("attendance").document(rec.id)
                                .update("isPresent", checked)
                        }
                    )
                }
            }
        }
    }
}