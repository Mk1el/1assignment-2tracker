package com.example.assignmenttracker.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.Course
import com.example.assignmenttracker.ui.components.LmsCard

@Composable
fun AdminCoursesTab(
    courses: List<Course>,
    onAddCourse: () -> Unit,
    onEditCourse: (Course) -> Unit
) {
    val context = LocalContext.current

    if (courses.isEmpty()) {
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
                    text = "No Courses Added Yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Get started by adding courses offered by your institution.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
                Button(
                    onClick = onAddCourse,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add First Course")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(courses, key = { it.id }) { course ->
                LmsCard {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${course.code} - ${course.title}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text("Department: ${course.department}", style = MaterialTheme.typography.bodySmall)
                            if (course.description.isNotBlank()) {
                                Text(course.description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                        Row {
                            IconButton(onClick = { onEditCourse(course) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                FirebaseService.deleteCourse(course.id,
                                    onSuccess = { Toast.makeText(context, "Course removed", Toast.LENGTH_SHORT).show() },
                                    onFailure = { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show() }
                                )
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}