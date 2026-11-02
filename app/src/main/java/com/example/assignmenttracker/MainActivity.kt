package com.example.assignmenttracker

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.example.assignmenttracker.data.FirebaseService
import com.example.assignmenttracker.ui.admin.AdminHodScreen
import com.example.assignmenttracker.ui.auth.LoginScreen
import com.example.assignmenttracker.ui.student.StudentDashboardScreen
import com.example.assignmenttracker.ui.theme.AssignmentTrackerTheme
import com.example.assignmenttracker.ui.tutor.TutorDashboardScreen
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val incomingRole = intent.getStringExtra("USER_ROLE")?.lowercase() ?: "login"

        setContent {
            AssignmentTrackerTheme {
                val context = LocalContext.current
                var currentScreen by remember {
                    mutableStateOf(
                        when (incomingRole) {
                            "admin", "hod" -> "admin"
                            "tutor" -> "tutor"
                            "student" -> "student"
                            else -> "login"
                        }
                    )
                }

                val timeoutMillis = 5 * 60 * 1000L
                var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

                fun returnToLogin(reason: String? = null) {
                    FirebaseService.auth.signOut()
                    reason?.let {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                    val intent = Intent(this@MainActivity, SplashActivity::class.java).apply {
                        putExtra("START_DESTINATION", "LOGIN")
                    }
                    startActivity(intent)
                    finish()
                }

                LaunchedEffect(lastInteractionTime, currentScreen) {
                    if (currentScreen != "login") {
                        while (true) {
                            delay(10_000L)
                            val elapsed = System.currentTimeMillis() - lastInteractionTime
                            if (elapsed >= timeoutMillis) {
                                returnToLogin("Session expired: logged out after 5 minutes of inactivity.")
                                break
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.changes.any { it.pressed }) {
                                            lastInteractionTime = System.currentTimeMillis()
                                        }
                                    }
                                }
                            }
                    ) {
                        when (currentScreen) {
                            "admin" -> {
                                AdminHodScreen(onLogout = { returnToLogin() })
                            }
                            "tutor" -> {
                                TutorDashboardScreen(onLogout = { returnToLogin() })
                            }
                            "student" -> {
                                StudentDashboardScreen(onLogout = { returnToLogin() })
                            }
                            else -> {
                                LoginScreen(
                                    onNavigateToRegister = {
                                        val intent = Intent(this@MainActivity, SplashActivity::class.java).apply {
                                            putExtra("START_DESTINATION", "REGISTER")
                                        }
                                        startActivity(intent)
                                        finish()
                                    },
                                    onBack = { returnToLogin() },
                                    onLoginSuccess = { role ->
                                        lastInteractionTime = System.currentTimeMillis()
                                        currentScreen = when (role.lowercase()) {
                                            "admin", "hod" -> "admin"
                                            "tutor" -> "tutor"
                                            else -> "student"
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}