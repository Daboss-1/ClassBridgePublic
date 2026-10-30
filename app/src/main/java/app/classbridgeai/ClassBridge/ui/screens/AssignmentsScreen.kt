package app.classbridgeai.ClassBridge.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.classbridgeai.ClassBridge.data.models.Assignment
import app.classbridgeai.ClassBridge.data.models.Teacher
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignmentsScreen(
    viewModel: ClassBridgeViewModel,
    onBack: () -> Unit
) {
    val assignments by viewModel.assignments.collectAsState()
    val courses by viewModel.courses.collectAsState()
    val courseTeachers by viewModel.courseTeachers.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
    var showReportDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadAllAssignments()
        viewModel.loadCourses()
    }

    LaunchedEffect(assignments) {
        val courseIds = assignments.map { it.courseId }.distinct()
        if (courseIds.isNotEmpty()) {
            viewModel.loadCourseTeachers(courseIds)
        }
    }
    
    selectedAssignment?.let { assignment ->
        StudyAssistDialog(
            assignment = assignment,
            viewModel = viewModel,
            onDismiss = { selectedAssignment = null }
        )
    }
    
    val filteredAssignments = when (selectedFilter) {
        "All" -> assignments
        "Pending" -> assignments.filter { it.state != "TURNED_IN" }
        "Completed" -> assignments.filter { it.state == "TURNED_IN" }
        "Overdue" -> assignments.filter { 
            it.dueDate != null && isOverdue(it.dueDate!!) && it.state != "TURNED_IN"
        }
        else -> assignments.filter { it.courseName == selectedFilter }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assignments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Badge(
                            containerColor = if (selectedFilter != "All")
                                MaterialTheme.colorScheme.primary else Color.Transparent
                        ) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showReportDialog = true },
                icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                text = { Text("2-Day Report") }
            )
        }
    ) { padding ->
        if (filteredAssignments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Assignment,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No assignments found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "$selectedFilter (${filteredAssignments.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                items(filteredAssignments) { assignment ->
                    AssignmentCard(
                        assignment = assignment,
                        teachers = courseTeachers[assignment.courseId] ?: emptyList(),
                        onRequestTeachersRefresh = {
                            viewModel.loadCourseTeachers(
                                courseIds = listOf(assignment.courseId),
                                forceRefresh = true
                            )
                        },
                        onStudyAssist = { selectedAssignment = assignment }
                    )
                }
            }
        }
    }
    
    if (showReportDialog) {
        HomeworkReportDialog(
            assignments = assignments,
            onDismiss = { showReportDialog = false }
        )
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            courses = courses,
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            onDismiss = { showFilterSheet = false }
        )
    }
}

