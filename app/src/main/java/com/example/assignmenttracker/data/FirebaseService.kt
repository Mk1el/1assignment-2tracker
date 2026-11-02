package com.example.assignmenttracker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.assignmenttracker.model.*
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

object FirebaseService {
    val auth: FirebaseAuth get() = Firebase.auth
    val db: FirebaseFirestore get() = Firebase.firestore

    fun uploadLocalFileSafe(
        context: Context,
        fileUri: Uri,
        folder: String = "media",
        onProgress: (Int) -> Unit = {},
        onSuccess: (downloadUrl: String, fileName: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var resolvedName = "audio_${System.currentTimeMillis()}.mp3"
                var fileSize: Long = 0

                context.contentResolver.query(fileUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) resolvedName = cursor.getString(nameIdx) ?: resolvedName
                        if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                    }
                }

                val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
                val inputStream: InputStream? = context.contentResolver.openInputStream(fileUri)

                if (inputStream == null) {
                    withContext(Dispatchers.Main) {
                        onFailure(Exception("Could not open audio file stream."))
                    }
                    return@launch
                }

                val boundary = "Boundary-${System.currentTimeMillis()}"
                val connection = (URL("https://tmpfiles.org/api/v1/upload").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    useCaches = false
                    connectTimeout = 40000
                    readTimeout = 90000
                    // Chunked streaming handles audio/video files smoothly without memory limits
                    setChunkedStreamingMode(65536)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }

                val output = DataOutputStream(connection.outputStream)

