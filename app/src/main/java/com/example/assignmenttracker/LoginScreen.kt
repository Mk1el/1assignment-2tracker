package com.example.assignmenttracker.ui.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.assignmenttracker.BackButtonGraphic
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.ui.components.LmsPrimaryButton
import com.google.firebase.firestore.FieldValue

@Composable
fun LoginScreen(
    onNavigateToRegister: () -> Unit = {},
    onBack: () -> Unit = {},
    onLoginSuccess: (role: String) -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var showForgotDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }
    var isSendingReset by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val loginBannerUrl = "https://images.unsplash.com/photo-1523240795612-9a054b0db644?w=800&auto=format&fit=crop&q=80"

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
                    BackButtonGraphic(tint = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                SubcomposeAsyncImage(
                    model = loginBannerUrl,
                    contentDescription = "LMS Banner",
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
                            Text("Assignment Tracker LMS", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Welcome Back 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Sign in with your institutional credentials",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                placeholder = { Text("e.g. name@institution.edu", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        resetEmail = email.trim()
                        showForgotDialog = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            LmsPrimaryButton(
                text = "Sign In",
                isLoading = isLoading,
                onClick = {
                    val trimmedEmail = email.trim()
                    val trimmedPassword = password.trim()

                    if (trimmedEmail.isBlank() || trimmedPassword.isBlank()) {
                        Toast.makeText(context, "❌ Please enter both email and password", Toast.LENGTH_SHORT).show()
                        return@LmsPrimaryButton
                    }

                    isLoading = true

                    FirebaseService.auth.signInWithEmailAndPassword(trimmedEmail, trimmedPassword)
                        .addOnSuccessListener { authResult ->
                            val uid = authResult.user?.uid ?: ""

                            FirebaseService.db.collection("users").document(uid).get()
                                .addOnSuccessListener { doc ->
                                    if (!doc.exists()) {
                                        val initialData = hashMapOf(
                                            "uid" to uid,
                                            "email" to trimmedEmail,
                                            "fullName" to trimmedEmail.substringBefore("@").replaceFirstChar { it.uppercase() },
                                            "role" to "student",
                                            "isActive" to true,
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                        FirebaseService.db.collection("users").document(uid).set(initialData)
                                            .addOnSuccessListener {
                                                isLoading = false
                                                Toast.makeText(context, "✅ Login Successful! Welcome Student", Toast.LENGTH_SHORT).show()
                                                onLoginSuccess("student")
                                            }
                                            .addOnFailureListener { e ->
                                                isLoading = false
                                                Toast.makeText(context, "❌ Profile init failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        return@addOnSuccessListener
                                    }

                                    val isActive = doc.getBoolean("isActive") ?: true
                                    if (!isActive) {
                                        isLoading = false
                                        FirebaseService.auth.signOut()
                                        Toast.makeText(
                                            context,
                                            "🚫 Access Denied: Your account has been deactivated by the Admin/HOD.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@addOnSuccessListener
                                    }

                                    val role = doc.getString("role")?.lowercase() ?: "student"
                                    val userName = doc.getString("fullName") ?: "User"
                                    isLoading = false

                                    val roleGreeting = when (role) {
                                        "admin", "hod" -> "Admin Portal"
                                        "tutor" -> "Tutor Portal"
                                        else -> "Student Portal"
                                    }
                                    Toast.makeText(context, "✅ Welcome back, $userName ($roleGreeting)", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess(role)
                                }
                                .addOnFailureListener { e ->
                                    isLoading = false
                                    Toast.makeText(context, "❌ Database error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            isLoading = false
                            Toast.makeText(context, "❌ Login Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Register as Student",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!isSendingReset) showForgotDialog = false },
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Reset Password 🔒",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter your registered email address. A password reset link will be sent to your inbox.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = resetEmail,
                        onValueChange = { resetEmail = it },
                        label = { Text("Account Email") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = !isSendingReset,
                    shape = RoundedCornerShape(10.dp),
                    onClick = {
                        val target = resetEmail.trim()
                        if (target.isBlank()) {
                            Toast.makeText(context, "❌ Please enter your email", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSendingReset = true
                        FirebaseService.auth.sendPasswordResetEmail(target)
                            .addOnSuccessListener {
                                isSendingReset = false
                                showForgotDialog = false
                                Toast.makeText(context, "✅ Password reset instructions sent to $target", Toast.LENGTH_LONG).show()
                            }
                            .addOnFailureListener { e ->
                                isSendingReset = false
                                Toast.makeText(context, "❌ Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                    }
                ) {
                    if (isSendingReset) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Send Reset Link")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isSendingReset,
                    onClick = { showForgotDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}