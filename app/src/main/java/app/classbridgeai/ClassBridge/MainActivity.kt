package app.classbridgeai.ClassBridge

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.classbridgeai.ClassBridge.ui.screens.*
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.classbridgeai.ClassBridge.data.services.NotificationScheduler
import app.classbridgeai.ClassBridge.ui.theme.ClassBridgeTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

@OptIn(ExperimentalMaterial3Api::class)
val WEB_CLIENT_ID = "700180410067-93e6ftc1pnhdig04r3mjcppnci159d15.apps.googleusercontent.com"

class MainActivity : ComponentActivity() {
    private val viewModel: ClassBridgeViewModel by viewModels {
        ClassBridgeViewModelFactory(applicationContext)
    }
    
    private lateinit var googleSignInClient: GoogleSignInClient
    private var onSignInResult: ((GoogleSignInAccount?) -> Unit)? = null
    
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult()
            Log.d(TAG, "Sign in successful: ${account.email}")
            onSignInResult?.invoke(account)
        } catch (e: Exception) {
            Log.e(TAG, "Sign in failed", e)
            onSignInResult?.invoke(null)
        }
    }

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Log.d(TAG, "Classroom consent granted, reloading data")
            viewModel.loadCourses()
            viewModel.loadAllAssignments()
            viewModel.loadGrades()
        } else {
            Log.w(TAG, "Classroom consent denied or cancelled")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate started")
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(WEB_CLIENT_ID)
                .requestScopes(
                    Scope("https://www.googleapis.com/auth/classroom.courses.readonly"),
                    Scope("https://www.googleapis.com/auth/classroom.coursework.me.readonly"),
                    Scope("https://www.googleapis.com/auth/classroom.student-submissions.me.readonly"),
                    Scope("https://www.googleapis.com/auth/classroom.rosters.readonly"),
                    Scope("https://www.googleapis.com/auth/classroom.profile.emails"),
                    Scope("https://www.googleapis.com/auth/drive.readonly")
                )
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d(TAG, "GoogleSignInClient configured")

            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.consentRequiredEvent.collect { intent ->
                        consentLauncher.launch(intent)
                    }
                }
            }
            
            enableEdgeToEdge()
            Log.d(TAG, "Edge-to-edge enabled")
            setContent {
                Log.d(TAG, "setContent called")
                val context = LocalContext.current
                var darkTheme by rememberSaveable {
                    mutableStateOf(
                        context.getSharedPreferences("app_prefs", 0).getBoolean("dark_theme", false)
                    )
                }
                ClassBridgeTheme(darkTheme = darkTheme) {
                    ClassBridgeMainApp(
                        viewModel = viewModel,
                        startSignIn = ::startGoogleSignIn,
                        signOut = ::signOut,
                        darkTheme = darkTheme,
                        onDarkThemeToggle = { enabled ->
                            darkTheme = enabled
                            context.getSharedPreferences("app_prefs", 0)
                                .edit().putBoolean("dark_theme", enabled).apply()
                        }
                    )
                }
            }
            Log.d(TAG, "onCreate completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            throw e
        }
    }
    
    private fun startGoogleSignIn(callback: (GoogleSignInAccount?) -> Unit) {
        onSignInResult = callback
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }
    
    fun signOut(callback: () -> Unit) {
        googleSignInClient.signOut().addOnCompleteListener(this) {
            Log.d(TAG, "User signed out successfully")
            callback()
        }
    }
    
    companion object {
        private const val TAG = "MainActivity"
    }
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    object Assignments : Screen("assignments", "Assignments", Icons.Default.Assignment)
    object Grades : Screen("grades", "Grades", Icons.Default.Grade)
    object Chat : Screen("chat", "Chat", Icons.Default.Chat)
    object AIStudy : Screen("ai_study", "AI Study", Icons.Default.Psychology)
    object Profile : Screen("profile", "Profile", Icons.Default.AccountBox)
}