                // Correct field name must be "file"
                output.writeBytes("--$boundary\r\n")
                output.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"$resolvedName\"\r\n")
                output.writeBytes("Content-Type: $mimeType\r\n\r\n")

                val bufferedInput = BufferedInputStream(inputStream)
                val buffer = ByteArray(65536)
                var bytesRead: Int
                var totalBytesSent: Long = 0

                while (bufferedInput.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    if (fileSize > 0) {
                        val progress = ((totalBytesSent.toDouble() / fileSize) * 100).toInt().coerceIn(1, 99)
                        withContext(Dispatchers.Main) { onProgress(progress) }
                    }
                }

                output.writeBytes("\r\n")
                output.writeBytes("--$boundary--\r\n")
                output.flush()
                output.close()
                bufferedInput.close()
                inputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    connection.disconnect()

                    // Extract "url": "https://tmpfiles.org/..."
                    val regex = Regex(""""url"\s*:\s*"([^"]+)"""")
                    val rawUrl = regex.find(body)?.groupValues?.get(1)

                    if (!rawUrl.isNullOrBlank()) {
                        // Convert to direct streaming/download URL (e.g. tmpfiles.org/123/audio.mp3 -> tmpfiles.org/dl/123/audio.mp3)
                        val directStreamUrl = rawUrl.replace("tmpfiles.org/", "tmpfiles.org/dl/")
                        withContext(Dispatchers.Main) {
                            onProgress(100)
                            onSuccess(directStreamUrl, resolvedName)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onFailure(Exception("Server returned unexpected response."))
                        }
                    }
                } else {
                    val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                    connection.disconnect()
                    withContext(Dispatchers.Main) {
                        onFailure(Exception("Server error ($responseCode): $err"))
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onFailure(e)
                }
            }
        }
    }

    fun broadcastToEnrolledStudents(
        courseId: String,
        type: String,
        title: String,
        message: String
    ) {
        db.collection("course_enrollments")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { snap ->
                val batch = db.batch()
                for (doc in snap.documents) {
                    val sId = doc.getString("studentId") ?: continue
                    val notifRef = db.collection("users").document(sId).collection("notifications").document()
                    batch.set(notifRef, mapOf(
                        "type" to type,
                        "title" to title,
                        "message" to message,
                        "read" to false,
                        "timestamp" to FieldValue.serverTimestamp()
                    ))
                }
                batch.commit()
            }
    }

    fun listenToCourses(onUpdate: (List<Course>) -> Unit): ListenerRegistration {
        return db.collection("courses").addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                Course(
                    id = doc.id,
                    code = doc.getString("code") ?: "",
                    title = doc.getString("title") ?: "",
                    department = doc.getString("department") ?: "",
                    description = doc.getString("description") ?: ""
                )
            } ?: emptyList()
            onUpdate(list)
        }
    }

    fun upsertCourse(course: Course, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val data = hashMapOf(
            "code" to course.code.trim(),
            "title" to course.title.trim(),
            "department" to course.department.trim(),
            "description" to course.description.trim(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (course.id.isBlank()) {
            data["createdAt"] = FieldValue.serverTimestamp()
            db.collection("courses").add(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        } else {
            db.collection("courses").document(course.id).set(data)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onFailure(it) }
        }
    }

    fun deleteCourse(courseId: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("courses").document(courseId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun listenToUsersByRole(role: String, onUpdate: (List<UserAccount>) -> Unit): ListenerRegistration {
        return db.collection("users").whereEqualTo("role", role).addSnapshotListener { snapshot, _ ->
            val list = snapshot?.documents?.mapNotNull { doc ->
                UserAccount(
                    uid = doc.id,
                    fullName = doc.getString("fullName") ?: "",
                    email = doc.getString("email") ?: "",
                    role = doc.getString("role") ?: role,
                    staffId = doc.getString("staffId") ?: "",
                    studentRegNo = doc.getString("studentRegNo") ?: "",
                    assignedCourseId = doc.getString("assignedCourseId") ?: "",
                    assignedCourseTitle = doc.getString("assignedCourseTitle") ?: "",
                    isActive = doc.getBoolean("isActive") ?: true
                )
            } ?: emptyList()
            onUpdate(list)
        }
    }

    fun registerUserSafe(
        context: Context,
        name: String,
        email: String,
        role: String,
        identifier: String,
        course: Course?,
        onSuccess: (generatedPassword: String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val tempPassword = "Lms@" + UUID.randomUUID().toString().take(6) + "1"
        val appName = "Secondary_${System.currentTimeMillis()}"
        val defaultApp = FirebaseApp.getInstance()
        val secondaryApp = FirebaseApp.initializeApp(context, defaultApp.options, appName)
        val secondaryAuth = FirebaseAuth.getInstance(secondaryApp)

        secondaryAuth.createUserWithEmailAndPassword(email, tempPassword)
            .addOnSuccessListener { authResult ->
                val uid = authResult.user?.uid ?: ""
                val userData = hashMapOf<String, Any?>(
                    "uid" to uid,
                    "fullName" to name,
                    "email" to email,
                    "role" to role,
                    "isActive" to true,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                if (role == "tutor") {
                    userData["staffId"] = identifier
                    userData["assignedCourseId"] = course?.id ?: ""
                    userData["assignedCourseTitle"] = "${course?.code ?: ""} - ${course?.title ?: ""}"
                } else {
                    userData["studentRegNo"] = identifier
                }

                val batch = db.batch()
                val userRef = db.collection("users").document(uid)
                batch.set(userRef, userData)

                if (role == "student" && course != null) {
                    val enrollRef = db.collection("course_enrollments").document("${uid}_${course.id}")
                    batch.set(enrollRef, hashMapOf(
                        "studentId" to uid,
                        "studentName" to name,
                        "studentEmail" to email,
                        "courseId" to course.id,
                        "courseTitle" to course.title,
                        "courseCode" to course.code,
                        "status" to "approved",
                        "verifiedBy" to "admin",
                        "verifiedAt" to FieldValue.serverTimestamp()
                    ))
                }

                batch.commit().addOnSuccessListener {
                    auth.sendPasswordResetEmail(email).addOnCompleteListener {
                        secondaryApp.delete()
                        onSuccess(tempPassword)
                    }
                }.addOnFailureListener { e ->
                    secondaryApp.delete()
                    onFailure(e)
                }
            }
            .addOnFailureListener { e ->
                secondaryApp.delete()
                onFailure(e)
            }
    }

    fun setUserActiveStatus(uid: String, isActive: Boolean, onSuccess: () -> Unit) {
        db.collection("users").document(uid).update("isActive", isActive)
            .addOnSuccessListener { onSuccess() }
    }
}