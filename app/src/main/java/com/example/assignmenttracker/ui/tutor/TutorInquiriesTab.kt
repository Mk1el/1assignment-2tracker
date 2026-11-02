package com.example.assignmenttracker.ui.tutor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.assignmenttracker.model.StudentInquiry
import com.example.assignmenttracker.ui.components.LmsCard

@Composable
fun TutorInquiriesTab(
    inquiries: List<StudentInquiry>,
    onReply: (StudentInquiry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(inquiries, key = { it.id }) { inq ->
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(inq.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("From: ${inq.studentName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(inq.message, style = MaterialTheme.typography.bodyMedium)

                    if (inq.reply.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Tutor Response: ${inq.reply}",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { onReply(inq) }) {
                        Text(if (inq.isResolved) "Edit Response" else "Reply")
                    }
                }
            }
        }
    }
}