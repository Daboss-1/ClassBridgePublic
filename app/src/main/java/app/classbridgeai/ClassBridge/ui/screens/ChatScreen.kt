package app.classbridgeai.ClassBridge.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.classbridgeai.ClassBridge.data.models.Assignment
import app.classbridgeai.ClassBridge.data.models.AssignmentAttachment
import app.classbridgeai.ClassBridge.data.models.ChatMessage
import app.classbridgeai.ClassBridge.data.models.ChatRoom
import app.classbridgeai.ClassBridge.viewmodel.ClassBridgeViewModel
import app.classbridgeai.ClassBridge.viewmodel.UiState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Real-time chat room list and message stream UI layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ClassBridgeViewModel,
    onBack: () -> Unit
) {
    val chatRooms by viewModel.chatRooms.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var selectedChatRoom by remember { mutableStateOf<ChatRoom?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.getUserChatRooms()
    }

    LaunchedEffect(uiState) {
        val currentState = uiState
        if (currentState is UiState.Error) {
            snackbarHostState.showSnackbar(currentState.message)
        }
    }

    if (selectedChatRoom != null) {
        ChatRoomScreen(
            viewModel = viewModel,
            chatRoom = selectedChatRoom!!,
            onBack = { selectedChatRoom = null }
        )
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Chat Rooms") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showJoinDialog = true }) {
                            Icon(Icons.Default.Login, contentDescription = "Join with code")
                        }
                    }
                )
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { showCreateDialog = true },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Create Room") }
                )
            }
        ) { padding ->
            if (chatRooms.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No chat rooms yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create one or join with a code",
                            style = MaterialTheme.typography.bodySmall,
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
                    items(chatRooms) { chatRoom ->
                        ChatRoomCard(
                            chatRoom = chatRoom,
                            onClick = { selectedChatRoom = chatRoom }
                        )
                    }
                }
            }
        }
        
        if (showCreateDialog) {
            CreateChatRoomDialog(
                viewModel = viewModel,
                onDismiss = { showCreateDialog = false }
            )
        }
        
        if (showJoinDialog) {
            JoinChatRoomDialog(
                viewModel = viewModel,
                onDismiss = { showJoinDialog = false }
            )
        }
    }
}

