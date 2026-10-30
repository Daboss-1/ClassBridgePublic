package app.classbridgeai.ClassBridge.viewmodel

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaLibraryInfo.TAG
import androidx.media3.common.util.UnstableApi
import app.classbridgeai.ClassBridge.data.models.*
import app.classbridgeai.ClassBridge.data.repository.DashboardStats
import app.classbridgeai.ClassBridge.data.repository.ClassBridgeRepository
import app.classbridgeai.ClassBridge.data.services.AIStudyResponse
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ClassBridgeViewModel @OptIn(UnstableApi::class) constructor
    (context: Context) : ViewModel() {
    
    init {
        Log.d(TAG, "ViewModel initialized")
        try {
            Log.d(TAG, "Creating repository")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ViewModel", e)
            throw e
        }
    }
    
    private val _consentRequiredEvent = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    val consentRequiredEvent: SharedFlow<Intent> = _consentRequiredEvent.asSharedFlow()

    private val repository = ClassBridgeRepository(context) { intent ->
        viewModelScope.launch { _consentRequiredEvent.emit(intent) }
    }
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private fun currentUserId(): String {
        return auth.currentUser?.uid ?: ""
    }

    private fun currentUserName(): String {
        return auth.currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "User"
    }

    private fun currentUserEmail(): String {
        return auth.currentUser?.email?.takeIf { it.isNotBlank() } ?: ""
    }
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    private val _dashboardStats = MutableStateFlow<DashboardStats?>(null)
    val dashboardStats: StateFlow<DashboardStats?> = _dashboardStats.asStateFlow()
    
    private val _courses = MutableStateFlow<List<Course>>(emptyList())
    val courses: StateFlow<List<Course>> = _courses.asStateFlow()
    
    private val _assignments = MutableStateFlow<List<Assignment>>(emptyList())
    val assignments: StateFlow<List<Assignment>> = _assignments.asStateFlow()
    
    private val _grades = MutableStateFlow<List<GradeData>>(emptyList())
    val grades: StateFlow<List<GradeData>> = _grades.asStateFlow()
    
    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms.asStateFlow()
    
    private val _currentChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val currentChatMessages: StateFlow<List<ChatMessage>> = _currentChatMessages.asStateFlow()
    
    private val _studySessions = MutableStateFlow<List<StudySession>>(emptyList())
    val studySessions: StateFlow<List<StudySession>> = _studySessions.asStateFlow()
    
    private val _aiResponse = MutableStateFlow<AIStudyResponse?>(null)
    val aiResponse: StateFlow<AIStudyResponse?> = _aiResponse.asStateFlow()

    private val _courseTeachers = MutableStateFlow<Map<String, List<app.classbridgeai.ClassBridge.data.models.Teacher>>>(emptyMap())
    val courseTeachers: StateFlow<Map<String, List<app.classbridgeai.ClassBridge.data.models.Teacher>>> = _courseTeachers.asStateFlow()
    
    fun loadDashboardStats(userId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getDashboardStats(userId).fold(
                onSuccess = { stats ->
                    _dashboardStats.value = stats
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error")
                }
            )
        }
    }
    
    fun loadCourses() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getCourses().fold(
                onSuccess = { courseList ->
                    _courses.value = courseList
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load courses")
                }
            )
        }
    }
    
    fun loadAllAssignments() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getAllAssignments().fold(
                onSuccess = { assignmentList ->
                    _assignments.value = assignmentList
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load assignments")
                }
            )
        }
    }
    
    fun loadUpcomingAssignments(daysAhead: Int = 7) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getUpcomingAssignments(daysAhead).fold(
                onSuccess = { assignmentList ->
                    _assignments.value = assignmentList
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load assignments")
                }
            )
        }
    }
    
    fun loadCourseTeachers(courseIds: List<String>, forceRefresh: Boolean = false) {
        viewModelScope.launch {
            courseIds.forEach { courseId ->
                if (forceRefresh || !_courseTeachers.value.containsKey(courseId)) {
                    repository.getTeachersForCourse(courseId)
                        .onSuccess { teachers ->
                            _courseTeachers.value = _courseTeachers.value + (courseId to teachers)
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Failed to load teachers for course $courseId", error)
                        }
                }
            }
        }
    }

    fun loadGrades() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getGrades().fold(
                onSuccess = { gradeList ->
                    _grades.value = gradeList
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load grades")
                }
            )
        }
    }
    
    fun loadUserChatRooms(userId: String) {
        Log.d("CHAT_DEBUG", "loadUserChatRooms: querying for userId='$userId'")
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.getUserChatRooms(userId).fold(
                onSuccess = { rooms ->
                    Log.d("CHAT_DEBUG", "loadUserChatRooms: got ${rooms.size} rooms -> ${rooms.map { it.name }}")
                    _chatRooms.value = rooms
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    Log.e("CHAT_DEBUG", "loadUserChatRooms: FAILED - ${error.message}", error)
                    _uiState.value = UiState.Error(error.message ?: "Failed to load chat rooms")
                }
            )
        }
    }

    fun createChatRoom(room: ChatRoom, userId: String) {
        Log.d("CHAT_DEBUG", "createChatRoom: name='${room.name}' creatorId='${room.creatorId}'")
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.createChatRoom(room).fold(
                onSuccess = { roomId ->
                    Log.d("CHAT_DEBUG", "createChatRoom: SUCCESS roomId='$roomId', now reloading rooms")
                    loadUserChatRooms(userId)
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    Log.e("CHAT_DEBUG", "createChatRoom: FAILED - ${error.message}", error)
                    _uiState.value = UiState.Error(error.message ?: "Failed to create chat room")
                }
            )
        }
    }

    fun joinChatRoomWithCode(code: String, userId: String, userEmail: String) {
        Log.d("CHAT_DEBUG", "joinChatRoomWithCode: code='$code' userId='$userId' email='$userEmail'")
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            repository.joinChatRoomWithCode(code, userId, userEmail).fold(
                onSuccess = { roomId ->
                    Log.d("CHAT_DEBUG", "joinChatRoomWithCode: SUCCESS roomId='$roomId', now reloading rooms")
                    loadUserChatRooms(userId)
                    _uiState.value = UiState.Success
                },
                onFailure = { error ->
                    Log.e("CHAT_DEBUG", "joinChatRoomWithCode: FAILED - ${error.message}", error)
                    _uiState.value = UiState.Error(error.message ?: "Failed to join chat room")
                }
            )
        }
    }
    
    fun observeChatMessages(roomId: String) {
        viewModelScope.launch {
            repository.observeMessages(roomId)
                .catch { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load messages")
                }
                .collect { messages ->
                    _currentChatMessages.value = messages
                }
        }
    }
    
    fun sendMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.sendMessage(message).fold(
                onSuccess = { messageId ->
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to send message")
                }
            )
        }
    }
    
    fun getUserChatRooms() {
        val userId = currentUserId()
        Log.d("CHAT_DEBUG", "getUserChatRooms: currentUserId='$userId' isSignedIn=${auth.currentUser != null}")
        if (userId.isNotBlank()) {
            loadUserChatRooms(userId)
        } else {
            Log.w("CHAT_DEBUG", "getUserChatRooms: userId is blank, skipping load")
        }
    }
    
    fun sendChatMessage(
        roomId: String,
        messageText: String,
        assignmentAttachment: app.classbridgeai.ClassBridge.data.models.AssignmentAttachment? = null
    ) {
        val userId = currentUserId()
        if (userId.isBlank()) return

        val message = ChatMessage(
            roomId = roomId,
            userId = userId,
            userName = currentUserName(),
            message = messageText,
            timestamp = System.currentTimeMillis(),
            assignmentAttachment = assignmentAttachment
        )
        sendMessage(message)
    }
    
    fun createChatRoom(name: String, description: String?, subject: String) {
        val userId = currentUserId()
        Log.d("CHAT_DEBUG", "createChatRoom(wrapper): name='$name' userId='$userId' userName='${currentUserName()}'")
        if (userId.isBlank()) {
            Log.w("CHAT_DEBUG", "createChatRoom(wrapper): userId is blank, aborting")
            return
        }

        val room = ChatRoom(
            name = name,
            description = description ?: "",
            subject = subject,
            creatorId = userId,
            creatorName = currentUserName()
        )
        createChatRoom(room, userId)
    }

    fun joinChatRoomWithCode(code: String) {
        val userId = currentUserId()
        val email = currentUserEmail()
        Log.d("CHAT_DEBUG", "joinChatRoomWithCode(wrapper): code='$code' userId='$userId' email='$email'")
        if (userId.isNotBlank()) {
            joinChatRoomWithCode(code, userId, email)
        } else {
            Log.w("CHAT_DEBUG", "joinChatRoomWithCode(wrapper): userId is blank, aborting")
        }
    }
    
    fun deleteMessage(roomId: String, messageId: String) {
        viewModelScope.launch {
            repository.deleteMessage(roomId, messageId).fold(
                onSuccess = {
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to delete message")
                }
            )
        }
    }
    
    fun startStudySession(session: StudySession) {
        viewModelScope.launch {
            repository.createStudySession(session).fold(
                onSuccess = { sessionId ->
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to start session")
                }
            )
        }
    }
    
    fun endStudySession(sessionId: String) {
        viewModelScope.launch {
            val endTime = System.currentTimeMillis()
            repository.endStudySession(sessionId, endTime).fold(
                onSuccess = {
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to end session")
                }
            )
        }
    }
    
    fun loadStudySessions(userId: String) {
        viewModelScope.launch {
            repository.getUserStudySessions(userId).fold(
                onSuccess = { sessions ->
                    _studySessions.value = sessions
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to load sessions")
                }
            )
        }
    }
    
    fun getStudyHelp(assignment: String, description: String?, userQuery: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            repository.getStudyHelp(assignment, description, userQuery).fold(
                onSuccess = { response ->
                    _aiResponse.value = response
                    _uiState.value = UiState.Success
                },
                onFailure = {
                    val fallback = repository.getMockStudyHelp(assignment, userQuery)
                    _aiResponse.value = fallback
                    _uiState.value = UiState.Success
                }
            )
        }
    }
    
    fun clearAiResponse() {
        _aiResponse.value = null
    }
    
    suspend fun getAIStudyHelp(question: String, fileUri: android.net.Uri? = null): String? {
        return try {
            repository.getSimpleStudyHelp(question, fileUri)
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun getAssignmentStudyHelp(
        assignment: Assignment,
        userQuestion: String,
        selectedMaterials: Set<String> = emptySet(),
        uploadedFileUri: android.net.Uri? = null
    ): AssignmentStudyHelpResult? {
        return try {
            repository.getAssignmentStudyHelp(
                assignment,
                userQuestion,
                selectedMaterials,
                uploadedFileUri
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun createOrUpdateUserProfile(profile: UserProfile) {
        viewModelScope.launch {
            repository.createOrUpdateUserProfile(profile).fold(
                onSuccess = {
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Failed to update profile")
                }
            )
        }
    }
}

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    object Success : UiState()
    data class Error(val message: String) : UiState()
}

class ClassBridgeViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        Log.d("ViewModelFactory", "Creating ViewModel: ${modelClass.simpleName}")
        if (modelClass.isAssignableFrom(ClassBridgeViewModel::class.java)) {
            try {
                Log.d("ViewModelFactory", "Instantiating ClassBridgeViewModel")
                @Suppress("UNCHECKED_CAST")
                val viewModel = ClassBridgeViewModel(context) as T
                Log.d("ViewModelFactory", "ClassBridgeViewModel created successfully")
                return viewModel
            } catch (e: Exception) {
                Log.e("ViewModelFactory", "Error creating ClassBridgeViewModel", e)
                throw e
            }
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
