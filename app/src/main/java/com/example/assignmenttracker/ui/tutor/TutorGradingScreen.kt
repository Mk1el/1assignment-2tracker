package com.example.assignmenttracker.ui.tutor

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.model.Assessment
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration

private data class GradingSubmission(
    val studentId: String,
    val studentName: String,
    val content: String,
    val submittedAt: String,
    val score: Int?,
    val feedback: String,
    val isGraded: Boolean
)

/**
 * Tutor grading screen.
 *
 * Firestore path:
 * courses/{courseId}/assessments/{assessmentId}/submissions/{studentUid}
 *
 * Add this screen to the tutor dashboard and pass the real course document ID.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TutorGradingScreen(
    courseId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tutorUid = FirebaseService.auth.currentUser?.uid.orEmpty()

    var assessments by remember { mutableStateOf<List<Assessment>>(emptyList()) }
    var selectedAssessment by remember { mutableStateOf<Assessment?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(courseId) {
        if (courseId.isBlank()) {
            isLoading = false
            loadError = "No course was selected. Open the grading screen with a valid course ID."
            onDispose { }
        } else {
            val registration: ListenerRegistration =
                FirebaseService.db.collection("courses")
                    .document(courseId)
                    .collection("assessments")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            isLoading = false
                            loadError = error.localizedMessage ?: "Could not load assessments."
                        } else {
                            loadError = null
                            assessments = snapshot?.documents?.map { d ->
                                @Suppress("UNCHECKED_CAST")
                                val questions = d.get("questions") as? List<String> ?: emptyList()

                                Assessment(
                                    id = d.id,
                                    courseId = courseId,
                                    title = d.getString("title") ?: "Untitled assessment",
                                    type = d.getString("type") ?: "Assignment",
                                    dueDate = d.getString("dueDate") ?: "",
                                    instructions = d.getString("instructions") ?: "",
                                    maxScore = (d.get("maxScore") as? Number)?.toInt() ?: 100,
                                    durationMinutes = (d.get("durationMinutes") as? Number)?.toInt() ?: 60,
                                    attachmentUrl = d.getString("attachmentUrl") ?: "",
                                    attachmentName = d.getString("attachmentName") ?: "",
                                    questions = questions,
                                    isActive = d.getBoolean("isActive") ?: true
                                )
                            } ?: emptyList()
                            isLoading = false
                        }
                    }

            onDispose { registration.remove() }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Assessment Grading",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Open an assessment to review each student's work, award marks and give feedback.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            loadError != null -> Text(
                text = loadError!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(8.dp)
            )

            assessments.isEmpty() -> Text(
                "There are no assessments in this course yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(assessments, key = { it.id }) { assessment ->
                    AssessmentToGradeCard(
                        courseId = courseId,
                        assessment = assessment,
                        onOpen = { selectedAssessment = assessment }
                    )
                }
            }
        }
    }

    selectedAssessment?.let { assessment ->
        AssessmentGradingDialog(
            courseId = courseId,
            assessment = assessment,
            tutorUid = tutorUid,
            onDismiss = { selectedAssessment = null }
        )
    }
}

@Composable
private fun AssessmentToGradeCard(
    courseId: String,
    assessment: Assessment,
    onOpen: () -> Unit
) {
    var totalSubmissions by remember(assessment.id, courseId) { mutableIntStateOf(0) }
    var gradedSubmissions by remember(assessment.id, courseId) { mutableIntStateOf(0) }

    DisposableEffect(assessment.id, courseId) {
        val registration = FirebaseService.db.collection("courses")
            .document(courseId)
            .collection("assessments")
            .document(assessment.id)
            .collection("submissions")
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty()
                totalSubmissions = docs.size
                gradedSubmissions = docs.count {
                    it.getBoolean("isGraded") == true ||
                            it.getLong("score") != null
                }
            }

        onDispose { registration.remove() }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Assignment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(assessment.title, fontWeight = FontWeight.Bold)
                    Text(
                        "${assessment.type} • Maximum marks: ${assessment.maxScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Submissions: $totalSubmissions")
                Text(
                    "Graded: $gradedSubmissions / $totalSubmissions",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (totalSubmissions > 0) {
                LinearProgressIndicator(
                    progress = gradedSubmissions.toFloat() / totalSubmissions.toFloat(),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.FactCheck, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Review & Grade ($totalSubmissions)")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssessmentGradingDialog(
    courseId: String,
    assessment: Assessment,
    tutorUid: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var submissions by remember(assessment.id, courseId) {
        mutableStateOf<List<GradingSubmission>>(emptyList())
    }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(assessment.id, courseId) {
        val registration = FirebaseService.db.collection("courses")
            .document(courseId)
            .collection("assessments")
            .document(assessment.id)
            .collection("submissions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    isLoading = false
                    loadError = error.localizedMessage ?: "Could not load student submissions."
                } else {
                    loadError = null
                    submissions = snapshot?.documents?.map { d ->
                        GradingSubmission(
                            studentId = d.id,
                            studentName = d.getString("studentName") ?: "Student",
                            content = d.getString("content") ?: "",
                            submittedAt = d.getTimestamp("submittedAt")
                                ?.toDate()?.toString()?.take(16) ?: "Submission time unavailable",
                            score = d.getLong("score")?.toInt(),
                            feedback = d.getString("feedback") ?: "",
                            isGraded = d.getBoolean("isGraded")
                                ?: (d.getLong("score") != null)
                        )
                    } ?: emptyList()
                    isLoading = false
                }
            }

        onDispose { registration.remove() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Grade: ${assessment.title}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Award marks out of ${assessment.maxScore}. Each student is graded separately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            when {
                isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                loadError != null -> Text(
                    loadError!!,
                    color = MaterialTheme.colorScheme.error
                )

                submissions.isEmpty() -> Text("No students have submitted this assessment yet.")

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(submissions, key = { it.studentId }) { submission ->
                        IndividualStudentGradingCard(
                            courseId = courseId,
                            assessment = assessment,
                            submission = submission,
                            tutorUid = tutorUid,
                            onSaved = onDismiss
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun IndividualStudentGradingCard(
    courseId: String,
    assessment: Assessment,
    submission: GradingSubmission,
    tutorUid: String,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var marks by remember(submission.studentId, submission.score) {
        mutableStateOf(submission.score?.toString() ?: "")
    }
    var feedback by remember(submission.studentId, submission.feedback) {
        mutableStateOf(submission.feedback)
    }
    var isSaving by remember(submission.studentId) { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(submission.studentName, fontWeight = FontWeight.Bold)
                    Text(
                        "Submitted: ${submission.submittedAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            if (submission.isGraded) {
                                "Graded: ${submission.score}/${assessment.maxScore}"
                            } else {
                                "Awaiting grade"
                            }
                        )
                    }
                )
            }

            Text("Student's submitted work", fontWeight = FontWeight.SemiBold)

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = submission.content.ifBlank {
                        "No text submission was provided. Check whether the student submitted a document link or attachment."
                    },
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedTextField(
                value = marks,
                onValueChange = { input ->
                    if (input.all { it.isDigit() }) marks = input
                },
                label = { Text("Marks awarded (out of ${assessment.maxScore})") },
                placeholder = { Text("Enter student's marks") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                label = { Text("Individual feedback / tutor remarks") },
                placeholder = { Text("Explain what the student did well and what needs improvement...") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val awardedMarks = marks.toIntOrNull()

                    if (awardedMarks == null) {
                        Toast.makeText(context, "Enter the student's marks.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    if (awardedMarks < 0 || awardedMarks > assessment.maxScore) {
                        Toast.makeText(
                            context,
                            "Marks must be between 0 and ${assessment.maxScore}.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    if (tutorUid.isBlank()) {
                        Toast.makeText(context, "Tutor is not signed in.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSaving = true

                    val submissionRef = FirebaseService.db
                        .collection("courses")
                        .document(courseId)
                        .collection("assessments")
                        .document(assessment.id)
                        .collection("submissions")
                        .document(submission.studentId)

                    submissionRef.update(
                        mapOf(
                            "score" to awardedMarks,
                            "feedback" to feedback.trim(),
                            "isGraded" to true,
                            "gradedBy" to tutorUid,
                            "gradedAt" to FieldValue.serverTimestamp()
                        )
                    ).addOnSuccessListener {
                        isSaving = false
                        Toast.makeText(
                            context,
                            "Marks and feedback saved for ${submission.studentName}.",
                            Toast.LENGTH_LONG
                        ).show()
                        onSaved()
                    }.addOnFailureListener { error ->
                        isSaving = false
                        Toast.makeText(
                            context,
                            "Could not save grade: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Saving...")
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Grade & Feedback")
                }
            }
        }
    }
}