// Chat room list item card layout and surface container
@Composable
fun ChatRoomCard(
    chatRoom: ChatRoom,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Group,
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chatRoom.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                chatRoom.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${chatRoom.members.size} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Active chat room screen with message stream and input bar visual layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: ClassBridgeViewModel,
    chatRoom: ChatRoom,
    onBack: () -> Unit
) {
    val messages by viewModel.currentChatMessages.collectAsState()
    val assignments by viewModel.assignments.collectAsState()
    var messageText by remember { mutableStateOf("") }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAssignmentPicker by remember { mutableStateOf(false) }
    var selectedAssignment by remember { mutableStateOf<Assignment?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(chatRoom.id) {
        viewModel.observeChatMessages(chatRoom.id)
    }

    LaunchedEffect(Unit) {
        if (assignments.isEmpty()) viewModel.loadAllAssignments()
    }

    if (showInfoDialog) {
        RoomInfoDialog(chatRoom = chatRoom, onDismiss = { showInfoDialog = false })
    }

    if (showAssignmentPicker) {
        AssignmentPickerSheet(
            assignments = assignments,
            onSelect = { selectedAssignment = it; showAssignmentPicker = false },
            onDismiss = { showAssignmentPicker = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(chatRoom.name)
                        Text(
                            "${chatRoom.members.size} members",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showInfoDialog = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Room info")
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, tonalElevation = 0.dp) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    selectedAssignment?.let { assignment ->
                        AssignmentAttachmentPreview(
                            assignment = assignment,
                            onRemove = { selectedAssignment = null }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(onClick = { showAssignmentPicker = true }) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Attach assignment",
                                tint = if (selectedAssignment != null)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Type a message...") },
                            maxLines = 4
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        val canSend = messageText.isNotBlank() || selectedAssignment != null
                        IconButton(
                            onClick = {
                                if (canSend) {
                                    scope.launch {
                                        viewModel.sendChatMessage(
                                            chatRoom.id,
                                            messageText,
                                            selectedAssignment?.toAttachmentModel()
                                        )
                                        messageText = ""
                                        selectedAssignment = null
                                    }
                                }
                            },
                            enabled = canSend
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (canSend)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            reverseLayout = true
        ) {
            items(messages.reversed()) { message ->
                ChatMessageBubble(message = message)
            }
        }
    }
}

// Chat message bubble surface styling and avatar alignment layout
@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isCurrentUser = false

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {
            if (!isCurrentUser) {
                Text(
                    text = message.userName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, bottom = 4.dp)
                )
            }

            // Attached assignment card container visual layout
            message.assignmentAttachment?.let { attachment ->
                AssignmentMessageCard(attachment = attachment)
                if (message.message.isNotBlank()) Spacer(modifier = Modifier.height(4.dp))
            }

            // Message text bubble surface styling
            if (message.message.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = if (isCurrentUser) 16.dp else 4.dp,
                        topEnd = if (isCurrentUser) 4.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    ),
                    color = if (isCurrentUser)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = message.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (message.assignmentAttachment != null) {
                Text(
                    text = formatTimestamp(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatRoomDialog(
    viewModel: ClassBridgeViewModel,
    onDismiss: () -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var roomDescription by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Chat Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text("Room Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = roomDescription,
                    onValueChange = { roomDescription = it },
                    label = { Text("Description (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomName.isNotBlank()) {
                        scope.launch {
                            viewModel.createChatRoom(roomName, roomDescription, "General")
                            onDismiss()
                        }
                    }
                },
                enabled = roomName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinChatRoomDialog(
    viewModel: ClassBridgeViewModel,
    onDismiss: () -> Unit
) {
    var joinCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        if (isLoading) {
            when (val state = uiState) {
                is UiState.Success -> onDismiss()
                is UiState.Error -> {
                    errorMessage = state.message
                    isLoading = false
                }
                else -> {}
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join Chat Room") },
        text = {
            Column {
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = {
                        joinCode = it.uppercase()
                        errorMessage = null
                    },
                    label = { Text("Room Code") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ABC123") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (joinCode.isNotBlank() && !isLoading) {
                        isLoading = true
                        errorMessage = null
                        viewModel.joinChatRoomWithCode(joinCode)
                    }
                },
                enabled = joinCode.isNotBlank() && !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Join")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Room details dialog visual layout
@Composable
fun RoomInfoDialog(chatRoom: ChatRoom, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val joinCode = chatRoom.joinCode ?: "N/A"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chatRoom.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (chatRoom.description.isNotBlank()) {
                    Text(chatRoom.description, style = MaterialTheme.typography.bodyMedium)
                    HorizontalDivider()
                }

                // Room invite code display container visual layout
                Text("Invite Code", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = joinCode,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Join Code", joinCode))
                            scope.launch { snackbarHostState.showSnackbar("Code copied!") }
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy code",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }

                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Text("Members", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${chatRoom.members.size} / ${chatRoom.maxMembers}",
                            style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    }
                    if (chatRoom.subject.isNotBlank()) {
                        Column {
                            Text("Subject", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(chatRoom.subject, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Text("Created by ${chatRoom.creatorName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// Assignment message attachment preview container layout
@Composable
private fun AssignmentMessageCard(attachment: AssignmentAttachment) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Assignment",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                attachment.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                attachment.courseName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            if (attachment.dueDate != null || attachment.maxPoints != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    attachment.dueDate?.let {
                        Text(
                            "Due: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    attachment.maxPoints?.let {
                        Text(
                            "${it.toInt()} pts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            attachment.description?.let { desc ->
                if (desc.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// Assignment attachment preview chip row visual layout
@Composable
private fun AssignmentAttachmentPreview(assignment: Assignment, onRemove: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    assignment.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    assignment.courseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// Assignment selection bottom sheet layout
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentPickerSheet(
    assignments: List<Assignment>,
    onSelect: (Assignment) -> Unit,
    onDismiss: () -> Unit
) {
    val grouped = remember(assignments) { assignments.groupBy { it.courseName } }
    var selectedCourse by remember { mutableStateOf(grouped.keys.firstOrNull()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Attach Assignment",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (grouped.size > 1) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(grouped.keys.toList()) { course ->
                        FilterChip(
                            selected = course == selectedCourse,
                            onClick = { selectedCourse = course },
                            label = { Text(course, maxLines = 1) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            val displayAssignments = grouped[selectedCourse] ?: emptyList()

            if (assignments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading assignments...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (displayAssignments.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No assignments in this course", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(displayAssignments) { assignment ->
                        AssignmentPickerItem(assignment = assignment, onClick = { onSelect(assignment) })
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun AssignmentPickerItem(assignment: Assignment, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    assignment.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    assignment.dueDate?.let {
                        Text(
                            "Due: $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    assignment.maxPoints?.let {
                        Text(
                            "${it.toInt()} pts",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Assignment.toAttachmentModel() = AssignmentAttachment(
    id = id,
    title = title,
    courseName = courseName,
    courseId = courseId,
    description = description,
    dueDate = dueDate,
    maxPoints = maxPoints,
    alternateLink = alternateLink
)

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - date.time
    
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(date)
    }
}
