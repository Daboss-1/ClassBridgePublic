package app.classbridgeai.ClassBridge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.classbridgeai.ClassBridge.data.models.Assignment
import app.classbridgeai.ClassBridge.data.models.AttachmentUploadStatus
import app.classbridgeai.ClassBridge.data.models.Material
import app.classbridgeai.ClassBridge.data.models.UploadStatusType
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import kotlinx.coroutines.launch

data class StudySection(
    val id: String,
    val title: String,
    val content: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

// Modal dialog container surface layout for AI study guide assistance
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyAssistDialog(
    assignment: Assignment,
    viewModel: ClassBridgeViewModel,
    onDismiss: () -> Unit
) {
    var userQuestion by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var studySections by remember { mutableStateOf<List<StudySection>>(emptyList()) }
    var attachmentStatuses by remember { mutableStateOf<List<AttachmentUploadStatus>>(emptyList()) }
    var showNavigation by remember { mutableStateOf(true) }
    var selectedMaterials by remember { mutableStateOf<Set<String>>(emptySet()) }
    var uploadedFileUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val inputScrollState = rememberScrollState()
    val context = LocalContext.current
    
    fun getFileNameFromUri(uri: Uri): String {
        var fileName = "Uploaded file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            uploadedFileUri = it
            uploadedFileName = getFileNameFromUri(it)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar container visual design
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "AI Study Assistant",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                assignment.title,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, "Close")
                        }
                    },
                    actions = {
                        if (studySections.isNotEmpty()) {
                            IconButton(onClick = { showNavigation = !showNavigation }) {
                                Icon(
                                    if (showNavigation) Icons.Default.MenuOpen else Icons.Default.Menu,
                                    "Toggle Navigation"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Navigation sidebar drawer visual design
                    AnimatedVisibility(
                        visible = showNavigation && studySections.isNotEmpty(),
                        enter = fadeIn() + slideInHorizontally(),
                        exit = fadeOut() + slideOutHorizontally()
                    ) {
                        Surface(
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                item {
                                    Text(
                                        "Contents",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                items(studySections.withIndex().toList()) { (index, section) ->
                                    NavigationItem(
                                        number = index + 1,
                                        title = section.title,
                                        icon = section.icon,
                                        onClick = { }
                                    )
                                }
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp)
                    ) {
                        if (studySections.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(inputScrollState)
                            ) {
                                AssignmentDetailsCard(assignment)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Material checkbox selection list item visual styling
                                if (!assignment.materials.isNullOrEmpty()) {
                                    MaterialSelectionCard(
                                        materials = assignment.materials,
                                        selectedMaterials = selectedMaterials,
                                        onMaterialToggle = { materialId ->
                                            selectedMaterials = if (materialId in selectedMaterials) {
                                                selectedMaterials - materialId
                                            } else {
                                                selectedMaterials + materialId
                                            }
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                // File attachment surface preview container
                                FileUploadCard(
                                    uploadedFileName = uploadedFileName,
                                    onUploadClick = { filePickerLauncher.launch("*/*") },
                                    onRemoveFile = {
                                        uploadedFileUri = null
                                        uploadedFileName = null
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (attachmentStatuses.isNotEmpty()) {
                                    AttachmentStatusCard(attachmentStatuses)
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                OutlinedTextField(
                                    value = userQuestion,
                                    onValueChange = { userQuestion = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text("Your Question (Optional)") },
                                    placeholder = {
                                        Text("e.g., 'I don't understand the requirements', 'How do I get started?'")
                                    },
                                    minLines = 3,
                                    maxLines = 5,
                                    leadingIcon = {
                                        Icon(Icons.Default.QuestionAnswer, null)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                if (errorMessage != null) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Error,
                                                "Error",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                errorMessage!!,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                if (isLoading) {
                                    LoadingIndicator()
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "AI-generated content may be inaccurate. Always double-check with your teacher or course materials.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoading = true
                                        errorMessage = null
                                        try {
                                            val result = viewModel.getAssignmentStudyHelp(
                                                assignment,
                                                userQuestion,
                                                selectedMaterials,
                                                uploadedFileUri
                                            )
                                            attachmentStatuses = result?.attachmentStatuses ?: emptyList()
                                            studySections = parseAIResponse(result?.response)
                                        } catch (e: Exception) {
                                            errorMessage = e.message ?: "Failed to get AI help"
                                        } finally {
                                            isLoading = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(if (isLoading) "Getting Help..." else "Get AI Study Help")
                            }
                        } else {
                            // Section content response view layout
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            "AI-generated content may be inaccurate. Always double-check with your teacher or course materials.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                studySections.forEach { section ->
                                    StudySectionCard(section)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                
                                OutlinedButton(
                                    onClick = {
                                        studySections = emptyList()
                                        userQuestion = ""
                                        attachmentStatuses = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Refresh, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Ask Another Question")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentStatusCard(statuses: List<AttachmentUploadStatus>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Attachment Upload Status",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            statuses.forEach { status ->
                val (icon, tint, badgeText) = when (status.status) {
                    UploadStatusType.UPLOADED -> Triple(Icons.Default.CheckCircle, MaterialTheme.colorScheme.primary, "Uploaded")
                    UploadStatusType.FAILED -> Triple(Icons.Default.Error, MaterialTheme.colorScheme.error, "Failed")
                    UploadStatusType.SKIPPED -> Triple(Icons.Default.Info, MaterialTheme.colorScheme.tertiary, "Skipped")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(status.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        status.detail?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(badgeText) }
                    )
                }
            }
        }
    }
}

// Assignment details card surface container
@Composable
fun AssignmentDetailsCard(assignment: Assignment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Assignment,
                    null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Assignment Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(8.dp))
            
            DetailRow("Course", assignment.courseName)
            DetailRow("Due Date", assignment.dueDate?.toString() ?: "No due date")
            assignment.maxPoints?.let {
                DetailRow("Points", "$it points")
            }
            assignment.description?.let {
                if (it.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Description:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun NavigationItem(number: Int, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                Text("$number", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(8.dp))
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2
            )
        }
    }
}

@Composable
fun StudySectionCard(section: StudySection) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (section.id) {
                "overview" -> MaterialTheme.colorScheme.primaryContainer
                "key-concepts" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(section.icon, null, Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            MarkdownText(
                text = section.content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun LoadingIndicator() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            "Analyzing assignment and generating study guide...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MaterialSelectionCard(
    materials: List<Material>,
    selectedMaterials: Set<String>,
    onMaterialToggle: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Attachment,
                    null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Assignment Materials",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Selected Drive attachments are uploaded to Gemini for analysis. Non-Drive links are included as context.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.height(8.dp))
            
            materials.forEach { material ->
                val materialId = material.driveFile?.id ?: material.link?.url ?: ""
                val materialTitle = material.driveFile?.title ?: material.link?.title ?: "Attachment"
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onMaterialToggle(materialId) }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = materialId in selectedMaterials,
                        onCheckedChange = { onMaterialToggle(materialId) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (material.driveFile != null) Icons.Default.InsertDriveFile
                        else Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        materialTitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
fun FileUploadCard(
    uploadedFileName: String?,
    onUploadClick: () -> Unit,
    onRemoveFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.UploadFile,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Upload Additional File",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            
            if (uploadedFileName != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uploadedFileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onRemoveFile) {
                            Icon(
                                Icons.Default.Close,
                                "Remove file",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onUploadClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose File")
                }
            }
            
            Text(
                "Upload notes, screenshots, or other helpful materials",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun parseAIResponse(response: String?): List<StudySection> {
    if (response.isNullOrBlank()) return emptyList()
    
    try {
        var jsonString = response.trim()
        
        if (jsonString.startsWith("```json")) {
            jsonString = jsonString.removePrefix("```json").trim()
        } else if (jsonString.startsWith("```")) {
            jsonString = jsonString.removePrefix("```").trim()
        }
        if (jsonString.endsWith("```")) {
            jsonString = jsonString.removeSuffix("```").trim()
        }
        
        val gson = com.google.gson.Gson()
        val jsonObject = gson.fromJson(jsonString, com.google.gson.JsonObject::class.java)
        val sectionsArray = jsonObject.getAsJsonArray("sections")
        
        val iconMap = mapOf(
            "overview" to Icons.Default.Info,
            "key-concepts" to Icons.Default.Lightbulb,
            "example-solution" to Icons.Default.School,
            "common-mistakes" to Icons.Default.Warning,
            "approach" to Icons.Default.TaskAlt
        )
        
        return sectionsArray.map { element ->
            val sectionObj = element.asJsonObject
            val id = sectionObj.get("id")?.asString ?: ""
            val title = sectionObj.get("title")?.asString ?: "Section"
            val content = sectionObj.get("content")?.asString ?: ""
            val icon = iconMap[id] ?: Icons.Default.Psychology
            
            StudySection(id, title, content, icon)
        }
    } catch (e: Exception) {
        android.util.Log.e("StudyAssistDialog", "Error parsing AI response", e)
        android.util.Log.e("StudyAssistDialog", "Response was: $response")
        
        return listOf(
            StudySection("response", "AI Response", response, Icons.Default.Psychology)
        )
    }
}
