package com.example.assignmenttracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.example.assignmenttracker.ui.auth.LoginScreen
import com.example.assignmenttracker.ui.theme.AssignmentTrackerTheme
import com.example.assignmenttracker.ui.theme.ThemeToggleButton

enum class ScreenState {
    WELCOME, LOGIN, REGISTER
}

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val startDestination = intent.getStringExtra("START_DESTINATION")
        val initialScreen = if (startDestination == "LOGIN") ScreenState.LOGIN else ScreenState.WELCOME

        setContent {
            AssignmentTrackerTheme {
                var currentScreen by remember { mutableStateOf(initialScreen) }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "AuthNavigation"
                    ) { screen ->
                        when (screen) {
                            ScreenState.WELCOME -> SplashWelcomeScreen(
                                onGetStarted = { currentScreen = ScreenState.LOGIN },
                                onExitApp = { finishAffinity() }
                            )
                            ScreenState.LOGIN -> LoginScreen(
                                onNavigateToRegister = { currentScreen = ScreenState.REGISTER },
                                onBack = { currentScreen = ScreenState.WELCOME },
                                onLoginSuccess = { role ->
                                    val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                                        putExtra("USER_ROLE", role)
                                    }
                                    startActivity(intent)
                                    finish()
                                }
                            )
                            ScreenState.REGISTER -> RegisterScreen(
                                onNavigateToLogin = { currentScreen = ScreenState.LOGIN },
                                onBack = { currentScreen = ScreenState.LOGIN },
                                onRegisterSuccess = { role ->
                                    val intent = Intent(this@SplashActivity, MainActivity::class.java).apply {
                                        putExtra("USER_ROLE", role)
                                    }
                                    startActivity(intent)
                                    finish()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SplashWelcomeScreen(
    onGetStarted: () -> Unit,
    onExitApp: () -> Unit
) {
    val welcomeBannerUrl = "https://images.unsplash.com/photo-1501504905252-473c47e087f8?w=800&auto=format&fit=crop&q=80"
    val appIconUrl = "https://images.unsplash.com/photo-1546410531-bb4caa6b424d?w=300&auto=format&fit=crop&q=80"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ThemeToggleButton()
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                shape = CircleShape,
                elevation = CardDefaults.cardElevation(6.dp),
                modifier = Modifier.size(90.dp)
            ) {
                SubcomposeAsyncImage(
                    model = appIconUrl,
                    contentDescription = "App Icon",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Assignment Tracker LMS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Higher Education Learning & Assessment System",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                SubcomposeAsyncImage(
                    model = welcomeBannerUrl,
                    contentDescription = "Education Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onGetStarted,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text("Get Started / Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onExitApp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Exit Application")
            }
        }
    }
}

@Composable
fun BackButtonGraphic(tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 2.5.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(x = size.width * 0.95f, y = size.height * 0.5f),
            end = Offset(x = size.width * 0.1f, y = size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(x = size.width * 0.1f, y = size.height * 0.5f),
            end = Offset(x = size.width * 0.5f, y = size.height * 0.15f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(x = size.width * 0.1f, y = size.height * 0.5f),
            end = Offset(x = size.width * 0.5f, y = size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}