package com.example.assignmenttracker.ui.admin

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.Course
import com.example.assignmenttracker.model.UserAccount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHodScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Courses", "Tutors", "Students")

    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var tutors by remember { mutableStateOf<List<UserAccount>>(emptyList()) }
    var students by remember { mutableStateOf<List<UserAccount>>(emptyList()) }

    var showCourseDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var courseCode by remember { mutableStateOf("") }
    var courseTitle by remember { mutableStateOf("") }
    var courseDept by remember { mutableStateOf("") }
    var courseDesc by remember { mutableStateOf("") }

    var showTutorDialog by remember { mutableStateOf(false) }
    var tutorName by remember { mutableStateOf("") }
    var tutorEmail by remember { mutableStateOf("") }
    var tutorStaffId by remember { mutableStateOf("") }
    var selectedCourseForTutor by remember { mutableStateOf<Course?>(null) }
    var isCourseMenuExpanded by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    var createdTutorCredentials by remember { mutableStateOf<Pair<String, String>?>(null) }

    DisposableEffect(Unit) {
        val courseListener = FirebaseService.listenToCourses { courses = it }
        val tutorListener = FirebaseService.listenToUsersByRole("tutor") { tutors = it }
        val studentListener = FirebaseService.listenToUsersByRole("student") { students = it }
        onDispose {
            courseListener.remove()
            tutorListener.remove()
            studentListener.remove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin & HOD Governance", fontWeight = FontWeight.Bold) },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = {
                            editingCourse = null
                            courseCode = ""; courseTitle = ""; courseDept = ""; courseDesc = ""
                            showCourseDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Course", tint = MaterialTheme.colorScheme.primary)
                        }
                    } else if (selectedTab == 1) {
                        IconButton(onClick = {
                            tutorName = ""; tutorEmail = ""; tutorStaffId = ""
                            selectedCourseForTutor = courses.firstOrNull()
                            showTutorDialog = true
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Tutor", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    IconButton(onClick = {
                        FirebaseService.auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingCourse = null
                        courseCode = ""; courseTitle = ""; courseDept = ""; courseDesc = ""
                        showCourseDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Course", fontWeight = FontWeight.Bold) }
                )
            } else if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        tutorName = ""; tutorEmail = ""; tutorStaffId = ""
                        selectedCourseForTutor = courses.firstOrNull()
                        showTutorDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Tutor", fontWeight = FontWeight.Bold) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (selectedTab) {
                0 -> AdminCoursesTab(
                    courses = courses,
                    onAddCourse = {
                        editingCourse = null
                        courseCode = ""; courseTitle = ""; courseDept = ""; courseDesc = ""
                        showCourseDialog = true
                    },
                    onEditCourse = {
                        editingCourse = it
                        courseCode = it.code
                        courseTitle = it.title
                        courseDept = it.department
                        courseDesc = it.description
                        showCourseDialog = true
                    }
                )
                1 -> AdminTutorsTab(
                    tutors = tutors,
                    onAddTutor = {
                        tutorName = ""; tutorEmail = ""; tutorStaffId = ""
                        selectedCourseForTutor = courses.firstOrNull()
                        showTutorDialog = true
                    }
                )
                2 -> AdminStudentsTab(students = students, courses = courses)
            }
        }
    }

    if (showCourseDialog) {
        AlertDialog(
            onDismissRequest = { showCourseDialog = false },
            title = { Text(if (editingCourse == null) "Add New Course" else "Edit Course", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = courseCode,
                        onValueChange = { courseCode = it },
                        label = { Text("Course Code (e.g. CS101)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = courseTitle,
                        onValueChange = { courseTitle = it },
                        label = { Text("Course Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = courseDept,
                        onValueChange = { courseDept = it },
                        label = { Text("Department") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = courseDesc,
                        onValueChange = { courseDesc = it },
                        label = { Text("Description") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (courseCode.isBlank() || courseTitle.isBlank()) {
                        Toast.makeText(context, "Code and Title are required", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val course = Course(
                        id = editingCourse?.id ?: "",
                        code = courseCode,
                        title = courseTitle,
                        department = courseDept,
                        description = courseDesc
                    )
                    FirebaseService.upsertCourse(course,
                        onSuccess = {
                            showCourseDialog = false
                            Toast.makeText(context, "Course saved successfully!", Toast.LENGTH_SHORT).show()
                        },
                        onFailure = { Toast.makeText(context, it.localizedMessage, Toast.LENGTH_SHORT).show() }
                    )
                }) { Text("Save Course") }
            },
            dismissButton = {
                TextButton(onClick = { showCourseDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTutorDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSubmitting) showTutorDialog = false },
            title = { Text("Register Tutor & Assign Course", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = tutorName,
                        onValueChange = { tutorName = it },
                        label = { Text("Full Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tutorEmail,
                        onValueChange = { tutorEmail = it },
                        label = { Text("Institutional Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tutorStaffId,
                        onValueChange = { tutorStaffId = it },
                        label = { Text("Staff / Payroll ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (courses.isEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⚠️ Please add at least one course first before registering a tutor.",
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = isCourseMenuExpanded,
                            onExpandedChange = { isCourseMenuExpanded = !isCourseMenuExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedCourseForTutor?.let { "${it.code} - ${it.title}" } ?: "Select Course to Assign",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Assign Primary Course") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCourseMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isCourseMenuExpanded,
                                onDismissRequest = { isCourseMenuExpanded = false }
                            ) {
                                courses.forEach { course ->
                                    DropdownMenuItem(
                                        text = { Text("${course.code} - ${course.title}") },
                                        onClick = {
                                            selectedCourseForTutor = course
                                            isCourseMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmitting && courses.isNotEmpty(),
                    onClick = {
                        val course = selectedCourseForTutor
                        val targetEmail = tutorEmail.trim()
                        if (tutorName.isBlank() || targetEmail.isBlank() || tutorStaffId.isBlank() || course == null) {
                            Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmitting = true

                        // Using registerUserSafe which handles role="tutor" and isolated secondary Firebase auth
                        FirebaseService.registerUserSafe(
                            context = context,
                            name = tutorName.trim(),
                            email = targetEmail,
                            role = "tutor",
                            identifier = tutorStaffId.trim(),
                            course = course,
                            onSuccess = { generatedPassword ->
                                isSubmitting = false
                                showTutorDialog = false
                                createdTutorCredentials = Pair(targetEmail, generatedPassword)
                            },
                            onFailure = {
                                isSubmitting = false
                                Toast.makeText(context, "Error: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                ) {
                    if (isSubmitting) CircularProgressIndicator(modifier = Modifier.size(20.dp)) else Text("Register Tutor")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTutorDialog = false }, enabled = !isSubmitting) { Text("Cancel") }
            }
        )
    }

    createdTutorCredentials?.let { (tutorCredEmail, tempPassword) ->
        AlertDialog(
            onDismissRequest = { createdTutorCredentials = null },
            shape = RoundedCornerShape(20.dp),
            title = { Text("Tutor Registered! 🎉", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("The account was created in Firebase. A password setup link was dispatched to $tutorCredEmail (check your **Spam / Junk** folder).")

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Direct Login Credentials:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Email: $tutorCredEmail", fontSize = 14.sp)
                            Text("Temp Password: $tempPassword", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Tutor Credentials", "Email: $tutorCredEmail\nPassword: $tempPassword")
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Credentials copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Credentials")
                    }
                }
            },
            confirmButton = {
                Button(onClick = { createdTutorCredentials = null }) {
                    Text("Done")
                }
            }
        )
    }
}