// Main application surface theme wrapper layout
@Composable
fun ClassBridgeMainApp(
    viewModel: ClassBridgeViewModel,
    startSignIn: ((GoogleSignInAccount?) -> Unit) -> Unit,
    signOut: (() -> Unit) -> Unit,
    darkTheme: Boolean = false,
    onDarkThemeToggle: (Boolean) -> Unit = {}
) {
    Log.d("ClassBridgeMainApp", "Composing main app")
    val alreadySignedIn = remember { FirebaseAuth.getInstance().currentUser != null }
    var isSignedIn by rememberSaveable { mutableStateOf(alreadySignedIn) }
    var showSignUp by remember { mutableStateOf(!alreadySignedIn) }
    
    LaunchedEffect(isSignedIn) {
        Log.d("ClassBridgeMainApp", "isSignedIn changed to: $isSignedIn")
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedVisibility(
            visible = showSignUp && !isSignedIn,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            SignUpScreen(
                startSignIn = startSignIn,
                onSignInSuccess = {
                    showSignUp = false
                    isSignedIn = true
                }
            )
        }
        
        AnimatedVisibility(
            visible = !showSignUp && isSignedIn,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically { it },
            exit = fadeOut()
        ) {
            ClassBridgeApp(
                viewModel = viewModel,
                onSignOut = {
                    signOut {
                        isSignedIn = false
                        showSignUp = true
                    }
                },
                darkTheme = darkTheme,
                onDarkThemeToggle = onDarkThemeToggle
            )
        }
    }
}

