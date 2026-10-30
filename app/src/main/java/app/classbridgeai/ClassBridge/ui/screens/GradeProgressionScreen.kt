package app.classbridgeai.ClassBridge.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.classbridgeai.ClassBridge.data.models.GradeAssignment
import app.classbridgeai.ClassBridge.data.models.GradeData
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeProgressionScreen(
    grades: List<GradeData>,
    initialCourseId: String,
    onBack: () -> Unit
) {
    var selectedCourseId by remember { mutableStateOf(initialCourseId) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val selectedGrade = grades.firstOrNull { it.courseId == selectedCourseId }

    val gradedAssignments = remember(selectedCourseId, grades) {
        selectedGrade?.assignments
            ?.filter { it.earnedPoints != null && it.maxPoints != null && it.maxPoints > 0 }
            ?.sortedWith(compareBy { parseDueDate(it.dueDate) })
            ?: emptyList()
    }

    val runningAverages = remember(gradedAssignments) {
        var totalEarned = 0.0
        var totalMax = 0.0
        gradedAssignments.map { a ->
            totalEarned += a.earnedPoints!!
            totalMax += a.maxPoints!!
            (totalEarned / totalMax) * 100
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Grade Progression") },
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
            if (grades.size > 1) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grades.forEach { grade ->
                            FilterChip(
                                selected = grade.courseId == selectedCourseId,
                                onClick = {
                                    selectedCourseId = grade.courseId
                                    selectedIndex = null
                                },
                                label = { Text(grade.courseName, maxLines = 1) }
                            )
                        }
                    }
                }
            }

            item {
                ProgressionSummaryCard(
                    currentAverage = runningAverages.lastOrNull(),
                    letterGrade = selectedGrade?.letterGrade,
                    gradedCount = gradedAssignments.size
                )
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Running Average — tap a point for details",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        if (gradedAssignments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No graded assignments yet",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            GradeLineChart(
                                assignments = gradedAssignments,
                                runningAverages = runningAverages,
                                selectedIndex = selectedIndex,
                                primaryColor = MaterialTheme.colorScheme.primary,
                                highlightColor = MaterialTheme.colorScheme.tertiary,
                                gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                onPointTapped = { i ->
                                    selectedIndex = if (selectedIndex == i) null else i
                                }
                            )
                        }
                    }
                }
            }

            selectedIndex?.let { idx ->
                val assignment = gradedAssignments.getOrNull(idx)
                val avg = runningAverages.getOrNull(idx)
                if (assignment != null && avg != null) {
                    item {
                        AssignmentDetailCard(
                            assignment = assignment,
                            runningAverage = avg,
                            position = idx + 1,
                            total = gradedAssignments.size
                        )
                    }
                }
            }
        }
    }
}

