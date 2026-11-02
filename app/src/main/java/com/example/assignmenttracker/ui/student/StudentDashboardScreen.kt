package com.example.assignmenttracker.ui.student

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.*
import com.example.assignmenttracker.ui.components.LmsCard
import com.example.assignmenttracker.ui.theme.ThemeToggleButton
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

/* =========================================================================================
   SAFE TYPE CONVERTERS
   ========================================================================================= */

fun safeFormatDate(value: Any?): String {
    return when (value) {
        is Timestamp -> SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(value.toDate())
        is Date -> SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(value)
        is Long -> SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(value))
        is String -> value
        else -> "N/A"
    }
}

fun safeToInt(value: Any?, default: Int = 0): Int {
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

fun safeToLong(value: Any?, default: Long = 0L): Long {
    return when (value) {
        is Timestamp -> value.toDate().time
        is Date -> value.time
        is Number -> value.toLong()
        is String -> value.toLongOrNull() ?: default
        else -> default
    }
}

data class StudentAssessmentSubmission(
    val assessmentId: String = "",
    val score: Int? = null,
    val feedback: String = "",
    val isGraded: Boolean = false,
    val submittedAt: String = "",
    val strikes: Int = 0,
    val terminationReason: String = ""
)

/* =========================================================================================
   STUDENT DASHBOARD SCREEN
   ========================================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDashboardScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val studentUid = FirebaseService.auth.currentUser?.uid ?: ""

    var enrolledCourseId by remember { mutableStateOf("") }
    var enrolledCourseTitle by remember { mutableStateOf("") }
    var enrolledCourseCode by remember { mutableStateOf("") }
    var studentName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Overview", "Assessments", "Materials", "Live Classes", "Inquiries", "Attendance")

    var contents by remember { mutableStateOf<List<CourseContent>>(emptyList()) }
    var liveSessions by remember { mutableStateOf<List<LiveSession>>(emptyList()) }
    var assessments by remember { mutableStateOf<List<Assessment>>(emptyList()) }
    var inquiries by remember { mutableStateOf<List<StudentInquiry>>(emptyList()) }
    var attendanceList by remember { mutableStateOf<List<AttendanceEntry>>(emptyList()) }
    var completedContentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submittedAssessmentIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var submissionsMap by remember { mutableStateOf<Map<String, StudentAssessmentSubmission>>(emptyMap()) }

    // Proctored Exam & Assignment State
    var activeProctoredAssessment by remember { mutableStateOf<Assessment?>(null) }
    var standardTargetAssessment by remember { mutableStateOf<Assessment?>(null) }
    var submissionText by remember { mutableStateOf("") }
    var isSubmittingStandard by remember { mutableStateOf(false) }

    var showInquiryDialog by remember { mutableStateOf(false) }
    var inquirySubject by remember { mutableStateOf("") }
    var inquiryMessage by remember { mutableStateOf("") }

    fun openResourceUrl(url: String) {
        if (url.isBlank()) {
            Toast.makeText(context, "No URL provided for this resource", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            try {
                val googleDocsUrl = "https://docs.google.com/viewer?url=" + Uri.encode(url)
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(googleDocsUrl)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(browserIntent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Cannot open: ${e2.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 1. Load User Profile and Course Enrollment dynamically
    LaunchedEffect(studentUid) {
        if (studentUid.isBlank()) return@LaunchedEffect

        FirebaseService.db.collection("users").document(studentUid).get()
            .addOnSuccessListener { doc ->
                studentName = doc.getString("fullName") ?: doc.getString("name") ?: "Student"
            }
            .addOnFailureListener { e ->
                errorMessage = "Failed to load profile: ${e.localizedMessage}"
            }

        // Live Enrollment Listener
        FirebaseService.db.collection("course_enrollments")
            .whereEqualTo("studentId", studentUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    errorMessage = "Enrollment error: ${error.localizedMessage}"
                    return@addSnapshotListener
                }
                val validDoc = snapshot?.documents?.firstOrNull {
                    val st = it.getString("status")?.lowercase()
                    st == "approved" || st == "enrolled" || st == "active"
                } ?: snapshot?.documents?.firstOrNull()

                if (validDoc != null) {
                    enrolledCourseId = validDoc.getString("courseId") ?: ""
                    enrolledCourseTitle = validDoc.getString("courseTitle") ?: ""
                    enrolledCourseCode = validDoc.getString("courseCode") ?: ""
                } else {
                    // Fallback: Check direct membership in courses collection
                    FirebaseService.db.collection("courses")
                        .whereArrayContains("enrolledStudents", studentUid)
                        .get()
                        .addOnSuccessListener { cSnap ->
                            val cDoc = cSnap.documents.firstOrNull()
                            if (cDoc != null) {
                                enrolledCourseId = cDoc.id
                                enrolledCourseTitle = cDoc.getString("title") ?: ""
                                enrolledCourseCode = cDoc.getString("code") ?: ""
                            }
                        }
                }
            }

        // Real-time Materials Completion
        FirebaseService.db.collection("users").document(studentUid)
            .collection("completed_materials")
            .addSnapshotListener { snapshot, _ ->
                completedContentIds = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        // Real-time Taken Exams Listener
        FirebaseService.db.collection("users").document(studentUid)
            .collection("taken_exams")
            .addSnapshotListener { snapshot, _ ->
                val takenIds = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                submittedAssessmentIds = submittedAssessmentIds + takenIds
            }
    }

    // 2. Load Course Subcollections dynamically from DB
    LaunchedEffect(enrolledCourseId) {
        if (enrolledCourseId.isBlank()) return@LaunchedEffect

        // Materials
        FirebaseService.db.collection("courses").document(enrolledCourseId).collection("contents")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { s, _ ->
                contents = s?.documents?.mapNotNull { d ->
                    CourseContent(
                        id = d.id,
                        courseId = enrolledCourseId,
                        moduleWeek = d.getString("week") ?: d.getString("moduleWeek") ?: "",
                        title = d.getString("title") ?: "",
                        mediaType = d.getString("mediaType") ?: "PDF Document",
                        resourceUrl = d.getString("resourceUrl") ?: "",
                        fileName = d.getString("fileName") ?: "",
                        summary = d.getString("summary") ?: "",
                        isActive = d.getBoolean("isActive") ?: true
                    )
                } ?: emptyList()
            }

        // Live sessions
        FirebaseService.db.collection("courses").document(enrolledCourseId).collection("live_sessions")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { s, _ ->
                liveSessions = s?.documents?.mapNotNull { d ->
                    LiveSession(
                        id = d.id,
                        courseId = enrolledCourseId,
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

        // Assessments with live Student Submissions, Grades & Feedback
        FirebaseService.db.collection("courses").document(enrolledCourseId).collection("assessments")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { s, _ ->
                val fetched = s?.documents?.mapNotNull { d ->
                    @Suppress("UNCHECKED_CAST")
                    val qList = d.get("questions") as? List<String> ?: emptyList()
                    Assessment(
                        id = d.id,
                        courseId = enrolledCourseId,
                        title = d.getString("title") ?: "",
                        type = d.getString("type") ?: "Assignment",
                        dueDate = d.getString("dueDate") ?: "",
                        instructions = d.getString("instructions") ?: "",
                        maxScore = safeToInt(d.get("maxScore"), 100),
                        durationMinutes = safeToInt(d.get("durationMinutes"), 60),
                        attachmentUrl = d.getString("attachmentUrl") ?: "",
                        attachmentName = d.getString("attachmentName") ?: "",
                        questions = qList,
                        isActive = d.getBoolean("isActive") ?: true
                    )
                } ?: emptyList()
                assessments = fetched

                // Real-time synchronization of submissions, marks and feedback
                fetched.forEach { ass ->
                    FirebaseService.db.collection("courses").document(enrolledCourseId)
                        .collection("assessments").document(ass.id)
                        .collection("submissions").document(studentUid)
                        .addSnapshotListener { subDoc, _ ->
                            if (subDoc != null && subDoc.exists()) {
                                val score = subDoc.getLong("score")?.toInt()
                                val feedback = subDoc.getString("feedback") ?: ""
                                val isGraded = subDoc.getBoolean("isGraded") ?: (score != null)
                                val submittedAt = safeFormatDate(subDoc.get("submittedAt"))
                                val strikes = subDoc.getLong("strikes")?.toInt() ?: 0
                                val reason = subDoc.getString("terminationReason") ?: ""

                                submissionsMap = submissionsMap + (ass.id to StudentAssessmentSubmission(
                                    assessmentId = ass.id,
                                    score = score,
                                    feedback = feedback,
                                    isGraded = isGraded,
                                    submittedAt = submittedAt,
                                    strikes = strikes,
                                    terminationReason = reason
                                ))
                                submittedAssessmentIds = submittedAssessmentIds + ass.id
                            }
                        }
                }
            }

        // Inquiries bound strictly to studentUid from DB
        FirebaseService.db.collection("courses").document(enrolledCourseId).collection("inquiries")
            .addSnapshotListener { s, _ ->
                inquiries = s?.documents?.mapNotNull { d ->
                    val sId = d.getString("studentId") ?: ""
                    val sName = d.getString("studentName") ?: ""
                    if (sId == studentUid || (sId.isBlank() && sName.isNotBlank() && sName == studentName)) {
                        StudentInquiry(
                            id = d.id,
                            courseId = enrolledCourseId,
                            studentName = sName,
                            subject = d.getString("subject") ?: "",
                            message = d.getString("message") ?: "",
                            reply = d.getString("reply") ?: "",
                            isResolved = d.getBoolean("isResolved") ?: false
                        )
                    } else null
                } ?: emptyList()
            }

        // Attendance
        FirebaseService.db.collection("courses").document(enrolledCourseId).collection("attendance")
            .whereEqualTo("studentId", studentUid)
            .addSnapshotListener { s, _ ->
                attendanceList = s?.documents?.mapNotNull { d ->
                    AttendanceEntry(
                        id = d.id,
                        studentId = d.getString("studentId") ?: "",
                        studentName = d.getString("studentName") ?: "",
                        date = d.getString("date") ?: "",
                        markedAt = safeFormatDate(d.get("markedAt")),
                        isPresent = d.getBoolean("isPresent") ?: false
                    )
                } ?: emptyList()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (enrolledCourseTitle.isNotBlank()) "$enrolledCourseCode - $enrolledCourseTitle" else "Student Dashboard",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Learner: ${studentName.ifBlank { "Active Student" }} • Academic Portal",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                },
                actions = {
                    ThemeToggleButton()
                    IconButton(onClick = {
                        FirebaseService.auth.signOut()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "⚠️ $err",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                when (index) {
                                    0 -> Icon(Icons.Default.Insights, null, modifier = Modifier.size(16.dp))
                                    1 -> Icon(Icons.Default.Assignment, null, modifier = Modifier.size(16.dp))
                                    2 -> Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(16.dp))
                                    3 -> Icon(Icons.Default.VideoCall, null, modifier = Modifier.size(16.dp))
                                    4 -> Icon(Icons.Default.QuestionAnswer, null, modifier = Modifier.size(16.dp))
                                    5 -> Icon(Icons.Default.FactCheck, null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(title, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    )
                }
            }

            if (enrolledCourseId.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> {
                        StudentAnalyticsOverview(
                            contents = contents,
                            completedIds = completedContentIds,
                            assessments = assessments,
                            submittedIds = submittedAssessmentIds,
                            attendanceCount = attendanceList.count { it.isPresent },
                            onNavigateToAssessments = { selectedTab = 1 },
                            onNavigateToMaterials = { selectedTab = 2 }
                        )
                    }
                    1 -> {
                        AssessmentsTabContent(
                            assessments = assessments,
                            submittedIds = submittedAssessmentIds,
                            submissionsMap = submissionsMap,
                            onOpenUrl = { openResourceUrl(it) },
                            onStartAssessment = { ass ->
                                val isExam = ass.type.contains("CAT", true) ||
                                        ass.type.contains("Exam", true) ||
                                        ass.type.contains("Test", true)
                                if (isExam) {
                                    activeProctoredAssessment = ass
                                } else {
                                    standardTargetAssessment = ass
                                    submissionText = ""
                                }
                            }
                        )
                    }
                    2 -> {
                        MaterialsTabContent(
                            contents = contents,
                            completedIds = completedContentIds,
                            studentUid = studentUid,
                            onOpenUrl = { openResourceUrl(it) }
                        )
                    }
                    3 -> {
                        LiveSessionsTabContent(
                            liveSessions = liveSessions,
                            onJoin = { url ->
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Invalid meeting URL", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                    4 -> {
                        InquiriesTabContent(
                            inquiries = inquiries,
                            onAskQuestionClick = {
                                inquirySubject = ""
                                inquiryMessage = ""
                                showInquiryDialog = true
                            }
                        )
                    }
                    5 -> {
                        AttendanceTabContent(
                            enrolledCourseId = enrolledCourseId,
                            studentUid = studentUid,
                            studentName = studentName,
                            attendanceList = attendanceList
                        )
                    }
                }
            }
        }
    }

    // =========================================================================
    // PROCTORED EXAM LOCKDOWN DIALOG
    // =========================================================================
    activeProctoredAssessment?.let { currentExam ->
        ProctoredExamLockdownScreen(
            assessment = currentExam,
            studentUid = studentUid,
            studentName = studentName,
            courseId = enrolledCourseId,
            onExamCompleted = {
                submittedAssessmentIds = submittedAssessmentIds + currentExam.id
                activeProctoredAssessment = null
            }
        )
    }

    // Standard Homework Submission Modal (Non-Proctored)
    standardTargetAssessment?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!isSubmittingStandard) standardTargetAssessment = null },
            title = { Text("Submit: ${target.title}", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Type your solution or paste your Google Docs / Drive cloud link:")
                    OutlinedTextField(
                        value = submissionText,
                        onValueChange = { submissionText = it },
                        label = { Text("Solution / URL") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSubmittingStandard,
                    onClick = {
                        val text = submissionText.trim()
                        if (text.isBlank()) {
                            Toast.makeText(context, "Please enter your submission", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSubmittingStandard = true
                        val submissionData = hashMapOf(
                            "assessmentId" to target.id,
                            "assessmentTitle" to target.title,
                            "studentId" to studentUid,
                            "studentName" to studentName,
                            "content" to text,
                            "submittedAt" to FieldValue.serverTimestamp()
                        )
                        FirebaseService.db.collection("courses").document(enrolledCourseId)
                            .collection("assessments").document(target.id)
                            .collection("submissions").document(studentUid)
                            .set(submissionData)
                            .addOnSuccessListener {
                                isSubmittingStandard = false
                                standardTargetAssessment = null
                                submittedAssessmentIds = submittedAssessmentIds + target.id
                                Toast.makeText(context, "Submitted successfully!", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                isSubmittingStandard = false
                                Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                ) {
                    if (isSubmittingStandard) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { standardTargetAssessment = null }, enabled = !isSubmittingStandard) {
                    Text("Cancel")
                }
            }
        )
    }

    // Ask Question Dialog (Linked to DB)
    if (showInquiryDialog) {
        AlertDialog(
            onDismissRequest = { showInquiryDialog = false },
            title = { Text("Ask Your Tutor", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = inquirySubject,
                        onValueChange = { inquirySubject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = inquiryMessage,
                        onValueChange = { inquiryMessage = it },
                        label = { Text("Question") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (inquirySubject.isBlank() || inquiryMessage.isBlank()) {
                        Toast.makeText(context, "Please fill subject and question", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val data = hashMapOf(
                        "courseId" to enrolledCourseId,
                        "studentId" to studentUid,
                        "studentName" to studentName,
                        "subject" to inquirySubject.trim(),
                        "message" to inquiryMessage.trim(),
                        "reply" to "",
                        "isResolved" to false,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                    FirebaseService.db.collection("courses").document(enrolledCourseId)
                        .collection("inquiries").add(data)
                        .addOnSuccessListener {
                            showInquiryDialog = false
                            Toast.makeText(context, "Sent to tutor!", Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text("Send")
                }
            },
            dismissButton = {
                TextButton(onClick = { showInquiryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/* =========================================================================================
   PROCTORED EXAM LOCKDOWN COMPONENT
   ========================================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProctoredExamLockdownScreen(
    assessment: Assessment,
    studentUid: String,
    studentName: String,
    courseId: String,
    onExamCompleted: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    DisposableEffect(Unit) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    BackHandler(enabled = true) {
        Toast.makeText(context, "⚠️ Proctored Mode Active! You cannot exit until submitted.", Toast.LENGTH_SHORT).show()
    }

    var strikeCount by remember { mutableIntStateOf(0) }
    val maxStrikes = 3
    var isAutoSubmittedDueToViolation by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !isAutoSubmittedDueToViolation) {
                strikeCount++
                if (strikeCount >= maxStrikes) {
                    isAutoSubmittedDueToViolation = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val totalSeconds = remember(assessment.durationMinutes) {
        (if (assessment.durationMinutes > 0) assessment.durationMinutes else 60) * 60
    }
    var remainingSeconds by remember { mutableIntStateOf(totalSeconds) }
    var isSubmittingExam by remember { mutableStateOf(false) }

    val answersMap = remember {
        mutableStateMapOf<Int, String>().apply {
            if (assessment.questions.isNotEmpty()) {
                assessment.questions.indices.forEach { put(it, "") }
            } else {
                put(0, "")
            }
        }
    }

    val submitExamToDatabase: (reason: String) -> Unit = { reason ->
        if (!isSubmittingExam) {
            isSubmittingExam = true
            val formattedAnswers = buildString {
                answersMap.forEach { (qIdx, ans) ->
                    val qPrompt = assessment.questions.getOrNull(qIdx) ?: "General Solution"
                    append("Q${qIdx + 1}: $qPrompt\nAnswer: $ans\n\n")
                }
            }.trim()

            val examRecord = hashMapOf(
                "assessmentId" to assessment.id,
                "assessmentTitle" to assessment.title,
                "type" to assessment.type,
                "studentId" to studentUid,
                "studentName" to studentName,
                "content" to formattedAnswers,
                "strikes" to strikeCount,
                "terminationReason" to reason,
                "status" to "COMPLETED",
                "submittedAt" to FieldValue.serverTimestamp()
            )

            FirebaseService.db.collection("courses").document(courseId)
                .collection("assessments").document(assessment.id)
                .collection("submissions").document(studentUid)
                .set(examRecord)
                .addOnSuccessListener {
                    FirebaseService.db.collection("users").document(studentUid)
                        .collection("taken_exams").document(assessment.id)
                        .set(mapOf("completedAt" to FieldValue.serverTimestamp()))

                    isSubmittingExam = false
                    Toast.makeText(context, "Exam finalized: $reason", Toast.LENGTH_LONG).show()
                    onExamCompleted()
                }
                .addOnFailureListener {
                    isSubmittingExam = false
                    Toast.makeText(context, "Submission error: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    LaunchedEffect(remainingSeconds) {
        if (remainingSeconds > 0 && !isAutoSubmittedDueToViolation) {
            delay(1000L)
            remainingSeconds--
        } else if (remainingSeconds <= 0) {
            submitExamToDatabase("Time Expired")
        }
    }

    LaunchedEffect(isAutoSubmittedDueToViolation) {
        if (isAutoSubmittedDueToViolation) {
            submitExamToDatabase("Exceeded Anti-Cheat Strikes (App Minimized/Switched)")
        }
    }

    var showInAppDocViewer by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PROCTORED EXAM ENVIRONMENT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (strikeCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
                            ) {
                                Text(
                                    text = "Strikes: $strikeCount / $maxStrikes",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Leaving or switching apps will increment strikes. At 3 strikes, your paper auto-submits.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = assessment.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Category: ${assessment.type} • Total: ${assessment.maxScore} marks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        val mins = remainingSeconds / 60
                        val secs = remainingSeconds % 60
                        Surface(
                            color = if (mins < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                if (assessment.attachmentUrl.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Exam Paper: ${assessment.attachmentName.ifBlank { "Reference Document" }}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Button(
                                onClick = { showInAppDocViewer = !showInAppDocViewer },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    if (showInAppDocViewer) Icons.Default.VisibilityOff else Icons.Default.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (showInAppDocViewer) "Hide Paper" else "Read Paper Inside App", fontSize = 11.sp)
                            }
                        }
                    }

                    AnimatedVisibility(visible = showInAppDocViewer) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                                .background(Color.DarkGray)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.domStorageEnabled = true
                                        settings.loadWithOverviewMode = true
                                        settings.useWideViewPort = true
                                        webViewClient = WebViewClient()
                                        webChromeClient = WebChromeClient()
                                        loadUrl("https://docs.google.com/viewer?url=" + Uri.encode(assessment.attachmentUrl))
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (assessment.instructions.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("General Instructions:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(assessment.instructions, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    if (assessment.questions.isNotEmpty()) {
                        itemsIndexed(assessment.questions) { index, questionPrompt ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Question ${index + 1}:",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(text = questionPrompt, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    OutlinedTextField(
                                        value = answersMap[index] ?: "",
                                        onValueChange = { answersMap[index] = it },
                                        placeholder = { Text("Write your detailed solution here...") },
                                        minLines = 4,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Comprehensive Solution / Essay:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = answersMap[0] ?: "",
                                        onValueChange = { answersMap[0] = it },
                                        placeholder = { Text("Enter your full answers for this assessment...") },
                                        minLines = 10,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Answers cannot be altered after submission.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            enabled = !isSubmittingExam,
                            onClick = { submitExamToDatabase("Submitted by Student") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isSubmittingExam) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Finalize & Submit Test")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   OVERVIEW TAB
   ========================================================================================= */

@Composable
fun StudentAnalyticsOverview(
    contents: List<CourseContent>,
    completedIds: Set<String>,
    assessments: List<Assessment>,
    submittedIds: Set<String>,
    attendanceCount: Int,
    onNavigateToAssessments: () -> Unit,
    onNavigateToMaterials: () -> Unit
) {
    val totalMaterials = contents.size
    val completedMaterials = contents.count { completedIds.contains(it.id) }
    val materialRatio = if (totalMaterials > 0) completedMaterials.toFloat() / totalMaterials else 0f

    val totalAssignments = assessments.size
    val completedAssignments = assessments.count { submittedIds.contains(it.id) }
    val pendingAssessments = assessments.filterNot { submittedIds.contains(it.id) }

    val moduleWeeks = contents.map { it.moduleWeek.ifBlank { "Module" } }.distinct()
    val dynamicChartPoints = remember(contents, completedIds) {
        if (moduleWeeks.isEmpty()) {
            emptyList()
        } else {
            moduleWeeks.map { week ->
                val weekItems = contents.filter { (it.moduleWeek.ifBlank { "Module" }) == week }
                val weekDone = weekItems.count { completedIds.contains(it.id) }
                if (weekItems.isNotEmpty()) (weekDone.toFloat() / weekItems.size) * 100f else 0f
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (pendingAssessments.isNotEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToAssessments() }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.error),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.NotificationImportant, contentDescription = null, tint = Color.White)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Action Required: ${pendingAssessments.size} Pending Task(s)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val nextDue = pendingAssessments.firstOrNull()?.dueDate ?: "Upcoming"
                            Text(
                                text = "Nearest deadline: $nextDue",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    title = "Progress",
                    value = "${(materialRatio * 100).toInt()}%",
                    subtitle = "$completedMaterials of $totalMaterials items",
                    icon = Icons.Default.TrendingUp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Tasks Due",
                    value = "${pendingAssessments.size}",
                    subtitle = "$completedAssignments completed",
                    icon = Icons.Default.PendingActions,
                    color = if (pendingAssessments.isNotEmpty()) Color(0xFFE65100) else Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "Attendance",
                    value = "$attendanceCount",
                    subtitle = "Marked present",
                    icon = Icons.Default.VerifiedUser,
                    color = Color(0xFF1565C0),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Curriculum Completion Breakdown", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    val slices = listOf(
                        PieSlice("Read Materials", completedMaterials.toFloat(), Color(0xFF4CAF50)),
                        PieSlice("Submitted Work", completedAssignments.toFloat(), Color(0xFF2196F3)),
                        PieSlice("Pending Readings", (totalMaterials - completedMaterials).coerceAtLeast(0).toFloat(), Color(0xFFFFB74D)),
                        PieSlice("Pending Deadlines", pendingAssessments.size.toFloat(), Color(0xFFE53935))
                    )

                    if ((totalMaterials + totalAssignments) == 0) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("No materials or assessments uploaded yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            LmsDonutChart(
                                slices = slices,
                                modifier = Modifier.size(130.dp),
                                centerLabel = "${(materialRatio * 100).toInt()}%",
                                centerSub = "Done"
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                slices.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(s.color))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${s.label} (${s.value.toInt()})", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            LmsCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Module-by-Module Progress Trend", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(18.dp))

                    if (dynamicChartPoints.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No modules posted yet.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LmsLineChart(
                            dataPoints = dynamicChartPoints,
                            labels = moduleWeeks,
                            lineColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().height(130.dp)
                        )
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   ASSESSMENTS TAB
   ========================================================================================= */

@Composable
fun AssessmentsTabContent(
    assessments: List<Assessment>,
    submittedIds: Set<String>,
    submissionsMap: Map<String, StudentAssessmentSubmission>,
    onOpenUrl: (String) -> Unit,
    onStartAssessment: (Assessment) -> Unit
) {
    if (assessments.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No assessments or exams posted yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Continuous Assessments & Timed Examinations",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(assessments, key = { it.id }) { ass ->
                val isDone = submittedIds.contains(ass.id)
                val submission = submissionsMap[ass.id]
                val isExam = ass.type.contains("CAT", true) ||
                        ass.type.contains("Exam", true) ||
                        ass.type.contains("Test", true)

                LmsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ass.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = if (isDone) Color(0xFFE8F5E9) else if (isExam) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (isDone) "✓ Submitted" else ass.type,
                                    color = if (isDone) Color(0xFF2E7D32) else if (isExam) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isExam) "⏱ Real-Time Exam • Duration: ${ass.durationMinutes} min • Max: ${ass.maxScore} marks" else "Max Score: ${ass.maxScore} marks • Due: ${ass.dueDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isExam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        if (ass.instructions.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(ass.instructions, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // SUBMISSION RESULTS & LIVE GRADES FROM DB
                        if (isDone) {
                            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    color = Color(0xFFE8F5E9),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "Paper Finalized",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF2E7D32),
                                                    fontSize = 13.sp
                                                )
                                            }

                                            if (submission?.isGraded == true && submission.score != null) {
                                                Surface(
                                                    color = Color(0xFF2E7D32),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = "Score: ${submission.score} / ${ass.maxScore}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            } else {
                                                Text("Pending Grade", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        if (submission?.feedback?.isNotBlank() == true) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Tutor Remarks: ${submission.feedback}",
                                                fontSize = 12.sp,
                                                color = Color(0xFF1B5E20),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = { onStartAssessment(ass) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isExam) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(if (isExam) Icons.Default.PlayCircleFilled else Icons.Default.UploadFile, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isExam) "Enter Proctored Exam (Lock Screen)" else "Submit Assignment Solution")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   LEARNING MATERIALS TAB
   ========================================================================================= */

@Composable
fun MaterialsTabContent(
    contents: List<CourseContent>,
    completedIds: Set<String>,
    studentUid: String,
    onOpenUrl: (String) -> Unit
) {
    if (contents.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No learning materials posted yet.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(contents, key = { it.id }) { item ->
                val isDone = completedIds.contains(item.id)
                val hasUrl = item.resourceUrl.isNotBlank()

                LmsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${item.moduleWeek}: ${item.title}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(onClick = {}, label = { Text(item.mediaType, fontSize = 11.sp) })
                        }

                        if (item.summary.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasUrl) {
                                Button(
                                    onClick = { onOpenUrl(item.resourceUrl) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Material")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    val docRef = FirebaseService.db.collection("users").document(studentUid)
                                        .collection("completed_materials").document(item.id)
                                    if (isDone) {
                                        docRef.delete()
                                    } else {
                                        docRef.set(mapOf("completedAt" to FieldValue.serverTimestamp()))
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = if (hasUrl) Modifier.wrapContentWidth() else Modifier.fillMaxWidth()
                            ) {
                                Icon(if (isDone) Icons.Default.CheckCircle else Icons.Default.Check, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isDone) "Done" else "Mark Done")
                            }
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   LIVE SESSIONS TAB
   ========================================================================================= */

@Composable
fun LiveSessionsTabContent(
    liveSessions: List<LiveSession>,
    onJoin: (String) -> Unit
) {
    if (liveSessions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No live video lectures scheduled.", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(liveSessions, key = { it.id }) { session ->
                LmsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            SuggestionChip(onClick = {}, label = { Text(session.meetingPlatform) })
                        }
                        Text(
                            text = "Schedule: ${session.date} at ${session.time}",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onJoin(session.meetingUrl) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.VideoCall, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Join ${session.meetingPlatform} Class")
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   INQUIRIES TAB
   ========================================================================================= */

@Composable
fun InquiriesTabContent(
    inquiries: List<StudentInquiry>,
    onAskQuestionClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp, 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("My Inquiries & Answers", fontWeight = FontWeight.Bold)
                Button(onClick = onAskQuestionClick, shape = RoundedCornerShape(10.dp)) {
                    Icon(Icons.Default.QuestionAnswer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ask Question")
                }
            }
        }

        if (inquiries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No questions asked yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(inquiries, key = { it.id }) { inq ->
                    LmsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(inq.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Q: ${inq.message}", style = MaterialTheme.typography.bodyMedium)
                            if (inq.reply.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Tutor Response:\n${inq.reply}",
                                        modifier = Modifier.padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall
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

/* =========================================================================================
   ATTENDANCE TAB
   ========================================================================================= */

@Composable
fun AttendanceTabContent(
    enrolledCourseId: String,
    studentUid: String,
    studentName: String,
    attendanceList: List<AttendanceEntry>
) {
    val context = LocalContext.current
    var activeAttendanceSessions by remember { mutableStateOf<List<AttendanceSession>>(emptyList()) }
    var myMarkedSessionIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(enrolledCourseId) {
        if (enrolledCourseId.isBlank()) return@LaunchedEffect
        FirebaseService.db.collection("courses").document(enrolledCourseId)
            .collection("attendance_sessions")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { s, _ ->
                activeAttendanceSessions = s?.documents?.mapNotNull { d ->
                    AttendanceSession(
                        id = d.id,
                        courseId = enrolledCourseId,
                        title = d.getString("title") ?: "",
                        expiresAtTimestamp = safeToLong(d.get("expiresAtTimestamp")),
                        durationMinutes = safeToInt(d.get("durationMinutes"), 15),
                        isActive = d.getBoolean("isActive") ?: true
                    )
                } ?: emptyList()
            }

        FirebaseService.db.collection("users").document(studentUid)
            .collection("marked_attendance")
            .addSnapshotListener { s, _ ->
                myMarkedSessionIds = s?.documents?.map { it.id }?.toSet() ?: emptySet()
            }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Active Attendance Windows", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }

        if (activeAttendanceSessions.isEmpty()) {
            item {
                Text("No open attendance sessions right now.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(activeAttendanceSessions, key = { it.id }) { session ->
                val isExpired = session.expiresAtTimestamp > 0 && System.currentTimeMillis() > session.expiresAtTimestamp
                val isMarked = myMarkedSessionIds.contains(session.id)
                val remainingSeconds = if (session.expiresAtTimestamp > 0) ((session.expiresAtTimestamp - System.currentTimeMillis()) / 1000).coerceAtLeast(0) else 0

                LmsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(session.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.width(8.dp))
                            AssistChip(
                                onClick = {},
                                label = { Text(if (!isExpired && !isMarked && remainingSeconds > 0) "${remainingSeconds / 60}m left" else if (isMarked) "Present" else "Expired") }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            enabled = !isExpired && !isMarked,
                            onClick = {
                                val batch = FirebaseService.db.batch()
                                val myRecordRef = FirebaseService.db.collection("users").document(studentUid)
                                    .collection("marked_attendance").document(session.id)
                                val courseAttendanceRef = FirebaseService.db.collection("courses").document(enrolledCourseId)
                                    .collection("attendance").document("${session.id}_$studentUid")

                                batch.set(myRecordRef, mapOf("markedAt" to FieldValue.serverTimestamp()))
                                batch.set(courseAttendanceRef, mapOf(
                                    "studentId" to studentUid,
                                    "studentName" to studentName,
                                    "sessionId" to session.id,
                                    "date" to session.title,
                                    "isPresent" to true,
                                    "markedAt" to FieldValue.serverTimestamp()
                                ))

                                batch.commit().addOnSuccessListener {
                                    Toast.makeText(context, "Attendance confirmed! Marked present.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMarked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(if (isMarked) Icons.Default.CheckCircle else Icons.Default.HowToReg, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isMarked) "Present Confirmed" else if (isExpired) "Session Expired" else "Mark Present Now")
                        }
                    }
                }
            }
        }
    }
}

/* =========================================================================================
   CANVAS CHARTS & STAT CARDS
   ========================================================================================= */

data class PieSlice(val label: String, val value: Float, val color: Color)

@Composable
fun LmsDonutChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    centerLabel: String = "",
    centerSub: String = ""
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val strokeWidth = 18.dp.toPx()
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
fun LmsLineChart(
    dataPoints: List<Float>,
    labels: List<String>,
    lineColor: Color,
    modifier: Modifier = Modifier
) {
    if (dataPoints.isEmpty()) return

    val maxVal = (dataPoints.maxOrNull() ?: 100f).coerceAtLeast(10f)

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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = if (labels.size > 1) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            labels.forEach { label ->
                Text(
                    text = label,
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
fun StatCard(
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