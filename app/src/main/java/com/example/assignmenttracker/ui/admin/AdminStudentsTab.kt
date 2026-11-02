package com.example.assignmenttracker.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.Course
import com.example.assignmenttracker.model.CourseEnrollment
import com.example.assignmenttracker.model.UserAccount
import com.example.assignmenttracker.ui.components.LmsCard
import com.google.firebase.firestore.FieldValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminStudentsTab(
    students: List<UserAccount>,
    courses: List<Course>
) {
    val context = LocalContext.current
    var selectedSubSection by remember { mutableIntStateOf(0) }
    val sections = listOf("Approved Students", "Pending Approvals")

    var pendingEnrollments by remember { mutableStateOf<List<CourseEnrollment>>(emptyList()) }
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("") }
    var studentEmail by remember { mutableStateOf("") }
    var regNo by remember { mutableStateOf("") }
    var selectedCourse by remember { mutableStateOf<Course?>(null) }
    var isCourseMenuOpen by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var createdStudentCreds by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        FirebaseService.db.collection("course_enrollments")
            .whereEqualTo("status", "pending_admin_approval")
            .addSnapshotListener { s, _ ->
                pendingEnrollments = s?.documents?.mapNotNull { d ->
                    CourseEnrollment(
                        id = d.id,
                        studentId = d.getString("studentId") ?: "",
                        studentName = d.getString("studentName") ?: "",
                        studentEmail = d.getString("studentEmail") ?: "",
                        courseId = d.getString("courseId") ?: "",
                        courseTitle = d.getString("courseTitle") ?: "",
                        status = d.getString("status") ?: "pending_admin_approval"
                    )
                } ?: emptyList()
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Student Management", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${students.size} active | ${pendingEnrollments.size} awaiting approval", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Button(
                    onClick = {
                        studentName = ""
                        studentEmail = ""
                        regNo = ""
                        selectedCourse = courses.firstOrNull()
                        showAddStudentDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Student")
                }
            }
        }

        TabRow(selectedTabIndex = selectedSubSection) {
            sections.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedSubSection == idx,
                    onClick = { selectedSubSection = idx },
                    text = {
                        Text(
                            if (idx == 1 && pendingEnrollments.isNotEmpty()) "$title (${pendingEnrollments.size})" else title,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }

        when (selectedSubSection) {
            0 -> {
                if (students.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No registered students found.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(students, key = { it.uid }) { student ->
                            LmsCard {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(student.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                            Text("Email: ${student.email}", style = MaterialTheme.typography.bodySmall)
                                            if (student.studentRegNo.isNotBlank()) {
                                                Text("Reg No: ${student.studentRegNo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        AssistChip(
                                            onClick = {},
                                            label = { Text(if (student.isActive) "Active" else "Deactivated") },
                                            colors = AssistChipDefaults.assistChipColors(
                                                labelColor = if (student.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                FirebaseService.setUserActiveStatus(student.uid, !student.isActive) {
                                                    Toast.makeText(context, "Status updated for ${student.fullName}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (student.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                            ),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (student.isActive) "Deactivate" else "Activate")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                FirebaseService.auth.sendPasswordResetEmail(student.email)
                                                    .addOnSuccessListener {
                                                        Toast.makeText(context, "Credentials reset link sent to ${student.email}", Toast.LENGTH_LONG).show()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                                    }
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Resend Credentials")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> {
                if (pendingEnrollments.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No pending student applications to approve.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(pendingEnrollments, key = { it.id }) { pending ->
                            LmsCard {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(pending.studentName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text("Email: ${pending.studentEmail}", style = MaterialTheme.typography.bodySmall)
                                    Text("Requested Course: ${pending.courseTitle}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                val batch = FirebaseService.db.batch()
                                                val enrollRef = FirebaseService.db.collection("course_enrollments").document(pending.id)
                                                val userRef = FirebaseService.db.collection("users").document(pending.studentId)

                                                batch.update(enrollRef, mapOf(
                                                    "status" to "approved",
                                                    "verifiedBy" to "admin",
                                                    "verifiedAt" to FieldValue.serverTimestamp()
                                                ))
                                                batch.update(userRef, mapOf(
                                                    "isActive" to true
                                                ))

                                                val notifRef = FirebaseService.db.collection("users")
                                                    .document(pending.studentId)
                                                    .collection("notifications")
                                                    .document()

                                                batch.set(notifRef, mapOf(
                                                    "type" to "course_verified",
                                                    "title" to "Account & Course Approved",
                                                    "message" to "Congratulations! Your registration for ${pending.courseTitle} was approved by Admin.",
                                                    "read" to false,
                                                    "timestamp" to FieldValue.serverTimestamp()
                                                ))

                                                batch.commit().addOnSuccessListener {
                                                    FirebaseService.auth.sendPasswordResetEmail(pending.studentEmail)
                                                        .addOnCompleteListener {
                                                            Toast.makeText(
                                                                context,
                                                                "Approved! Credentials emailed to ${pending.studentEmail}",
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                        }
                                                }.addOnFailureListener {
                                                    Toast.makeText(context, "Approval error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Approve & Send Credentials")
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                FirebaseService.db.collection("course_enrollments").document(pending.id)
                                                    .update("status", "rejected")
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Reject")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStudentDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showAddStudentDialog = false },
            title = { Text("Direct Student Registration", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = studentName,
                        onValueChange = { studentName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = studentEmail,
                        onValueChange = { studentEmail = it },
                        label = { Text("Institutional Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = regNo,
                        onValueChange = { regNo = it },
                        label = { Text("Registration Number") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = isCourseMenuOpen,
                        onExpandedChange = { isCourseMenuOpen = !isCourseMenuOpen },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCourse?.let { "${it.code} - ${it.title}" } ?: "Assign Course (Optional)",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Enroll Into Course") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCourseMenuOpen) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = isCourseMenuOpen,
                            onDismissRequest = { isCourseMenuOpen = false }
                        ) {
                            courses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text("${course.code} - ${course.title}") },
                                    onClick = {
                                        selectedCourse = course
                                        isCourseMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting,
                    onClick = {
                        val name = studentName.trim()
                        val email = studentEmail.trim()
                        val reg = regNo.trim()
                        if (name.isBlank() || email.isBlank()) {
                            Toast.makeText(context, "Fill name and email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true
                        FirebaseService.registerUserSafe(
                            context = context,
                            name = name,
                            email = email,
                            role = "student",
                            identifier = reg,
                            course = selectedCourse,
                            onSuccess = { pwd ->
                                isSubmitting = false
                                showAddStudentDialog = false
                                createdStudentCreds = Pair(email, pwd)
                            },
                            onFailure = {
                                isSubmitting = false
                                Toast.makeText(context, it.localizedMessage, Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Register & Send Email")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddStudentDialog = false }, enabled = !isSubmitting) { Text("Cancel") }
            }
        )
    }

    createdStudentCreds?.let { (email, pwd) ->
        AlertDialog(
            onDismissRequest = { createdStudentCreds = null },
            title = { Text("Student Registered! 🎉", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The student account has been approved and credentials emailed.")
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Email: $email", fontSize = 14.sp)
                            Text("Temp Password: $pwd", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Student Creds", "Email: $email\nPassword: $pwd"))
                            Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Credentials")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { createdStudentCreds = null }) { Text("Done") }
            }
        )
    }
}