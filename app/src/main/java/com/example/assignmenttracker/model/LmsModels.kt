package com.example.assignmenttracker.model

data class Course(
    val id: String = "",
    val code: String = "",
    val title: String = "",
    val department: String = "",
    val description: String = ""
)

data class UserAccount(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val role: String = "",
    val staffId: String = "",
    val studentRegNo: String = "",
    val assignedCourseId: String = "",
    val assignedCourseTitle: String = "",
    val isActive: Boolean = true
)

data class CourseEnrollment(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val studentEmail: String = "",
    val courseId: String = "",
    val courseTitle: String = "",
    val courseCode: String = "",
    val status: String = "approved"
)

data class Assessment(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val type: String = "Assignment", // "Assignment", "CAT 1", "CAT 2", "Final Exam"
    val dueDate: String = "",
    val instructions: String = "",
    val maxScore: Int = 100,
    val durationMinutes: Int = 60,
    val attachmentUrl: String = "",
    val attachmentName: String = "",
    val questions: List<String> = emptyList(),
    val isActive: Boolean = true
)

data class CourseContent(
    val id: String = "",
    val courseId: String = "",
    val moduleWeek: String = "",
    val title: String = "",
    val mediaType: String = "PDF Document",
    val resourceUrl: String = "",
    val fileName: String = "",
    val summary: String = "",
    val isActive: Boolean = true
)

data class LiveSession(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val meetingPlatform: String = "Google Meet",
    val meetingUrl: String = "",
    val agenda: String = "",
    val linkedAssignmentId: String = "",
    val isActive: Boolean = true
)

data class AttendanceSession(
    val id: String = "",
    val courseId: String = "",
    val title: String = "",
    val expiresAtTimestamp: Long = 0L,
    val durationMinutes: Int = 15,
    val isActive: Boolean = true
)

data class AttendanceEntry(
    val id: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val date: String = "",
    val markedAt: String = "",
    val isPresent: Boolean = false
)

data class StudentInquiry(
    val id: String = "",
    val courseId: String = "",
    val studentName: String = "",
    val subject: String = "",
    val message: String = "",
    val reply: String = "",
    val isResolved: Boolean = false
)
