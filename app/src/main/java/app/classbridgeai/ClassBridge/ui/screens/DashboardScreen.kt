package app.classbridgeai.ClassBridge.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.classbridgeai.ClassBridge.data.models.*
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: ClassBridgeViewModel,
    onNavigateToAssignments: () -> Unit,
    onNavigateToGrades: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAI: () -> Unit
) {
    val courses by viewModel.courses.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    val grades by viewModel.grades.collectAsState()
    val chatRooms by viewModel.chatRooms.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadCourses()
        viewModel.loadAllAssignments()
        viewModel.loadGrades()
        viewModel.getUserChatRooms()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Dashboard", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "Welcome back!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
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
            // Quick navigation actions card container layout
            item {
                QuickActionsCard(
                    onNavigateToAssignments = onNavigateToAssignments,
                    onNavigateToGrades = onNavigateToGrades,
                    onNavigateToChat = onNavigateToChat,
                    onNavigateToAI = onNavigateToAI
                )
            }
            
            // Statistics overview metrics card aesthetic design
            item {
                StatsOverviewCard(
                    totalCourses = courses.size,
                    totalAssignments = assignments.size,
                    completedAssignments = assignments.count { it.state == "TURNED_IN" },
                    averageGrade = grades.mapNotNull { it.overallGrade }.average().takeIf { !it.isNaN() }
                )
            }
            
            // Upcoming assignments summary list section
            item {
                val upcomingAssignments = assignments
                    .filter { it.dueDate != null }
                    .sortedBy { parseDate(it.dueDate!!) }
                    .take(5)
                
                if (upcomingAssignments.isNotEmpty()) {
                    SectionCard(
                        title = "Upcoming Assignments",
                        icon = Icons.Default.Assignment,
                        onViewAll = onNavigateToAssignments
                    ) {
                        upcomingAssignments.forEach { assignment ->
                            AssignmentItem(assignment = assignment)
                            if (assignment != upcomingAssignments.last()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }
            
            // Grade summary list card surface section
            item {
                if (grades.isNotEmpty()) {
                    SectionCard(
                        title = "Grade Summary",
                        icon = Icons.Default.Grade,
                        onViewAll = onNavigateToGrades
                    ) {
                        grades.take(4).forEach { gradeData ->
                            GradeItem(gradeData = gradeData)
                        }
                    }
                }
            }
            
            // Active chat rooms visual list section layout
            item {
                if (chatRooms.isNotEmpty()) {
                    SectionCard(
                        title = "Active Chat Rooms",
                        icon = Icons.Default.Chat,
                        onViewAll = onNavigateToChat
                    ) {
                        chatRooms.take(3).forEach { chatRoom ->
                            ChatRoomItem(chatRoom = chatRoom)
                        }
                    }
                }
            }
        }
    }
}

// Quick action navigation card component visual layout
@Composable
fun QuickActionsCard(
    onNavigateToAssignments: () -> Unit,
    onNavigateToGrades: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToAI: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickActionButton(
                    icon = Icons.Default.Assignment,
                    label = "Assignments",
                    onClick = onNavigateToAssignments
                )
                QuickActionButton(
                    icon = Icons.Default.Grade,
                    label = "Grades",
                    onClick = onNavigateToGrades
                )
                QuickActionButton(
                    icon = Icons.Default.Chat,
                    label = "Chat",
                    onClick = onNavigateToChat
                )
                QuickActionButton(
                    icon = Icons.Default.Psychology,
                    label = "AI Help",
                    onClick = onNavigateToAI
                )
            }
        }
    }
}

// Circle shape icon button layout for navigation actions
@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.padding(14.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// Summary overview stats container layout
@Composable
fun StatsOverviewCard(
    totalCourses: Int,
    totalAssignments: Int,
    completedAssignments: Int,
    averageGrade: Double?
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = totalCourses.toString(),
                    label = "Courses",
                    icon = Icons.Default.School
                )
                StatItem(
                    value = "$completedAssignments/$totalAssignments",
                    label = "Completed",
                    icon = Icons.Default.CheckCircle
                )
                StatItem(
                    value = averageGrade?.let { "%.1f%%".format(it) } ?: "N/A",
                    label = "Avg Grade",
                    icon = Icons.Default.TrendingUp
                )
            }
        }
    }
}

// Metric stat item vertical alignment layout
@Composable
fun StatItem(value: String, label: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Section card surface container layout
@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    onViewAll: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                TextButton(onClick = onViewAll) {
                    Text("View All")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

// Assignment item row visual layout
@Composable
fun AssignmentItem(assignment: Assignment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = assignment.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = assignment.courseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            assignment.dueDate?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue(it)) MaterialTheme.colorScheme.error
                           else MaterialTheme.colorScheme.primary
                )
            }
            assignment.maxPoints?.let {
                Text(
                    text = "$it pts",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Course grade summary row layout
@Composable
fun GradeItem(gradeData: GradeData) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gradeData.courseName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${gradeData.assignments.size} assignments",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            gradeData.letterGrade?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = getGradeColor(it)
                )
            }
            gradeData.overallGrade?.let {
                Text(
                    text = "%.1f%%".format(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Chat room list item visual surface layout
@Composable
fun ChatRoomItem(chatRoom: ChatRoom) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatRoom.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${chatRoom.members.size} members",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseDate(dateString: String): Date {
    return try {
        SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).parse(dateString) ?: Date()
    } catch (e: Exception) {
        Date()
    }
}

private fun isOverdue(dateString: String): Boolean {
    val dueDate = parseDate(dateString)
    return dueDate.before(Date())
}

// Visual color palette styling for grade letter indicators
@Composable
private fun getGradeColor(letterGrade: String): Color {
    return when (letterGrade.first()) {
        'A' -> Color(0xFF4CAF50)
        'B' -> Color(0xFF2196F3)
        'C' -> Color(0xFFFF9800)
        'D' -> Color(0xFFFF5722)
        else -> Color(0xFFf44336)
    }
}
