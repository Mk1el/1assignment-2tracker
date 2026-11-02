package com.example.assignmenttracker

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.firestore

data class CourseItem(
    val id: String = "",
    val title: String = "",
    val code: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit = {},
    onBack: () -> Unit = {},
    onRegisterSuccess: (role: String) -> Unit = {}
) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    var availableCourses by remember { mutableStateOf<List<CourseItem>>(emptyList()) }
    var selectedCourse by remember { mutableStateOf<CourseItem?>(null) }
    var isCourseDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val registerImageUrl = "https://images.unsplash.com/photo-1523240795612-9a054b0db644?w=800&auto=format&fit=crop&q=80"

    val auth = remember {
        if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)
        Firebase.auth
    }
    val db = remember { Firebase.firestore }

    LaunchedEffect(Unit) {
        db.collection("courses")
            .get()
            .addOnSuccessListener { snapshot ->
                val courses = snapshot.documents.map { doc ->
                    CourseItem(
                        id = doc.id,
                        title = doc.getString("title") ?: doc.getString("name") ?: doc.id,
                        code = doc.getString("code") ?: ""
                    )
                }
                availableCourses = courses
                if (courses.isNotEmpty()) {
                    selectedCourse = courses.first()
                }
            }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Application Submitted! 📋",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                val courseName = selectedCourse?.title ?: "Selected Course"
                Text(
                    text = "Thank you, $fullName!\n\nYour application for '$courseName' has been submitted for administrative verification.\n\nOnce the Admin/HOD reviews and approves your details, login credentials and access will be sent directly to $email.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        onNavigateToLogin()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Return to Login")
                }
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            ) {
                SubcomposeAsyncImage(
                    model = registerImageUrl,
                    contentDescription = "Register Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Assignment Tracker LMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Student Registration",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Apply for institutional course admission",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Notice",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Submitted registrations are approved by the Admin/HOD before course access and credentials are dispatched.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                placeholder = { Text("e.g. Alex Morgan", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Student Institutional Email") },
                placeholder = { Text("e.g. alex@student.edu", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                placeholder = { Text("6+ characters", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = isCourseDropdownExpanded,
                onExpandedChange = { isCourseDropdownExpanded = !isCourseDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCourse?.let {
                        if (it.code.isNotBlank()) "${it.code} - ${it.title}" else it.title
                    } ?: if (availableCourses.isEmpty()) "Loading courses..." else "Select Course",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Desired Course Enrollment") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCourseDropdownExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )
                ExposedDropdownMenu(
                    expanded = isCourseDropdownExpanded,
                    onDismissRequest = { isCourseDropdownExpanded = false }
                ) {
                    if (availableCourses.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No courses available") },
                            onClick = { isCourseDropdownExpanded = false }
                        )
                    } else {
                        availableCourses.forEach { course ->
                            DropdownMenuItem(
                                text = {
                                    val label = if (course.code.isNotBlank()) "${course.code} - ${course.title}" else course.title
                                    Text(label)
                                },
                                onClick = {
                                    selectedCourse = course
                                    isCourseDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = {
                    val trimmedName = fullName.trim()
                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()
                    val course = selectedCourse

                    if (trimmedName.isBlank() || trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
                        Toast.makeText(context, "Please complete all fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (trimmedPassword.length < 6) {
                        Toast.makeText(context, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (course == null) {
                        Toast.makeText(context, "Please select a course", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isLoading = true

                    auth.createUserWithEmailAndPassword(trimmedEmail, trimmedPassword)
                        .addOnSuccessListener { authResult ->
                            val firebaseUser = authResult.user
                            val uid = firebaseUser?.uid

                            if (uid == null) {
                                isLoading = false
                                Toast.makeText(context, "Failed to retrieve student ID", Toast.LENGTH_SHORT).show()
                                return@addOnSuccessListener
                            }

                            val profileUpdates = userProfileChangeRequest {
                                displayName = trimmedName
                            }

                            firebaseUser.updateProfile(profileUpdates).addOnCompleteListener {
                                val studentData = hashMapOf(
                                    "uid" to uid,
                                    "fullName" to trimmedName,
                                    "email" to trimmedEmail,
                                    "role" to "student",
                                    "isActive" to false, // Inactive until Admin verifies
                                    "createdAt" to FieldValue.serverTimestamp()
                                )

                                val enrollmentData = hashMapOf(
                                    "studentId" to uid,
                                    "studentName" to trimmedName,
                                    "studentEmail" to trimmedEmail,
                                    "courseId" to course.id,
                                    "courseTitle" to course.title,
                                    "courseCode" to course.code,
                                    "status" to "pending_admin_approval",
                                    "verifiedBy" to null,
                                    "requestedAt" to FieldValue.serverTimestamp()
                                )

                                val batch = db.batch()
                                val userRef = db.collection("users").document(uid)
                                val enrollmentRef = db.collection("course_enrollments").document("${uid}_${course.id}")

                                batch.set(userRef, studentData)
                                batch.set(enrollmentRef, enrollmentData)

                                batch.commit()
                                    .addOnSuccessListener {
                                        isLoading = false
                                        showSuccessDialog = true
                                    }
                                    .addOnFailureListener { e ->
                                        isLoading = false
                                        Toast.makeText(
                                            context,
                                            "Failed to initialize: ${e.localizedMessage}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(
                                context,
                                "Registration failed: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                },
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Submit Registration for Approval", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already registered? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Log In",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}