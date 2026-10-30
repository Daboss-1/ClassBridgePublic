package app.classbridgeai.ClassBridge.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AIMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val attachedFileName: String? = null
)

// AI study assistant conversational chat screen layout and interactive UI components
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIStudyScreen(
    viewModel: ClassBridgeViewModel,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf(listOf<AIMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFileUri = uri
        selectedFileName = uri?.lastPathSegment ?: "document"
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Study Helper")
                        Text(
                            if (selectedFileName != null) "File: $selectedFileName" else "Ask me anything!",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(
                            if (selectedFileName != null) Icons.Default.AttachFile else Icons.Default.UploadFile,
                            contentDescription = "Upload file",
                            tint = if (selectedFileName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (selectedFileName != null) {
                        IconButton(onClick = {
                            selectedFileUri = null
                            selectedFileName = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear file")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 8.dp,
                tonalElevation = 0.dp
            ) {
                Column {
                    // Quick prompt suggestion chip visual row styling
                    if (messages.isEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(quickPrompts) { prompt ->
                                SuggestionChip(
                                    onClick = {
                                        inputText = prompt
                                    },
                                    label = { Text(prompt, maxLines = 1) }
                                )
                            }
                        }
                    }
                    
                    // Input bar row container visual layout
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            selectedFileName?.let { fileName ->
                                AssistChip(
                                    onClick = {
                                        selectedFileUri = null
                                        selectedFileName = null
                                    },
                                    label = { Text(fileName, maxLines = 1) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.AttachFile,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Ask a question...") },
                                maxLines = 4,
                                leadingIcon = {
                                    Icon(Icons.Default.Psychology, contentDescription = null)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !isLoading) {
                                    val userMessage = inputText
                                    val fileName = selectedFileName
                                    val capturedFileUri = selectedFileUri

                                    messages = messages + AIMessage(userMessage, true, attachedFileName = fileName)
                                    inputText = ""
                                    selectedFileUri = null
                                    selectedFileName = null
                                    isLoading = true

                                    scope.launch {
                                        val response = viewModel.getAIStudyHelp(userMessage, capturedFileUri)
                                        messages = messages + AIMessage(
                                            response ?: "Sorry, I couldn't process that request.",
                                            false
                                        )
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = inputText.isNotBlank() && !isLoading
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank() && !isLoading)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // Initial empty state graphic illustration and helper card layout
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "AI Study Assistant",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Ask me to help with homework, explain concepts, or study for exams!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text(
                        "Example questions:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    exampleQuestions.forEach { question ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Text(
                                question,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

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
                                "AI can make mistakes. Always verify information with your teacher or a trusted source.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Card(
                                modifier = Modifier.widthIn(max = 280.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Thinking...")
                                }
                            }
                        }
                    }
                }
                
                items(
                    items = messages,
                    key = { message -> message.timestamp }
                ) { message ->
                    AIMessageBubble(message = message)
                }
            }
        }
    }
}

// Chat bubble surface shape, padding, and background color styling
@Composable
fun AIMessageBubble(message: AIMessage) {
    var visibleCharacters by remember(message.timestamp) {
        mutableIntStateOf(if (message.isUser) message.content.length else 0)
    }
    val animatedContent = if (message.isUser) {
        message.content
    } else {
        message.content.take(visibleCharacters)
    }

    LaunchedEffect(message.timestamp) {
        if (!message.isUser) {
            for (index in 1..message.content.length) {
                visibleCharacters = index
                delay(12)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (message.isUser)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(
                    topStart = if (message.isUser) 16.dp else 4.dp,
                    topEnd = if (message.isUser) 4.dp else 16.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (message.attachedFileName != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = message.attachedFileName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
                    }
                    if (message.isUser) {
                        Text(
                            text = animatedContent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        if (visibleCharacters == message.content.length) {
                            MarkdownText(
                                text = animatedContent,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = animatedContent,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            if (message.isUser) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (!message.isUser) {
            Text(
                "AI can make mistakes. Verify important information.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 40.dp, top = 2.dp)
            )
        }
    }
}

private val quickPrompts = listOf(
    "Explain this concept",
    "Help with homework",
    "Summarize this document",
    "Quiz me on this topic"
)

private val exampleQuestions = listOf(
    "Can you explain the Pythagorean theorem?",
    "Help me understand photosynthesis",
    "What are the main causes of World War 2?",
    "Quiz me on basic algebra"
)
