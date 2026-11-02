package com.example.assignmenttracker.ui.tutor

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.Assessment
import com.example.assignmenttracker.ui.components.LmsCard
import com.google.firebase.firestore.FieldValue

private fun safeToInt(value: Any?, default: Int = 0): Int {
    return when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull() ?: default
        else -> default
    }
}

data class StudentSubmissionRecord(
    val studentId: String = "",
    val studentName: String = "",
    val content: String = "",
    val submittedAt: String = "",
    val score: Int? = null,
    val feedback: String = "",
    val strikes: Int = 0,
    val terminationReason: String = "",
    val isGraded: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TutorAssessmentsTab(
    assessments: List<Assessment> = emptyList(),
    courseId: String = assessments.firstOrNull()?.courseId ?: ""
) {
    val context = LocalContext.current
    val currentUid = FirebaseService.auth.currentUser?.uid ?: ""

    // Dynamic Database State
    var activeCourseId by remember { mutableStateOf(courseId) }
    var dbAssessments by remember { mutableStateOf<List<Assessment>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf("All") }
    val filterTypes = listOf("All", "Assignment", "CAT", "Final Exam")

    // Modal Dialog States
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingAssessment by remember { mutableStateOf<Assessment?>(null) }
    var reviewingAssessment by remember { mutableStateOf<Assessment?>(null) }

    // Assessment Input Form States
    var formTitle by remember { mutableStateOf("") }
    var formType by remember { mutableStateOf("Assignment") }
    var formDueDate by remember { mutableStateOf("") }
    var formDueTime by remember { mutableStateOf("23:59") }
    var formDurationMinutes by remember { mutableStateOf("60") }
    var formMaxScore by remember { mutableStateOf("100") }
    var formInstructions by remember { mutableStateOf("") }
    var formAttachmentUrl by remember { mutableStateOf("") }
    var formAttachmentName by remember { mutableStateOf("") }
    var isUploadingAttachment by remember { mutableStateOf(false) }
    var questionsList by remember { mutableStateOf(listOf("")) }

    // 1. Resolve Course ID dynamically if not passed directly
    LaunchedEffect(courseId, currentUid) {
        if (courseId.isNotBlank()) {
            activeCourseId = courseId
        } else if (currentUid.isNotBlank()) {
            FirebaseService.db.collection("courses")
                .whereEqualTo("tutorId", currentUid)
                .get()
                .addOnSuccessListener { s ->
                    val first = s.documents.firstOrNull()?.id
                    if (!first.isNullOrBlank()) {
                        activeCourseId = first
                    } else {
                        FirebaseService.db.collection("courses")
                            .whereEqualTo("createdBy", currentUid)
                            .get()
                            .addOnSuccessListener { s2 ->
                                activeCourseId = s2.documents.firstOrNull()?.id ?: ""
                            }
                    }
                }
        }
    }

    // 2. Fetch Assessments dynamically from Firestore
    LaunchedEffect(activeCourseId) {
        if (activeCourseId.isNotBlank()) {
            FirebaseService.db.collection("courses").document(activeCourseId)
                .collection("assessments")
                .addSnapshotListener { s, _ ->
                    if (s != null) {
                        dbAssessments = s.documents.mapNotNull { d ->
                            @Suppress("UNCHECKED_CAST")
                            val qList = d.get("questions") as? List<String> ?: emptyList()
                            Assessment(
                                id = d.id,
                                courseId = activeCourseId,
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
                        }
                    }
                }
        }
    }

    val liveAssessmentsList = if (assessments.isNotEmpty()) assessments else dbAssessments

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isUploadingAttachment = true
            FirebaseService.uploadLocalFileSafe(
                context = context,
                fileUri = uri,
                folder = "assessment_papers",
                onProgress = {},
                onSuccess = { url, name ->
                    isUploadingAttachment = false
                    formAttachmentUrl = url
                    formAttachmentName = name
                    Toast.makeText(context, "Question paper attached!", Toast.LENGTH_SHORT).show()
                },
                onFailure = {
                    isUploadingAttachment = false
                    Toast.makeText(context, "Attachment failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun openAttachment(url: String) {
        if (url.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open attachment", Toast.LENGTH_SHORT).show()
        }
    }

    val filteredList = remember(liveAssessmentsList, selectedFilter) {
        if (selectedFilter == "All") liveAssessmentsList
        else liveAssessmentsList.filter { it.type.contains(selectedFilter, ignoreCase = true) }
    }

    val totalActive = liveAssessmentsList.count { it.isActive }
    val totalExamsAndCats = liveAssessmentsList.count { it.type.contains("CAT", true) || it.type.contains("Exam", true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Assessment Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Manage real-time exams, assignments & grading",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            editingAssessment = null
                            formTitle = ""
                            formType = "CAT 1"
                            formDueDate = ""
                            formDueTime = "23:59"
                            formDurationMinutes = "60"
                            formMaxScore = "100"
                            formInstructions = ""
                            formAttachmentUrl = ""
                            formAttachmentName = ""
                            questionsList = listOf("")
                            showCreateDialog = true
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set New Test")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Total Set", style = MaterialTheme.typography.labelSmall)
                            Text("${liveAssessmentsList.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Active / Open", style = MaterialTheme.typography.labelSmall)
                            Text("$totalActive", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Proctored Tests", style = MaterialTheme.typography.labelSmall)
                            Text("$totalExamsAndCats", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filterTypes.forEach { type ->
                FilterChip(
                    selected = selectedFilter == type,
                    onClick = { selectedFilter = type },
                    label = { Text(type, fontSize = 12.sp) }
                )
            }
        }

        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No assessments found for '$selectedFilter'", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredList, key = { it.id }) { item ->
                    var submissionCount by remember { mutableIntStateOf(0) }
                    val currentCourse = if (activeCourseId.isNotBlank()) activeCourseId else item.courseId

                    LaunchedEffect(item.id, currentCourse) {
                        if (currentCourse.isNotBlank()) {
                            FirebaseService.db.collection("courses").document(currentCourse)
                                .collection("assessments").document(item.id)
                                .collection("submissions")
                                .addSnapshotListener { s, _ ->
                                    submissionCount = s?.documents?.size ?: 0
                                }
                        }
                    }

                    LmsCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Duration: ${item.durationMinutes} min • Max: ${item.maxScore} pts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(item.type, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Due: ${item.dueDate.ifBlank { "Open Deadline" }}", fontSize = 12.sp)
                                    Text("$submissionCount submitted", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            if (item.attachmentUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { openAttachment(item.attachmentUrl) },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paper: ${item.attachmentName.ifBlank { "Document.pdf" }}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { reviewingAssessment = item },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grade ($submissionCount)")
                                }

                                Row {
                                    // Live Edit Button
                                    IconButton(
                                        onClick = {
                                            editingAssessment = item
                                            formTitle = item.title
                                            formType = item.type
                                            formDurationMinutes = item.durationMinutes.toString()
                                            formMaxScore = item.maxScore.toString()
                                            val parts = item.dueDate.split(" ")
                                            formDueDate = parts.firstOrNull() ?: ""
                                            formDueTime = if (parts.size > 1) parts[1] else "23:59"
                                            formInstructions = item.instructions
                                            formAttachmentUrl = item.attachmentUrl
                                            formAttachmentName = item.attachmentName
                                            questionsList = if (item.questions.isNotEmpty()) item.questions else listOf("")
                                            showCreateDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                    }

                                    // Remind Students
                                    IconButton(
                                        onClick = {
                                            FirebaseService.broadcastToEnrolledStudents(
                                                courseId = currentCourse,
                                                type = "assignment_reminder",
                                                title = "Reminder: ${item.title}",
                                                message = "Assessment '${item.title}' is active. Please complete before the deadline."
                                            )
                                            Toast.makeText(context, "Reminder sent to enrolled learners!", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(Icons.Default.NotificationsActive, contentDescription = "Remind", tint = MaterialTheme.colorScheme.secondary)
                                    }

                                    // Delete from DB
                                    IconButton(
                                        onClick = {
                                            FirebaseService.db.collection("courses").document(currentCourse)
                                                .collection("assessments").document(item.id)
                                                .delete()
                                                .addOnSuccessListener {
                                                    Toast.makeText(context, "Assessment deleted", Toast.LENGTH_SHORT).show()
                                                }
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Create or Edit Assessment (Connected to DB)
    if (showCreateDialog) {
        val isEditing = editingAssessment != null
        AlertDialog(
            onDismissRequest = { if (!isUploadingAttachment) showCreateDialog = false },
            title = { Text(if (isEditing) "Edit Assessment" else "Set Real-Time Test / Exam", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Type:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Assignment", "CAT 1", "CAT 2", "Final Exam").forEach { t ->
                            FilterChip(selected = formType == t, onClick = { formType = t }, label = { Text(t) })
                        }
                    }

                    OutlinedTextField(
                        value = formTitle,
                        onValueChange = { formTitle = it },
                        label = { Text("Title (e.g. Midterm Test)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = formDurationMinutes,
                            onValueChange = { formDurationMinutes = it },
                            label = { Text("Duration (Mins)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = formMaxScore,
                            onValueChange = { formMaxScore = it },
                            label = { Text("Total Marks") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = formDueDate,
                            onValueChange = { formDueDate = it },
                            label = { Text("Date (YYYY-MM-DD)") },
                            modifier = Modifier.weight(1.3f)
                        )
                        OutlinedTextField(
                            value = formDueTime,
                            onValueChange = { formDueTime = it },
                            label = { Text("Time (HH:MM)") },
                            modifier = Modifier.weight(0.7f)
                        )
                    }

                    OutlinedTextField(
                        value = formInstructions,
                        onValueChange = { formInstructions = it },
                        label = { Text("Instructions") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Document attachment
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Attach Question Paper (PDF / Doc):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = {
                                    docPickerLauncher.launch(
                                        arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Document")
                            }

                            if (isUploadingAttachment) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            } else if (formAttachmentName.isNotBlank()) {
                                Text("Attached: $formAttachmentName", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }

                    // Dynamic Question Items (Editable)
                    Text("Breakdown Questions (Rendered for Student Solutions):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    questionsList.forEachIndexed { index, qText ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = qText,
                                onValueChange = { newQ ->
                                    questionsList = questionsList.toMutableList().also { it[index] = newQ }
                                },
                                label = { Text("Question ${index + 1}") },
                                placeholder = { Text("Write problem prompt...") },
                                modifier = Modifier.weight(1f)
                            )
                            if (questionsList.size > 1) {
                                IconButton(onClick = {
                                    questionsList = questionsList.toMutableList().also { it.removeAt(index) }
                                }) {
                                    Icon(Icons.Default.RemoveCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    TextButton(onClick = { questionsList = questionsList + "" }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Question Item")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isUploadingAttachment,
                    onClick = {
                        val activeCourse = if (activeCourseId.isNotBlank()) activeCourseId else editingAssessment?.courseId ?: ""
                        if (formTitle.isBlank() || activeCourse.isBlank()) {
                            Toast.makeText(context, "Title and Course are required", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val fullDeadline = if (formDueDate.isNotBlank()) "${formDueDate.trim()} ${formDueTime.trim()}" else "No fixed deadline"
                        val data = hashMapOf(
                            "courseId" to activeCourse,
                            "title" to formTitle.trim(),
                            "type" to formType,
                            "dueDate" to fullDeadline,
                            "durationMinutes" to (formDurationMinutes.toIntOrNull() ?: 60),
                            "instructions" to formInstructions.trim(),
                            "maxScore" to (formMaxScore.toIntOrNull() ?: 100),
                            "attachmentUrl" to formAttachmentUrl,
                            "attachmentName" to formAttachmentName,
                            "questions" to questionsList.filter { it.isNotBlank() },
                            "isActive" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        if (isEditing) {
                            FirebaseService.db.collection("courses").document(activeCourse)
                                .collection("assessments").document(editingAssessment!!.id)
                                .update(data as Map<String, Any>)
                                .addOnSuccessListener {
                                    showCreateDialog = false
                                    Toast.makeText(context, "Assessment updated!", Toast.LENGTH_SHORT).show()
                                }
                        } else {
                            data["createdAt"] = FieldValue.serverTimestamp()
                            FirebaseService.db.collection("courses").document(activeCourse)
                                .collection("assessments").add(data)
                                .addOnSuccessListener {
                                    showCreateDialog = false
                                    Toast.makeText(context, "Assessment published!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                ) {
                    Text(if (isEditing) "Save Changes" else "Publish Test")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !isUploadingAttachment) { Text("Cancel") }
            }
        )
    }

    // Modal: Real-Time Grading Hub (Displays Strikes, Violations & Grades to DB)
    reviewingAssessment?.let { targetAssessment ->
        var submissionsList by remember { mutableStateOf<List<StudentSubmissionRecord>>(emptyList()) }
        var isLoadingSubmissions by remember { mutableStateOf(true) }
        val activeCourse = if (activeCourseId.isNotBlank()) activeCourseId else targetAssessment.courseId

        LaunchedEffect(targetAssessment.id, activeCourse) {
            FirebaseService.db.collection("courses").document(activeCourse)
                .collection("assessments").document(targetAssessment.id)
                .collection("submissions")
                .addSnapshotListener { snapshot, _ ->
                    isLoadingSubmissions = false
                    submissionsList = snapshot?.documents?.mapNotNull { d ->
                        StudentSubmissionRecord(
                            studentId = d.id,
                            studentName = d.getString("studentName") ?: "Student",
                            content = d.getString("content") ?: "",
                            submittedAt = d.getTimestamp("submittedAt")?.toDate()?.toString()?.take(16) ?: "Recently",
                            score = d.getLong("score")?.toInt(),
                            feedback = d.getString("feedback") ?: "",
                            strikes = d.getLong("strikes")?.toInt() ?: 0,
                            terminationReason = d.getString("terminationReason") ?: "Normal Submit",
                            isGraded = d.getBoolean("isGraded") ?: (d.getLong("score") != null)
                        )
                    } ?: emptyList()
                }
        }

        AlertDialog(
            onDismissRequest = { reviewingAssessment = null },
            title = {
                Column {
                    Text("Grading Hub: ${targetAssessment.title}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Total Marks: ${targetAssessment.maxScore} • ${submissionsList.size} submitted", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                    if (isLoadingSubmissions) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (submissionsList.isEmpty()) {
                        Text("No submissions submitted yet.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            items(submissionsList, key = { it.studentId }) { sub ->
                                var studentScore by remember(sub.score) { mutableStateOf(sub.score?.toString() ?: "") }
                                var tutorFeedback by remember(sub.feedback) { mutableStateOf(sub.feedback) }
                                var isSavingGrade by remember { mutableStateOf(false) }

                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(sub.studentName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            AssistChip(
                                                onClick = {},
                                                label = { Text(if (sub.isGraded) "Graded (${sub.score}/${targetAssessment.maxScore})" else "Pending Grade") }
                                            )
                                        }

                                        // Violation Badge
                                        if (sub.strikes > 0) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.errorContainer,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "⚠️ Proctored Alert: ${sub.strikes} screen switches (${sub.terminationReason})",
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = sub.content,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = studentScore,
                                                onValueChange = { studentScore = it },
                                                label = { Text("Score") },
                                                singleLine = true,
                                                modifier = Modifier.weight(1f)
                                            )

                                            Button(
                                                enabled = !isSavingGrade,
                                                onClick = {
                                                    val awarded = studentScore.toIntOrNull()
                                                    if (awarded == null) {
                                                        Toast.makeText(context, "Enter a valid mark", Toast.LENGTH_SHORT).show()
                                                        return@Button
                                                    }
                                                    isSavingGrade = true
                                                    FirebaseService.db.collection("courses").document(activeCourse)
                                                        .collection("assessments").document(targetAssessment.id)
                                                        .collection("submissions").document(sub.studentId)
                                                        .update(
                                                            mapOf(
                                                                "score" to awarded,
                                                                "feedback" to tutorFeedback.trim(),
                                                                "isGraded" to true,
                                                                "gradedAt" to FieldValue.serverTimestamp()
                                                            )
                                                        )
                                                        .addOnSuccessListener {
                                                            isSavingGrade = false
                                                            Toast.makeText(context, "Grade saved in DB!", Toast.LENGTH_SHORT).show()
                                                        }
                                                        .addOnFailureListener {
                                                            isSavingGrade = false
                                                            Toast.makeText(context, "Failed: ${it.localizedMessage}", Toast.LENGTH_SHORT).show()
                                                        }
                                                }
                                            ) {
                                                Text("Grade")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        OutlinedTextField(
                                            value = tutorFeedback,
                                            onValueChange = { tutorFeedback = it },
                                            label = { Text("Feedback / Remarks") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { reviewingAssessment = null }) { Text("Close") }
            }
        )
    }
}