// Sign-up hero banner visual layout and gradient theme background
@Composable
fun SignUpScreen(startSignIn: ((GoogleSignInAccount?) -> Unit) -> Unit, onSignInSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    ) { }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1976D2),
                        Color(0xFF42A5F5),
                        Color(0xFF64B5F6)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.School,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale),
                tint = Color.White
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "ClassBridge",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Text(
                text = "Your AI-powered learning companion",
                fontSize = 16.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(scale),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Get Started",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1976D2)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Sign in with your Google account to access your assignments, grades, and more",
                        textAlign = TextAlign.Center,
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            Log.d("SignUpScreen", "Sign in button clicked")
                            isLoading = true
                            errorMessage = null
                            startSignIn { account ->
                                scope.launch {
                                    if (account != null) {
                                        Log.d("SignUpScreen", "Google sign-in successful: ${account.email}, hasIdToken=${account.idToken != null}")
                                        try {
                                            val idToken = account.idToken
                                            if (idToken != null) {
                                                val credential = GoogleAuthProvider.getCredential(idToken, null)
                                                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                                Log.d("SignUpScreen", "Firebase Auth sign-in successful, uid=$uid")
                                            } else {
                                                Log.w("SignUpScreen", "No idToken — Firebase Auth uid will be null")
                                            }
                                            isLoading = false
                                            onSignInSuccess()
                                        } catch (e: Exception) {
                                            Log.e("SignUpScreen", "Firebase Auth sign-in failed", e)
                                            errorMessage = "Sign in failed: ${e.message}"
                                            isLoading = false
                                        }
                                    } else {
                                        Log.e("SignUpScreen", "Google sign-in failed / cancelled")
                                        errorMessage = "Sign in failed. Please try again."
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1976D2)
                        ),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Login,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Sign in with Google",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            Log.d("SignUpScreen", "Test account Google sign-in button clicked")
                            isLoading = true
                            errorMessage = null
                            startSignIn { account ->
                                scope.launch {
                                    if (account != null) {
                                        Log.d("SignUpScreen", "Test account Google sign-in successful: ${account.email}, hasIdToken=${account.idToken != null}")
                                        try {
                                            val idToken = account.idToken
                                            if (idToken != null) {
                                                val credential = GoogleAuthProvider.getCredential(idToken, null)
                                                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                                Log.d("SignUpScreen", "Firebase Auth sign-in successful, uid=$uid")
                                            } else {
                                                Log.w("SignUpScreen", "No idToken — Firebase Auth uid will be null")
                                            }
                                            isLoading = false
                                            onSignInSuccess()
                                        } catch (e: Exception) {
                                            Log.e("SignUpScreen", "Test account Firebase sign-in failed", e)
                                            errorMessage = "Test sign in failed: ${e.message}"
                                            isLoading = false
                                        }
                                    } else {
                                        Log.e("SignUpScreen", "Test account Google sign-in failed / cancelled")
                                        errorMessage = "Test sign in failed. Please try again."
                                        isLoading = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        enabled = !isLoading
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color(0xFF1976D2)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Sign in with Test Account",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1976D2)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFE3F2FD),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Test Account Credentials",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0D47A1)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Email: classbridge.student@gmail.com",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0D47A1),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Password: ClassBridge_2027",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF0D47A1),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    errorMessage?.let { error ->
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// Bottom bar navigation scaffold container structure
@Composable
fun ClassBridgeApp(
    viewModel: ClassBridgeViewModel,
    onSignOut: () -> Unit,
    darkTheme: Boolean = false,
    onDarkThemeToggle: (Boolean) -> Unit = {}
) {
    Log.d("ClassBridgeApp", "Initializing app navigation")
    val screens = remember {
        listOf(
            Screen.Dashboard,
            Screen.Assignments,
            Screen.Grades,
            Screen.Chat,
            Screen.AIStudy,
            Screen.Profile
        )
    }
    val pagerState = rememberPagerState(initialPage = 0) { screens.size }
    val scope = rememberCoroutineScope()
    val currentScreen = screens[pagerState.currentPage]
    val dashboardIndex = remember(screens) { screens.indexOf(Screen.Dashboard) }

    fun navigateToScreen(screen: Screen, coroutineScope: CoroutineScope) {
        val targetIndex = screens.indexOf(screen)
        if (targetIndex != -1 && targetIndex != pagerState.currentPage) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(targetIndex)
            }
        }
    }
    
    LaunchedEffect(currentScreen) {
        Log.d("ClassBridgeApp", "Current screen: ${currentScreen.title}")
    }
    
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            screens.forEach { screen ->
                item(
                    selected = currentScreen == screen,
                    onClick = { navigateToScreen(screen, scope) },
                    icon = {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title
                        )
                    },
                    label = { 
                        Text(
                            text = screen.title,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        ) 
                    },
                    alwaysShowLabel = false
                )
            }
        }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (screens[page]) {
                Screen.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToAssignments = { navigateToScreen(Screen.Assignments, scope) },
                    onNavigateToGrades = { navigateToScreen(Screen.Grades, scope) },
                    onNavigateToChat = { navigateToScreen(Screen.Chat, scope) },
                    onNavigateToAI = { navigateToScreen(Screen.AIStudy, scope) }
                )

                Screen.Assignments -> AssignmentsScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (dashboardIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(dashboardIndex) }
                        }
                    }
                )

                Screen.Grades -> GradesScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (dashboardIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(dashboardIndex) }
                        }
                    }
                )

                Screen.Chat -> ChatScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (dashboardIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(dashboardIndex) }
                        }
                    }
                )

                Screen.AIStudy -> AIStudyScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (dashboardIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(dashboardIndex) }
                        }
                    }
                )

                Screen.Profile -> ProfileScreen(
                    viewModel = viewModel,
                    onBack = {
                        if (dashboardIndex != -1) {
                            scope.launch { pagerState.animateScrollToPage(dashboardIndex) }
                        }
                    },
                    onSignOut = onSignOut,
                    darkTheme = darkTheme,
                    onDarkThemeToggle = onDarkThemeToggle
                )
            }
        }
    }
}

