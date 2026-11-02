package com.example.assignmenttracker.ui.tutor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.*
import com.example.assignmenttracker.ui.components.LmsCard
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.*

/* =========================================================================================
   SAFE FIRESTORE VALUE CONVERTERS
   ========================================================================================= */

private fun safeFormatDate(value: Any?): String {
    return when (value) {
        is Timestamp -> {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(value.toDate())
        }
        is Date -> {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(value)
        }
        is Long -> {
            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            sdf.format(Date(value))
        }
        is String -> value
        else -> "N/A"
    }
}

private fun safeToInt(value: Any?, default: Int = 0): Int {
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

private fun safeToLong(value: Any?, default: Long = 0L): Long {
    return when (value) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}

/* =========================================================================================
   TUTOR DASHBOARD SCREEN
   ========================================================================================= */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TutorDashboardScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val tutorId = FirebaseService.auth.currentUser?.uid ?: ""

    var courseId by remember { mutableStateOf("") }
    var courseTitle by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Analytics", "Materials", "Assignments", "Live Attendance", "Live Classes", "Enrolled", "Inquiries", "Grading")

    var approvedStudents by remember { mutableStateOf<List<CourseEnrollment>>(emptyList()) }
    var contents by remember { mutableStateOf<List<CourseContent>>(emptyList()) }
    var assessments by remember { mutableStateOf<List<Assessment>>(emptyList()) }
    var liveSessions by remember { mutableStateOf<List<LiveSession>>(emptyList()) }
    var attendanceSessions by remember { mutableStateOf<List<AttendanceSession>>(emptyList()) }
    var inquiries by remember { mutableStateOf<List<StudentInquiry>>(emptyList()) }

    // Real DB-driven analytics states
    var submissionCountsByAssessmentId by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var attendanceCountsBySessionId by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var isUploadingFile by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableIntStateOf(0) }
    var pickedFileName by remember { mutableStateOf("") }

    var showContentDialog by remember { mutableStateOf(false) }
    var editingContent by remember { mutableStateOf<CourseContent?>(null) }
    var cntWeek by remember { mutableStateOf("Week 1") }
    var cntTitle by remember { mutableStateOf("") }
    var cntType by remember { mutableStateOf("PDF Document") }
    var cntUrl by remember { mutableStateOf("") }
    var cntSummary by remember { mutableStateOf("") }

    var showAssessmentDialog by remember { mutableStateOf(false) }
    var editingAssessment by remember { mutableStateOf<Assessment?>(null) }
    var assTitle by remember { mutableStateOf("") }
    var assType by remember { mutableStateOf("Assignment") }
    var assDueDate by remember { mutableStateOf("") }
    var assInstructions by remember { mutableStateOf("") }
    var assMaxScore by remember { mutableStateOf("100") }
    var assAttachmentUrl by remember { mutableStateOf("") }
    var assAttachmentName by remember { mutableStateOf("") }

    var showAttendanceDialog by remember { mutableStateOf(false) }
    var attTitle by remember { mutableStateOf("Lecture Attendance") }
    var attDurationMinutes by remember { mutableIntStateOf(15) }

    var showLiveSessionDialog by remember { mutableStateOf(false) }
    var editingSession by remember { mutableStateOf<LiveSession?>(null) }
    var sessionTitle by remember { mutableStateOf("") }
    var sessionDate by remember { mutableStateOf("") }
    var sessionTime by remember { mutableStateOf("") }
    var sessionPlatform by remember { mutableStateOf("Google Meet") }
    var sessionUrl by remember { mutableStateOf("") }
    var sessionAgenda by remember { mutableStateOf("") }

    var respondingInquiry by remember { mutableStateOf<StudentInquiry?>(null) }
    var replyText by remember { mutableStateOf("") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingFile = true
            FirebaseService.uploadLocalFileSafe(
                context = context,
                fileUri = uri,
                folder = "course_materials",
                onProgress = { uploadProgress = it },
                onSuccess = { downloadUrl, fileName ->
                    isUploadingFile = false
                    cntUrl = downloadUrl
                    pickedFileName = fileName
                    Toast.makeText(context, "Uploaded: $fileName", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    isUploadingFile = false
                    Toast.makeText(context, "Upload failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    val assignmentDocPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingFile = true
            FirebaseService.uploadLocalFileSafe(
                context = context,
                fileUri = uri,
                folder = "assignments",
                onProgress = { uploadProgress = it },
                onSuccess = { downloadUrl, fileName ->
                    isUploadingFile = false
                    assAttachmentUrl = downloadUrl
                    assAttachmentName = fileName
                    Toast.makeText(context, "Attached: $fileName", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    isUploadingFile = false
                    Toast.makeText(context, "Attachment upload failed: ${it.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    // Load Tutor Data & Course Collections
    LaunchedEffect(tutorId) {
        if (tutorId.isBlank()) return@LaunchedEffect
        FirebaseService.db.collection("users").document(tutorId).get().addOnSuccessListener { doc ->
            courseId = doc.getString("assignedCourseId") ?: ""
            courseTitle = doc.getString("assignedCourseTitle") ?: "Assigned Course"

            if (courseId.isNotBlank()) {
                // Materials
                FirebaseService.db.collection("courses").document(courseId).collection("contents")
                    .addSnapshotListener { s, _ ->
                        contents = s?.documents?.mapNotNull { d ->
                            CourseContent(
                                id = d.id,
                                courseId = courseId,
                                moduleWeek = d.getString("week") ?: "",
                                title = d.getString("title") ?: "",
                                mediaType = d.getString("mediaType") ?: "PDF Document",
                                resourceUrl = d.getString("resourceUrl") ?: "",
                                fileName = d.getString("fileName") ?: "",
                                summary = d.getString("summary") ?: "",
                                isActive = d.getBoolean("isActive") ?: true
                            )
                        } ?: emptyList()
                    }

                // Assessments + Dynamic submission counting per assessment
                FirebaseService.db.collection("courses").document(courseId).collection("assessments")
                    .addSnapshotListener { s, _ ->
                        val fetched = s?.documents?.mapNotNull { d ->
                            Assessment(
                                id = d.id,
                                courseId = courseId,
                                title = d.getString("title") ?: "",
                                type = d.getString("type") ?: "Assignment",
                                dueDate = d.getString("dueDate") ?: "",
                                instructions = d.getString("instructions") ?: "",
                                maxScore = safeToInt(d.get("maxScore"), 100),
                                attachmentUrl = d.getString("attachmentUrl") ?: "",
                                attachmentName = d.getString("attachmentName") ?: "",
                                isActive = d.getBoolean("isActive") ?: true
                            )
                        } ?: emptyList()
                        assessments = fetched

                        // Attach realtime submission listeners for each assessment
                        fetched.forEach { ass ->
                            FirebaseService.db.collection("courses").document(courseId)
                                .collection("assessments").document(ass.id)
                                .collection("submissions")
                                .addSnapshotListener { subSnap, _ ->
                                    val count = subSnap?.size() ?: 0
                                    submissionCountsByAssessmentId = submissionCountsByAssessmentId + (ass.id to count)
                                }
                        }
                    }

                // Attendance Sessions
                FirebaseService.db.collection("courses").document(courseId).collection("attendance_sessions")
                    .addSnapshotListener { s, _ ->
                        attendanceSessions = s?.documents?.mapNotNull { d ->
                            AttendanceSession(
                                id = d.id,
                                courseId = courseId,
                                title = d.getString("title") ?: "",
                                expiresAtTimestamp = safeToLong(d.get("expiresAtTimestamp")),
                                durationMinutes = safeToInt(d.get("durationMinutes"), 15),
                                isActive = d.getBoolean("isActive") ?: true
                            )
                        } ?: emptyList()
                    }

                // Attendance entries (to count present students per session)
                FirebaseService.db.collection("courses").document(courseId).collection("attendance")
                    .addSnapshotListener { s, _ ->
                        val counts = mutableMapOf<String, Int>()
                        s?.documents?.forEach { doc ->
                            val isPresent = doc.getBoolean("isPresent") ?: false
                            val sessId = doc.getString("sessionId") ?: ""
                            if (isPresent && sessId.isNotBlank()) {
                                counts[sessId] = (counts[sessId] ?: 0) + 1
                            }
                        }
                        attendanceCountsBySessionId = counts
                    }

                // Live Sessions
                FirebaseService.db.collection("courses").document(courseId).collection("live_sessions")
                    .addSnapshotListener { s, _ ->
                        liveSessions = s?.documents?.mapNotNull { d ->
                            LiveSession(
                                id = d.id,
                                courseId = courseId,
                                title = d.getString("title") ?: "",
                                date = d.getString("date") ?: "",
                                time = d.getString("time") ?: "",
                                meetingPlatform = d.getString("meetingPlatform") ?: "Google Meet",
                                meetingUrl = d.getString("meetingUrl") ?: "",
                                agenda = d.getString("agenda") ?: "",
                                isActive = d.getBoolean("isActive") ?: true
                            )
                        } ?: emptyList()
                    }

                // Enrolled Students
                FirebaseService.db.collection("course_enrollments")
                    .whereEqualTo("courseId", courseId)
                    .whereEqualTo("status", "approved")
                    .addSnapshotListener { s, _ ->
                        approvedStudents = s?.documents?.mapNotNull { d ->
                            CourseEnrollment(
                                id = d.id,
                                studentId = d.getString("studentId") ?: "",
                                studentName = d.getString("studentName") ?: "",
                                studentEmail = d.getString("studentEmail") ?: "",
                                courseId = d.getString("courseId") ?: "",
                                courseTitle = d.getString("courseTitle") ?: "",
                                courseCode = d.getString("courseCode") ?: "",
                                status = d.getString("status") ?: "approved"
                            )
                        } ?: emptyList()
                    }

                // Inquiries
                FirebaseService.db.collection("courses").document(courseId).collection("inquiries")
                    .addSnapshotListener { s, _ ->
                        inquiries = s?.documents?.mapNotNull { d ->
                            StudentInquiry(
                                id = d.id,
                                courseId = courseId,
                                studentName = d.getString("studentName") ?: "",
                                subject = d.getString("subject") ?: "",
                                message = d.getString("message") ?: "",
                                reply = d.getString("reply") ?: "",
                                isResolved = d.getBoolean("isResolved") ?: false
                            )
                        } ?: emptyList()
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tutor Academic Portal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(courseTitle.ifBlank { "Assigned Course" }, style = MaterialTheme.typography.bodySmall)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        FirebaseService.auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (index) {
                                    0 -> Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(16.dp))
                                    1 -> Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                    2 -> Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                    3 -> Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    4 -> Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(16.dp))
                                    5 -> Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(16.dp))
                                    6 -> Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> { // TAB 0: REAL DATABASE-DRIVEN DATA ANALYSIS & LEARNER PROGRESS
                    TutorAnalyticsScreen(
                        enrolledCount = approvedStudents.size,
                        contents = contents,
                        assessments = assessments,
                        attendanceSessions = attendanceSessions,
                        inquiries = inquiries,
                        submissionCounts = submissionCountsByAssessmentId,
                        attendanceCounts = attendanceCountsBySessionId
                    )
                }

                1 -> { // Materials
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Learning Content (${contents.size})", fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    editingContent = null
                                    cntWeek = "Week 1"; cntTitle = ""; cntType = "PDF Document"; cntUrl = ""; cntSummary = ""
                                    pickedFileName = ""
                                    showContentDialog = true
                                }, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Content")
                                }
                            }
                        }

                        if (contents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No materials published yet. Click 'Add Content' to upload.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(contents, key = { it.id }) { item ->
                                    LmsCard {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text("${item.moduleWeek}: ${item.title}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(if (item.isActive) "Active" else "Deactivated") },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        labelColor = if (item.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                    )
                                                )
                                            }
                                            SuggestionChip(onClick = {}, label = { Text(item.mediaType) })
                                            Text(item.summary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))

                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    editingContent = item
                                                    cntWeek = item.moduleWeek
                                                    cntTitle = item.title
                                                    cntType = item.mediaType
                                                    cntUrl = item.resourceUrl
                                                    cntSummary = item.summary
                                                    pickedFileName = item.fileName
                                                    showContentDialog = true
                                                }) {
                                                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Edit")
                                                }

                                                Button(
                                                    onClick = {
                                                        FirebaseService.db.collection("courses").document(courseId)
                                                            .collection("contents").document(item.id)
                                                            .update("isActive", !item.isActive)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (item.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                                    )
                                                ) {
                                                    Text(if (item.isActive) "Deactivate" else "Activate")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> { // Assessments
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Assessments (${assessments.size})", fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    editingAssessment = null
                                    assTitle = ""; assType = "Assignment"; assDueDate = ""; assInstructions = ""
                                    assAttachmentUrl = ""; assAttachmentName = ""
                                    showAssessmentDialog = true
                                }, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Post Assessment")
                                }
                            }
                        }

                        if (assessments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No assessments posted yet.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(assessments, key = { it.id }) { ass ->
                                    val submissionsReceived = submissionCountsByAssessmentId[ass.id] ?: 0
                                    LmsCard {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                Text(ass.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(if (ass.isActive) "Open" else "Closed") },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        labelColor = if (ass.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                    )
                                                )
                                            }

                                            // Submissions counter badge from DB
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "📥 Submissions: $submissionsReceived / ${approvedStudents.size} enrolled learners",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }

                                            Text("Type: ${ass.type} | Max Marks: ${ass.maxScore}", style = MaterialTheme.typography.bodySmall)
                                            Text("Deadline: ${ass.dueDate}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(ass.instructions, style = MaterialTheme.typography.bodyMedium)

                                            if (ass.attachmentUrl.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                OutlinedButton(onClick = {
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ass.attachmentUrl)))
                                                    } catch (_: Exception) {}
                                                }) {
                                                    Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Attachment: ${ass.attachmentName.ifBlank { "Download Doc" }}")
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    editingAssessment = ass
                                                    assTitle = ass.title
                                                    assType = ass.type
                                                    assDueDate = ass.dueDate
                                                    assInstructions = ass.instructions
                                                    assMaxScore = ass.maxScore.toString()
                                                    assAttachmentUrl = ass.attachmentUrl
                                                    assAttachmentName = ass.attachmentName
                                                    showAssessmentDialog = true
                                                }) { Text("Edit") }

                                                Button(
                                                    onClick = {
                                                        FirebaseService.db.collection("courses").document(courseId)
                                                            .collection("assessments").document(ass.id)
                                                            .update("isActive", !ass.isActive)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (ass.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                                    )
                                                ) {
                                                    Text(if (ass.isActive) "Close Submissions" else "Re-open")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> { // Live Attendance
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Attendance Sessions", fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    attTitle = "Lecture Attendance"
                                    attDurationMinutes = 15
                                    showAttendanceDialog = true
                                }, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Attendance")
                                }
                            }
                        }

                        if (attendanceSessions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No attendance windows created yet.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(attendanceSessions, key = { it.id }) { session ->
                                    val isExpired = System.currentTimeMillis() > session.expiresAtTimestamp
                                    val presentCount = attendanceCountsBySessionId[session.id] ?: 0
                                    LmsCard {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(session.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(if (!isExpired && session.isActive) "Live Window" else "Closed / Expired") },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        labelColor = if (!isExpired && session.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                    )
                                                )
                                            }
                                            Text("Duration: ${session.durationMinutes} Minutes", style = MaterialTheme.typography.bodySmall)
                                            Text("Present Recorded: $presentCount / ${approvedStudents.size} learners", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)

                                            Spacer(modifier = Modifier.height(10.dp))
                                            Button(
                                                onClick = {
                                                    FirebaseService.db.collection("courses").document(courseId)
                                                        .collection("attendance_sessions").document(session.id)
                                                        .update("isActive", !session.isActive)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (session.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                                )
                                            ) {
                                                Text(if (session.isActive) "Deactivate Window" else "Re-activate")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> { // Live Classes
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Live Classes (${liveSessions.size})", fontWeight = FontWeight.Bold)
                                Button(onClick = {
                                    editingSession = null
                                    sessionTitle = ""; sessionDate = ""; sessionTime = ""; sessionUrl = ""; sessionAgenda = ""
                                    showLiveSessionDialog = true
                                }, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Default.VideoCall, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("New Class")
                                }
                            }
                        }

                        if (liveSessions.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No live classes scheduled.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(liveSessions, key = { it.id }) { sess ->
                                    LmsCard {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(sess.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                AssistChip(
                                                    onClick = {},
                                                    label = { Text(if (sess.isActive) "Scheduled" else "Cancelled") },
                                                    colors = AssistChipDefaults.assistChipColors(
                                                        labelColor = if (sess.isActive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                                    )
                                                )
                                            }
                                            Text("${sess.date} at ${sess.time} via ${sess.meetingPlatform}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                            if (sess.agenda.isNotBlank()) Text("Agenda: ${sess.agenda}", style = MaterialTheme.typography.bodySmall)

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(onClick = {
                                                    editingSession = sess
                                                    sessionTitle = sess.title
                                                    sessionDate = sess.date
                                                    sessionTime = sess.time
                                                    sessionPlatform = sess.meetingPlatform
                                                    sessionUrl = sess.meetingUrl
                                                    sessionAgenda = sess.agenda
                                                    showLiveSessionDialog = true
                                                }) { Text("Edit") }

                                                Button(
                                                    onClick = {
                                                        FirebaseService.db.collection("courses").document(courseId)
                                                            .collection("live_sessions").document(sess.id)
                                                            .update("isActive", !sess.isActive)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (sess.isActive) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                                                    )
                                                ) {
                                                    Text(if (sess.isActive) "Cancel / Deactivate" else "Restore")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                5 -> { // Enrolled Students
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Enrolled Course Students", fontWeight = FontWeight.Bold)
                                Text("Total: ${approvedStudents.size}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        if (approvedStudents.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("No students verified for this course yet.", color = Color.Gray)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(approvedStudents, key = { it.id }) { item ->
                                    LmsCard {
                                        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column {
                                                Text(item.studentName, fontWeight = FontWeight.Bold)
                                                Text(item.studentEmail, style = MaterialTheme.typography.bodySmall)
                                            }
                                            AssistChip(onClick = {}, label = { Text("Active") })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                6 -> { // Inquiries
                    if (inquiries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No student inquiries at this time.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(inquiries, key = { it.id }) { inq ->
                                LmsCard {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(inq.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text("From: ${inq.studentName}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(inq.message, style = MaterialTheme.typography.bodyMedium)
                                        if (inq.reply.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                                Text("Response: ${inq.reply}", modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(onClick = {
                                            respondingInquiry = inq
                                            replyText = inq.reply
                                        }) {
                                            Text(if (inq.isResolved) "Edit Answer" else "Answer Question")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                7 -> {
                    if (courseId.isBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else {
                        TutorGradingScreen(
                            courseId = courseId,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Dialogs
    if (showContentDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploadingFile) showContentDialog = false },
            title = { Text(if (editingContent == null) "Publish Learning Resource" else "Edit Resource", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = cntWeek, onValueChange = { cntWeek = it }, label = { Text("Module / Week") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = cntTitle, onValueChange = { cntTitle = it }, label = { Text("Resource Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("Media Category:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("PDF Document", "Video Link", "Podcast / Audio", "Word / Doc").forEach { type ->
                            FilterChip(
                                selected = cntType == type,
                                onClick = { cntType = type },
                                label = { Text(type, fontSize = 12.sp) }
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Attach File from Device:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Select File from Phone")
                            }

                            if (isUploadingFile) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("Uploading: $uploadProgress%", style = MaterialTheme.typography.bodySmall)
                            } else if (pickedFileName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Attached: $pickedFileName", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = cntUrl,
                        onValueChange = { cntUrl = it },
                        label = { Text("URL Link (Auto-filled on file upload)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cntSummary,
                        onValueChange = { cntSummary = it },
                        label = { Text("Summary & Study Objectives") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploadingFile,
                    onClick = {
                        if (cntTitle.isBlank() || courseId.isBlank()) return@Button
                        val data = hashMapOf(
                            "week" to cntWeek.trim(),
                            "title" to cntTitle.trim(),
                            "mediaType" to cntType,
                            "resourceUrl" to cntUrl.trim(),
                            "fileName" to pickedFileName.trim(),
                            "summary" to cntSummary.trim(),
                            "isActive" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        if (editingContent == null) {
                            data["createdAt"] = FieldValue.serverTimestamp()
                            FirebaseService.db.collection("courses").document(courseId).collection("contents").add(data)
                            FirebaseService.broadcastToEnrolledStudents(
                                courseId = courseId,
                                type = "new_content",
                                title = "New Material: $cntTitle",
                                message = "Module: $cntWeek has been uploaded."
                            )
                        } else {
                            FirebaseService.db.collection("courses").document(courseId).collection("contents").document(editingContent!!.id).update(data as Map<String, Any>)
                        }
                        showContentDialog = false
                        Toast.makeText(context, "Content saved & students notified!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Publish Resource")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContentDialog = false }, enabled = !isUploadingFile) { Text("Cancel") }
            }
        )
    }

    if (showAssessmentDialog) {
        AlertDialog(
            onDismissRequest = { if (!isUploadingFile) showAssessmentDialog = false },
            title = { Text(if (editingAssessment == null) "Create Assessment" else "Edit Assessment", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(value = assTitle, onValueChange = { assTitle = it }, label = { Text("Assessment Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                    Text("Assessment Type:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Assignment", "CAT", "Final Exam").forEach { t ->
                            FilterChip(selected = assType == t, onClick = { assType = t }, label = { Text(t) })
                        }
                    }

                    OutlinedTextField(value = assDueDate, onValueChange = { assDueDate = it }, label = { Text("Deadline (e.g. 2026-11-20 23:59)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = assMaxScore, onValueChange = { assMaxScore = it }, label = { Text("Total Marks (e.g. 100)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = assInstructions, onValueChange = { assInstructions = it }, label = { Text("Questions / Instructions") }, minLines = 3, modifier = Modifier.fillMaxWidth())

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Attach Question Paper (PDF/Word):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { assignmentDocPickerLauncher.launch(arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Pick PDF / DOC Document")
                            }

                            if (isUploadingFile) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else if (assAttachmentName.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Attached: $assAttachmentName", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploadingFile,
                    onClick = {
                        if (assTitle.isBlank() || courseId.isBlank()) return@Button
                        val data = hashMapOf(
                            "title" to assTitle.trim(),
                            "type" to assType,
                            "dueDate" to assDueDate.trim(),
                            "instructions" to assInstructions.trim(),
                            "maxScore" to (assMaxScore.toIntOrNull() ?: 100),
                            "attachmentUrl" to assAttachmentUrl,
                            "attachmentName" to assAttachmentName,
                            "isActive" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        if (editingAssessment == null) {
                            data["createdAt"] = FieldValue.serverTimestamp()
                            FirebaseService.db.collection("courses").document(courseId).collection("assessments").add(data)
                            FirebaseService.broadcastToEnrolledStudents(
                                courseId = courseId,
                                type = "new_assessment",
                                title = "New $assType: $assTitle",
                                message = "Submission deadline: $assDueDate."
                            )
                        } else {
                            FirebaseService.db.collection("courses").document(courseId).collection("assessments").document(editingAssessment!!.id).update(data as Map<String, Any>)
                        }
                        showAssessmentDialog = false
                        Toast.makeText(context, "Assessment published!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Publish Assessment")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAssessmentDialog = false }, enabled = !isUploadingFile) { Text("Cancel") }
            }
        )
    }

    if (showAttendanceDialog) {
        AlertDialog(
            onDismissRequest = { showAttendanceDialog = false },
            title = { Text("Open Timed Attendance Window", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = attTitle, onValueChange = { attTitle = it }, label = { Text("Session Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Active Window Duration:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 15, 30, 60).forEach { mins ->
                            FilterChip(
                                selected = attDurationMinutes == mins,
                                onClick = { attDurationMinutes = mins },
                                label = { Text("$mins Mins") }
                            )
                        }
                    }
                    Text(
                        "Students must tap 'Mark Present' on their dashboard within $attDurationMinutes minutes before it automatically closes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (attTitle.isBlank() || courseId.isBlank()) return@Button
                    val expiry = System.currentTimeMillis() + (attDurationMinutes * 60 * 1000)
                    val data = hashMapOf(
                        "title" to attTitle.trim(),
                        "durationMinutes" to attDurationMinutes,
                        "expiresAtTimestamp" to expiry,
                        "isActive" to true,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    FirebaseService.db.collection("courses").document(courseId).collection("attendance_sessions").add(data)
                    FirebaseService.broadcastToEnrolledStudents(
                        courseId = courseId,
                        type = "attendance_call",
                        title = "Mark Attendance: $attTitle",
                        message = "Live attendance window is open for $attDurationMinutes minutes!"
                    )
                    showAttendanceDialog = false
                    Toast.makeText(context, "Attendance window opened!", Toast.LENGTH_SHORT).show()
                }) { Text("Start Live Attendance") }
            },
            dismissButton = {
                TextButton(onClick = { showAttendanceDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showLiveSessionDialog) {
        AlertDialog(
            onDismissRequest = { showLiveSessionDialog = false },
            title = { Text(if (editingSession == null) "Schedule Live Online Session" else "Edit Live Session", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = sessionTitle, onValueChange = { sessionTitle = it }, label = { Text("Session Topic") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Google Meet", "Zoom", "MS Teams").forEach { p ->
                            FilterChip(selected = sessionPlatform == p, onClick = { sessionPlatform = p }, label = { Text(p) })
                        }
                    }
                    OutlinedTextField(value = sessionDate, onValueChange = { sessionDate = it }, label = { Text("Date (e.g. 2026-11-15)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sessionTime, onValueChange = { sessionTime = it }, label = { Text("Time (e.g. 10:00 AM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sessionUrl, onValueChange = { sessionUrl = it }, label = { Text("Meeting Link URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = sessionAgenda, onValueChange = { sessionAgenda = it }, label = { Text("Agenda / Outcomes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (sessionTitle.isBlank() || sessionUrl.isBlank() || courseId.isBlank()) return@Button
                    val data = hashMapOf(
                        "title" to sessionTitle.trim(),
                        "date" to sessionDate.trim(),
                        "time" to sessionTime.trim(),
                        "meetingPlatform" to sessionPlatform,
                        "meetingUrl" to sessionUrl.trim(),
                        "agenda" to sessionAgenda.trim(),
                        "isActive" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )

                    if (editingSession == null) {
                        data["createdAt"] = FieldValue.serverTimestamp()
                        FirebaseService.db.collection("courses").document(courseId).collection("live_sessions").add(data)
                        FirebaseService.broadcastToEnrolledStudents(
                            courseId = courseId,
                            type = "live_class",
                            title = "Live Class: $sessionTitle",
                            message = "Scheduled for $sessionDate at $sessionTime via $sessionPlatform."
                        )
                    } else {
                        FirebaseService.db.collection("courses").document(courseId).collection("live_sessions").document(editingSession!!.id).update(data as Map<String, Any>)
                    }
                    showLiveSessionDialog = false
                    Toast.makeText(context, "Session updated!", Toast.LENGTH_SHORT).show()
                }) { Text("Save Session") }
            },
            dismissButton = {
                TextButton(onClick = { showLiveSessionDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (respondingInquiry != null) {
        AlertDialog(
            onDismissRequest = { respondingInquiry = null },
            title = { Text("Reply to Student Inquiry", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Question: ${respondingInquiry?.message}", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(value = replyText, onValueChange = { replyText = it }, label = { Text("Tutor Explanation") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val inq = respondingInquiry ?: return@Button
                    FirebaseService.db.collection("courses").document(courseId)
                        .collection("inquiries").document(inq.id)
                        .update(mapOf(
                            "reply" to replyText.trim(),
                            "isResolved" to true,
                            "answeredAt" to FieldValue.serverTimestamp()
                        ))
                    respondingInquiry = null
                    Toast.makeText(context, "Answer sent!", Toast.LENGTH_SHORT).show()
                }) { Text("Send Response") }
            },
            dismissButton = {
                TextButton(onClick = { respondingInquiry = null }) { Text("Cancel") }
            }
        )
    }
}

/* =========================================================================================
   TUTOR ANALYTICS TAB: 100% REAL DATABASE CHARTS & LEARNER PROGRESS
   ========================================================================================= */

@Composable
fun TutorAnalyticsScreen(
    enrolledCount: Int,
    contents: List<CourseContent>,
    assessments: List<Assessment>,
    attendanceSessions: List<AttendanceSession>,
    inquiries: List<StudentInquiry>,
    submissionCounts: Map<String, Int>,
    attendanceCounts: Map<String, Int>
) {
    // 1. Dynamic Calculations from DB
    val totalSubmissions = submissionCounts.values.sum()
    val expectedSubmissions = (enrolledCount * assessments.size).coerceAtLeast(1)
    val submissionRate = ((totalSubmissions.toFloat() / expectedSubmissions) * 100).coerceIn(0f, 100f).toInt()

    val totalAttendanceMarks = attendanceCounts.values.sum()
    val avgAttendance = if (attendanceSessions.isNotEmpty()) totalAttendanceMarks / attendanceSessions.size else 0

    val resolvedInquiries = inquiries.count { it.isResolved }
    val pendingInquiries = inquiries.size - resolvedInquiries

    // 2. Bar Chart Data: Learner submission rate per assessment from Firestore
    val barLabels = assessments.map { it.title.take(10) }
    val barValues = assessments.map { ass ->
        val received = submissionCounts[ass.id] ?: 0
        if (enrolledCount > 0) (received.toFloat() / enrolledCount) * 100f else 0f
    }

    // 3. Line Graph Data: Learner attendance count per live session from Firestore
    val lineLabels = attendanceSessions.map { it.title.take(8) }
    val linePoints = attendanceSessions.map { session ->
        (attendanceCounts[session.id] ?: 0).toFloat()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Stat Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TutorStatCard(
                    title = "Enrolled",
                    value = "$enrolledCount",
                    subtitle = "Active Learners",
                    icon = Icons.Default.Groups,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                TutorStatCard(
                    title = "Submissions",
                    value = "$submissionRate%",
                    subtitle = "$totalSubmissions received",
                    icon = Icons.Default.AssignmentTurnedIn,
                    color = if (submissionRate >= 50) Color(0xFF2E7D32) else Color(0xFFE65100),
                    modifier = Modifier.weight(1f)
                )
                TutorStatCard(
                    title = "Avg Attendance",
                    value = "$avgAttendance",
                    subtitle = "Per lecture window",
                    icon = Icons.Default.FactCheck,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Graph 1: Learner Submission Turnout (Bar Chart)
        item {
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Learner Submission Turnout",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Percentage of enrolled learners who submitted per assessment",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (assessments.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No assessments posted yet. Add an assignment to analyze turnout.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        TutorBarChart(
                            values = barValues,
                            labels = barLabels,
                            barColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        )
                    }
                }
            }
        }

        // Graph 2: Attendance Turnout Trend (Line Chart)
        item {
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Lecture Attendance Turnout Trend",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Count of students present across live attendance sessions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (attendanceSessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No attendance windows recorded yet in database.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        TutorLineChart(
                            dataPoints = linePoints,
                            labels = lineLabels,
                            lineColor = Color(0xFF00897B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                        )
                    }
                }
            }
        }

        // Graph 3: Course Inquiry & Content Distribution (Donut Chart)
        item {
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Learner Inquiry Resolution",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Real-time ratio of answered vs pending learner questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (inquiries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No learner inquiries recorded yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        val slices = listOf(
                            TutorPieSlice(label = "Answered", value = resolvedInquiries.toFloat(), color = Color(0xFF4CAF50)),
                            TutorPieSlice(label = "Pending Response", value = pendingInquiries.toFloat(), color = Color(0xFFE53935))
                        )

                        val percentResolved = ((resolvedInquiries.toFloat() / inquiries.size) * 100).toInt()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            TutorDonutChart(
                                slices = slices,
                                modifier = Modifier.size(120.dp),
                                centerLabel = "$percentResolved%",
                                centerSub = "Resolved"
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slices.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(s.color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${s.label} (${s.value.toInt()})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
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

/* =========================================================================================
   CANVAS CHART COMPONENTS (PURE COMPOSE, ZERO EXTERNAL DEPENDENCIES)
   ========================================================================================= */

data class TutorPieSlice(val label: String, val value: Float, val color: Color)

@Composable
fun TutorDonutChart(
    slices: List<TutorPieSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String = "",
    centerSub: String = ""
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 16.dp.toPx()
            var startAngle = -90f

            slices.forEach { slice ->
                val sweepAngle = (slice.value / total) * 360f
                if (sweepAngle > 0f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = centerLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (centerSub.isNotBlank()) {
                Text(text = centerSub, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun TutorBarChart(
    values: List<Float>,
    labels: List<String>,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            val width = size.width
            val height = size.height
            val barCount = values.size
            val barWidth = (width / barCount.coerceAtLeast(1)) * 0.5f
            val slotWidth = width / barCount.coerceAtLeast(1)

            values.forEachIndexed { i, value ->
                val barHeight = ((value.coerceIn(0f, 100f) / 100f) * height).coerceAtLeast(6f)
                val left = i * slotWidth + (slotWidth - barWidth) / 2
                val top = height - barHeight

                // Draw background track
                drawRoundRect(
                    color = barColor.copy(alpha = 0.12f),
                    topLeft = Offset(left, 0f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )

                // Draw filled bar
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            labels.forEachIndexed { idx, label ->
                val pct = values.getOrNull(idx)?.toInt() ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "$pct%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = barColor)
                    Text(text = label, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun TutorLineChart(
    dataPoints: List<Float>,
    labels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    val maxVal = (dataPoints.maxOrNull() ?: 10f).coerceAtLeast(5f)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val count = dataPoints.size
            val spacing = if (count > 1) width / (count - 1) else width / 2

            val coordinates = dataPoints.mapIndexed { index, value ->
                val x = if (count > 1) index * spacing else width / 2
                val y = height - (value / maxVal * height)
                Offset(x, y)
            }

            if (coordinates.size > 1) {
                val fillPath = Path().apply {
                    moveTo(coordinates.first().x, height)
                    coordinates.forEach { lineTo(it.x, it.y) }
                    lineTo(coordinates.last().x, height)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.35f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                val strokePath = Path().apply {
                    moveTo(coordinates.first().x, coordinates.first().y)
                    for (i in 1 until coordinates.size) {
                        lineTo(coordinates[i].x, coordinates[i].y)
                    }
                }
                drawPath(path = strokePath, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }

            coordinates.forEach { pt ->
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = pt)
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = pt)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = if (labels.size > 1) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            labels.forEachIndexed { i, label ->
                val pt = dataPoints.getOrNull(i)?.toInt() ?: 0
                Text(
                    text = "$label ($pt)",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TutorStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.08f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}