// Progression summary hero card layout and styling
@Composable
private fun ProgressionSummaryCard(
    currentAverage: Double?,
    letterGrade: String?,
    gradedCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Current Average",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    currentAverage?.let { "%.1f%%".format(it) } ?: "N/A",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (letterGrade != null) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(48.dp)
                        .padding(vertical = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Letter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        letterGrade,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(48.dp)
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Graded",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    "$gradedCount",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    if (gradedCount == 1) "assignment" else "assignments",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// Interactive custom Canvas line chart aesthetic visual design
@Composable
private fun GradeLineChart(
    assignments: List<GradeAssignment>,
    runningAverages: List<Double>,
    selectedIndex: Int?,
    primaryColor: Color,
    highlightColor: Color,
    gridColor: Color,
    onPointTapped: (Int) -> Unit
) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .pointerInput(assignments.size) {
                detectTapGestures { tapOffset ->
                    if (assignments.isEmpty()) return@detectTapGestures
                    val padL = 52f
                    val padR = 20f
                    val availW = size.width - padL - padR
                    val step = if (assignments.size > 1) availW / (assignments.size - 1).toFloat()
                               else availW / 2f
                    val nearest = assignments.indices.minByOrNull { i ->
                        val x = if (assignments.size == 1) padL + availW / 2f else padL + i * step
                        kotlin.math.abs(tapOffset.x - x)
                    }
                    if (nearest != null) onPointTapped(nearest)
                }
            }
    ) {
        val padL = 52f
        val padR = 20f
        val padTop = 16f
        val padBot = 36f
        val w = size.width - padL - padR
        val h = size.height - padTop - padBot

        // Y-axis gridline visual styling and percentage label alignment
        val labelPaintY = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 28f
            isAntiAlias = true
        }
        listOf(0, 25, 50, 75, 100).forEach { pct ->
            val y = padTop + h * (1f - pct / 100f)
            drawLine(gridColor, Offset(padL, y), Offset(size.width - padR, y), strokeWidth = 1f)
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText("$pct%", 6f, y + 5f, labelPaintY)
            }
        }

        if (assignments.isEmpty()) return@Canvas

        val step = if (assignments.size > 1) w / (assignments.size - 1).toFloat() else w / 2f

        fun xAt(i: Int) = if (assignments.size == 1) padL + w / 2f else padL + i * step
        fun yAt(avg: Double) = padTop + h * (1f - (avg / 100f)).toFloat()

        val pts = runningAverages.mapIndexed { i, avg -> Offset(xAt(i), yAt(avg)) }

        // Subtle gradient fill under the line chart curve
        if (pts.size > 1) {
            val fill = Path().apply {
                moveTo(pts.first().x, padTop + h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, padTop + h)
                close()
            }
            drawPath(fill, primaryColor.copy(alpha = 0.12f))

            // Primary line chart stroke styling
            val line = Path().apply {
                moveTo(pts.first().x, pts.first().y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(line, primaryColor, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Circular node highlights for interactive chart points
        pts.forEachIndexed { i, pt ->
            val isSelected = i == selectedIndex
            val color = if (isSelected) highlightColor else primaryColor
            val r = if (isSelected) 14f else 7f
            drawCircle(Color.White, r + 3f, pt)
            drawCircle(color, r, pt)
        }

        // Horizontal axis date typography labels
        val labelPaintX = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 26f
            isAntiAlias = true
        }
        val firstLabel = assignments.first().dueDate?.let { shortenDate(it) } ?: "#1"
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawText(firstLabel, pts.first().x - 10f, size.height - 4f, labelPaintX)
        }
        if (assignments.size > 1) {
            val lastLabel = assignments.last().dueDate?.let { shortenDate(it) } ?: "#${assignments.size}"
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawText(lastLabel, pts.last().x - 36f, size.height - 4f, labelPaintX)
            }
        }
    }
}

// Detail card container layout and surface background styling
@Composable
private fun AssignmentDetailCard(
    assignment: GradeAssignment,
    runningAverage: Double,
    position: Int,
    total: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    "Assignment $position of $total",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
                if (assignment.late) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Late",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                assignment.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            assignment.dueDate?.let {
                Text(
                    "Due $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProgressionDetailStat(
                    label = "Score",
                    value = if (assignment.earnedPoints != null && assignment.maxPoints != null)
                        "${assignment.earnedPoints.toInt()}/${assignment.maxPoints.toInt()}"
                    else "N/A",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                ProgressionDetailStat(
                    label = "Assignment %",
                    value = assignment.grade?.let { "%.1f%%".format(it) } ?: "—",
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                ProgressionDetailStat(
                    label = "Running Avg",
                    value = "%.1f%%".format(runningAverage),
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun ProgressionDetailStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.6f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

private fun parseDueDate(dateStr: String?): Long {
    dateStr ?: return Long.MAX_VALUE
    return try {
        SimpleDateFormat("MMM dd, yyyy", Locale.US).parse(dateStr)?.time ?: Long.MAX_VALUE
    } catch (_: Exception) {
        Long.MAX_VALUE
    }
}

private fun shortenDate(dateStr: String): String = dateStr.substringBeforeLast(",").trim()