// Profile screen settings list card layout and user avatar header visual design
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ClassBridgeViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
    darkTheme: Boolean = false,
    onDarkThemeToggle: (Boolean) -> Unit = {}
) {
    val grades by viewModel.grades.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val context = LocalContext.current
    val googleAccount = remember { GoogleSignIn.getLastSignedInAccount(context) }
    val profileName = googleAccount?.displayName?.takeIf { it.isNotBlank() } ?: "Student"
    val profileEmail = googleAccount?.email?.takeIf { it.isNotBlank() } ?: "No email available"
    
    var notificationsEnabled by remember { mutableStateOf(NotificationScheduler.isEnabled(context)) }
    var notificationHour by remember { mutableIntStateOf(NotificationScheduler.getHour(context)) }
    var notificationMinute by remember { mutableIntStateOf(NotificationScheduler.getMinute(context)) }
    var showTimePicker by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            notificationsEnabled = true
            NotificationScheduler.savePrefs(context, true, notificationHour, notificationMinute)
            NotificationScheduler.schedule(context, notificationHour, notificationMinute)
        }
    }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign Out") },
            text = { Text("Are you sure you want to sign out?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onSignOut()
                    }
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About ClassBridge") },
            text = { 
                Column {
                    Text("Version 1.0.0")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your AI-powered learning companion", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("© 2026 ClassBridge", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy & Data") },
            text = { 
                Column {
                    Text("Your data is stored securely in Firebase and is never shared with third parties.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("We collect only the information necessary to provide our services.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("OK")
                }
            }
        )
    }
    
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = notificationHour,
            initialMinute = notificationMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Notification Time") },
            text = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    notificationHour = timePickerState.hour
                    notificationMinute = timePickerState.minute
                    NotificationScheduler.savePrefs(context, true, notificationHour, notificationMinute)
                    NotificationScheduler.schedule(context, notificationHour, notificationMinute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(80.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.padding(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            profileName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            profileEmail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            item {
                Text(
                    "Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard(
                        value = courses.size.toString(),
                        label = "Courses",
                        icon = Icons.Default.School
                    )
                    StatCard(
                        value = grades.flatMap { it.assignments }.size.toString(),
                        label = "Assignments",
                        icon = Icons.Default.Assignment
                    )
                    StatCard(
                        value = grades.mapNotNull { it.overallGrade }.average()
                            .takeIf { !it.isNaN() }?.let { "%.0f%%".format(it) } ?: "N/A",
                        label = "Avg Grade",
                        icon = Icons.Default.TrendingUp
                    )
                }
            }
            
            item {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        SettingsItemWithSwitch(
                            icon = Icons.Default.Notifications,
                            title = "Notifications",
                            subtitle = "Daily homework reminders",
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.POST_NOTIFICATIONS
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notificationsEnabled = true
                                        NotificationScheduler.savePrefs(context, true, notificationHour, notificationMinute)
                                        NotificationScheduler.schedule(context, notificationHour, notificationMinute)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    notificationsEnabled = false
                                    NotificationScheduler.savePrefs(context, false, notificationHour, notificationMinute)
                                    NotificationScheduler.cancel(context)
                                }
                            }
                        )
                        if (notificationsEnabled) {
                            HorizontalDivider()
                            SettingsItem(
                                icon = Icons.Default.Schedule,
                                title = "Notification Time",
                                subtitle = formatNotificationTime(notificationHour, notificationMinute),
                                onClick = { showTimePicker = true }
                            )
                        }
                        HorizontalDivider()
                        SettingsItemWithSwitch(
                            icon = Icons.Default.DarkMode,
                            title = "Dark Theme",
                            subtitle = "Change app appearance",
                            checked = darkTheme,
                            onCheckedChange = onDarkThemeToggle
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Security,
                            title = "Privacy",
                            subtitle = "Control your data and privacy",
                            onClick = { showPrivacyDialog = true }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "About",
                            subtitle = "Version 1.0.0",
                            onClick = { showAboutDialog = true }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Logout,
                            title = "Sign Out",
                            subtitle = "Sign out of your account",
                            onClick = { showSignOutDialog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, icon: ImageVector) {
    Card(
        modifier = Modifier.width(100.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatNotificationTime(hour: Int, minute: Int): String {
    val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
    val amPm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(h, minute, amPm)
}

@Composable
fun SettingsItemWithSwitch(
    icon: ImageVector, 
    title: String, 
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
