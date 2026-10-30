package app.classbridgeai.ClassBridge.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.classbridgeai.ClassBridge.data.models.GradeData
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    viewModel: ClassBridgeViewModel,
    onBack: () -> Unit
) {
    val grades by viewModel.grades.collectAsState()
    var selectedCourseId by remember { mutableStateOf<String?>(null) }
    var progressionCourseId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadGrades()
    }

    if (progressionCourseId != null) {
        GradeProgressionScreen(
            grades = grades,
            initialCourseId = progressionCourseId!!,
            onBack = { progressionCourseId = null }
        )
        return
    }
    
    val averageGrade = grades.mapNotNull { it.overallGrade }.average().takeIf { !it.isNaN() }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grades") },
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
            // Overall GPA display hero card surface layout
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Overall Average",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = averageGrade?.let { "%.1f%%".format(it) } ?: "N/A",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        averageGrade?.let {
                            Text(
                                text = calculateLetterGrade(it),
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            // Course grade list items layout
            items(grades) { gradeData ->
                CourseGradeCard(
                    gradeData = gradeData,
                    isExpanded = selectedCourseId == gradeData.courseId,
                    onExpandClick = {
                        selectedCourseId = if (selectedCourseId == gradeData.courseId) null
                        else gradeData.courseId
                    },
                    onViewProgression = { progressionCourseId = gradeData.courseId }
                )
            }
            
            if (grades.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Grade,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "No grades available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// Collapsible course grade card component design and expandable layout
@Composable
fun CourseGradeCard(
    gradeData: GradeData,
    isExpanded: Boolean,
    onExpandClick: () -> Unit,
    onViewProgression: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onExpandClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = gradeData.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = getGradeColor(it)
                        )
                    }
                    gradeData.overallGrade?.let {
                        Text(
                            text = "%.1f%%".format(it),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                IconButton(onClick = onExpandClick) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }
            
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                FilledTonalButton(
                    onClick = onViewProgression,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Grade Progression")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Assignment breakdown sub-list aesthetic layout
                Text(
                    "Assignment Breakdown",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                gradeData.assignments.forEach { assignment ->
                    AssignmentGradeItem(assignment = assignment)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

// Assignment grade list item row surface container styling
@Composable
fun AssignmentGradeItem(assignment: app.classbridgeai.ClassBridge.data.models.GradeAssignment) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = assignment.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                assignment.dueDate?.let {
                    Text(
                        text = "Due: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (assignment.late) {
                    Text(
                        text = "Late Submission",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                val gradeText = if (assignment.earnedPoints != null && assignment.maxPoints != null) {
                    "${assignment.earnedPoints}/${assignment.maxPoints}"
                } else {
                    assignment.state
                }
                
                Text(
                    text = gradeText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (assignment.state) {
                        "TURNED_IN" -> MaterialTheme.colorScheme.primary
                        "NOT_SUBMITTED" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                assignment.grade?.let { grade ->
                    Text(
                        text = "%.1f%%".format(grade),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// Aesthetic color palette mapping for grade letter badges
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

private fun calculateLetterGrade(percentage: Double): String {
    return when {
        percentage >= 93 -> "A"
        percentage >= 90 -> "A-"
        percentage >= 87 -> "B+"
        percentage >= 83 -> "B"
        percentage >= 80 -> "B-"
        percentage >= 77 -> "C+"
        percentage >= 73 -> "C"
        percentage >= 70 -> "C-"
        percentage >= 67 -> "D+"
        percentage >= 63 -> "D"
        percentage >= 60 -> "D-"
        else -> "F"
    }
}
