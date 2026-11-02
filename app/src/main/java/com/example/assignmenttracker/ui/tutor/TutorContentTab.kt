package com.example.assignmenttracker.ui.tutor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.assignmenttracker.model.CourseContent
import com.example.assignmenttracker.ui.components.LmsCard

@Composable
fun TutorContentTab(contents: List<CourseContent>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(contents, key = { it.id }) { content ->
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${content.moduleWeek}: ${content.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(content.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                    if (content.resourceUrl.isNotBlank()) {
                        Text("Material Link: ${content.resourceUrl}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}