// Filter sheet surface layout and chip item visual styling
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    courses: List<app.classbridgeai.ClassBridge.data.models.Course>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Filter Assignments",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            FilterChip(
                selected = selectedFilter == "All",
                onClick = {
                    onFilterSelected("All")
                    onDismiss()
                },
                label = { Text("All Assignments") },
                modifier = Modifier.fillMaxWidth()
            )
            
            FilterChip(
                selected = selectedFilter == "Pending",
                onClick = {
                    onFilterSelected("Pending")
                    onDismiss()
                },
                label = { Text("Pending") },
                modifier = Modifier.fillMaxWidth()
            )
            
            FilterChip(
                selected = selectedFilter == "Completed",
                onClick = {
                    onFilterSelected("Completed")
                    onDismiss()
                },
                label = { Text("Completed") },
                modifier = Modifier.fillMaxWidth()
            )
            
            FilterChip(
                selected = selectedFilter == "Overdue",
                onClick = {
                    onFilterSelected("Overdue")
                    onDismiss()
                },
                label = { Text("Overdue") },
                modifier = Modifier.fillMaxWidth()
            )
            
            if (courses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "By Course",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                
                courses.forEach { course ->
                    FilterChip(
                        selected = selectedFilter == course.name,
                        onClick = {
                            onFilterSelected(course.name)
                            onDismiss()
                        },
                        label = { Text(course.name) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Assignment card component visual design, elevation, and layout
@Composable
fun AssignmentCard(
    assignment: Assignment,
    teachers: List<Teacher> = emptyList(),
    onRequestTeachersRefresh: () -> Unit = {},
    onStudyAssist: () -> Unit = {}
) {
    val context = LocalContext.current
    var showTeacherMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (assignment.state == "TURNED_IN")
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = assignment.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = assignment.courseName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                // Status badge surface color styling
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = when (assignment.state) {
                        "TURNED_IN" -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = when (assignment.state) {
                            "TURNED_IN" -> "Submitted"
                            "CREATED" -> "Assigned"
                            else -> assignment.state
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Email teacher icon action button styling
                Box {
                    IconButton(
                        onClick = {
                            if (teachers.isEmpty()) {
                                onRequestTeachersRefresh()
                                Toast.makeText(
                                    context,
                                    "Refreshing teachers... If none appear, Classroom may not provide teacher emails for this class.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                showTeacherMenu = true
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Email,
                            contentDescription = "Email teacher",
                            modifier = Modifier.size(18.dp),
                            tint = if (teachers.isEmpty()) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = showTeacherMenu,
                        onDismissRequest = { showTeacherMenu = false }
                    ) {
                        teachers.forEach { teacher ->
                            DropdownMenuItem(
                                enabled = teacher.email.isNotBlank(),
                                text = {
                                    Text(
                                        if (teacher.email.isNotBlank()) {
                                            teacher.name
                                        } else {
                                            "${teacher.name} (email unavailable)"
                                        }
                                    )
                                },
                                onClick = {
                                    showTeacherMenu = false
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:")
                                        putExtra(Intent.EXTRA_EMAIL, arrayOf(teacher.email))
                                        putExtra(Intent.EXTRA_SUBJECT, "Help with ${assignment.title}")
                                    }
                                    context.startActivity(Intent.createChooser(intent, null))
                                }
                            )
                        }
                    }
                }
            }
            
            assignment.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Due date icon and label typography formatting
                assignment.dueDate?.let { dueDate ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isOverdue(dueDate) && assignment.state != "TURNED_IN")
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Due $dueDate",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverdue(dueDate) && assignment.state != "TURNED_IN")
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Points badge icon and text styling
                assignment.maxPoints?.let { points ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$points points",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Material attachment list row layout
            if (!assignment.materials.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))
                
                assignment.materials.take(2).forEach { material ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Icon(
                            if (material.driveFile != null) Icons.Default.InsertDriveFile
                            else Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = material.driveFile?.title ?: material.link?.title ?: "Attachment",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                if (assignment.materials.size > 2) {
                    Text(
                        text = "+${assignment.materials.size - 2} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp)
                    )
                }
            }
            
            // Outlined action button styling for AI study help
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))
            
            OutlinedButton(
                onClick = onStudyAssist,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Get AI Study Help")
            }
        }
    }
}

// Dialog alert container visual styling for homework report
@Composable
fun HomeworkReportDialog(
    assignments: List<Assignment>,
    onDismiss: () -> Unit
) {
    val grouped = remember(assignments) { getAssignmentsForNext2Days(assignments) }
    val todayAssignments = grouped["Today"] ?: emptyList()
    val tomorrowAssignments = grouped["Tomorrow"] ?: emptyList()
    val total = todayAssignments.size + tomorrowAssignments.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Homework Report — Next 2 Days") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (total == 0) {
                    Text(
                        "No pending homework due in the next 2 days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    if (todayAssignments.isNotEmpty()) {
                        Text(
                            "Today",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        todayAssignments.forEach { assignment ->
                            ReportAssignmentRow(assignment)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    if (tomorrowAssignments.isNotEmpty()) {
                        Text(
                            "Tomorrow",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        tomorrowAssignments.forEach { assignment ->
                            ReportAssignmentRow(assignment)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun ReportAssignmentRow(assignment: Assignment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            Icons.Default.Circle,
            contentDescription = null,
            modifier = Modifier
                .size(8.dp)
                .padding(top = 4.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = assignment.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildString {
                    append(assignment.courseName)
                    assignment.maxPoints?.let { append(" · ${it.toInt()} pts") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getAssignmentsForNext2Days(assignments: List<Assignment>): Map<String, List<Assignment>> {
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val dayAfter = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }

    val todayList = mutableListOf<Assignment>()
    val tomorrowList = mutableListOf<Assignment>()

    assignments
        .filter { it.state != "TURNED_IN" && it.dueDate != null }
        .forEach { assignment ->
            val parsed = try { format.parse(assignment.dueDate!!) } catch (e: Exception) { null }
                ?: return@forEach
            val due = Calendar.getInstance().apply { time = parsed }
            when {
                !due.before(today) && due.before(tomorrow) -> todayList.add(assignment)
                !due.before(tomorrow) && due.before(dayAfter) -> tomorrowList.add(assignment)
            }
        }

    return buildMap {
        if (todayList.isNotEmpty()) put("Today", todayList)
        if (tomorrowList.isNotEmpty()) put("Tomorrow", tomorrowList)
    }
}

private fun isOverdue(dateString: String): Boolean {
    return try {
        val format = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        val dueDate = format.parse(dateString)
        dueDate?.before(java.util.Date()) == true
    } catch (e: Exception) {
        false
    }
}
