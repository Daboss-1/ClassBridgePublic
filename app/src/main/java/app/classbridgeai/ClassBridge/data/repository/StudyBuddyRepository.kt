package app.classbridgeai.ClassBridge.data.repository

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.ClassBridge.data.services.GeminiService
import com.example.ClassBridge.data.services.ClassroomDataProvider
import app.classbridgeai.ClassBridge.data.models.*
import app.classbridgeai.ClassBridge.data.services.*
import kotlinx.coroutines.flow.Flow

class ClassBridgeRepository(
    context: Context,
    onConsentRequired: ((Intent) -> Unit)? = null
) {

    private val appContext = context.applicationContext
    private val classroomService: GoogleClassroomService
    private val firestoreService: FirestoreService
    private val aiStudyService: AIStudyService
    private val geminiService: GeminiService

    init {
        try {
            Log.d(TAG, "Initializing ClassBridgeRepository")
            classroomService = GoogleClassroomService(context, onConsentRequired)
            Log.d(TAG, "GoogleClassroomService initialized")
            firestoreService = FirestoreService()
            Log.d(TAG, "FirestoreService initialized")
            aiStudyService = AIStudyService()
            Log.d(TAG, "AIStudyService initialized")
            geminiService = GeminiService(context, object : ClassroomDataProvider {
                override suspend fun getCourses() = this@ClassBridgeRepository.getCourses()
                override suspend fun getAllAssignments() = this@ClassBridgeRepository.getAllAssignments()
                override suspend fun getGrades() = this@ClassBridgeRepository.getGrades()
            })
            Log.d(TAG, "GeminiService initialized")
            Log.d(TAG, "ClassBridgeRepository initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ClassBridgeRepository", e)
            throw e
        }
    }
    
    companion object {
        private const val TAG = "ClassBridgeRepository"
    }
    
    suspend fun getCourses(): Result<List<Course>> {
        return classroomService.getCourses()
    }
    
    suspend fun getCourseWork(courseId: String): Result<List<Assignment>> {
        return classroomService.getCourseWork(courseId)
    }
    
    suspend fun getAllAssignments(): Result<List<Assignment>> {
        return classroomService.getAllAssignments().also { result ->
            result.onSuccess { AssignmentCache.save(appContext, it) }
        }
    }
    
    suspend fun getGrades(): Result<List<GradeData>> {
        return classroomService.getGrades()
    }
    
    suspend fun getStudentSubmissions(courseId: String, courseWorkId: String): 
        Result<List<StudentSubmission>> {
        return classroomService.getStudentSubmissions(courseId, courseWorkId)
    }
    
    suspend fun getTeachersForCourse(courseId: String): Result<List<Teacher>> {
        return classroomService.getTeachersForCourse(courseId)
    }

    suspend fun getUpcomingAssignments(daysAhead: Int = 7): Result<List<Assignment>> {
        return try {
            val allAssignments = getAllAssignments().getOrThrow()
            val now = System.currentTimeMillis()
            val cutoff = now + (daysAhead * 24 * 60 * 60 * 1000L)
            
            val upcoming = allAssignments.filter { assignment ->
                assignment.dueDate != null
            }
            
            Result.success(upcoming)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> {
        return firestoreService.createOrUpdateUserProfile(profile)
    }
    
    suspend fun getUserProfile(uid: String): Result<UserProfile> {
        return firestoreService.getUserProfile(uid)
    }
    
    suspend fun createChatRoom(room: ChatRoom): Result<String> {
        return firestoreService.createChatRoom(room)
    }
    
    suspend fun getChatRoom(roomId: String): Result<ChatRoom> {
        return firestoreService.getChatRoom(roomId)
    }
    
    suspend fun getUserChatRooms(userId: String): Result<List<ChatRoom>> {
        return firestoreService.getUserChatRooms(userId)
    }
    
    suspend fun joinChatRoom(roomId: String, userId: String): Result<Unit> {
        return firestoreService.joinChatRoom(roomId, userId)
    }
    
    suspend fun joinChatRoomWithCode(code: String, userId: String, userEmail: String): 
        Result<String> {
        return firestoreService.joinChatRoomWithCode(code, userId, userEmail)
    }
    
    suspend fun leaveChatRoom(roomId: String, userId: String): Result<Unit> {
        return firestoreService.leaveChatRoom(roomId, userId)
    }
    
    suspend fun regenerateJoinCode(roomId: String): Result<String> {
        return firestoreService.regenerateJoinCode(roomId)
    }
    
    suspend fun sendMessage(message: ChatMessage): Result<String> {
        return firestoreService.sendMessage(message)
    }
    
    fun observeMessages(roomId: String): Flow<List<ChatMessage>> {
        return firestoreService.observeMessages(roomId)
    }
    
    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> {
        return firestoreService.deleteMessage(roomId, messageId)
    }
    
    suspend fun createStudySession(session: StudySession): Result<String> {
        return firestoreService.createStudySession(session)
    }
    
    suspend fun endStudySession(sessionId: String, endTime: Long): Result<Unit> {
        return firestoreService.endStudySession(sessionId, endTime)
    }
    
    suspend fun getUserStudySessions(userId: String, limit: Int = 50): 
        Result<List<StudySession>> {
        return firestoreService.getUserStudySessions(userId, limit)
    }
    
    suspend fun getTotalStudyTime(userId: String, daysBack: Int = 7): Result<Long> {
        return try {
            val sessions = getUserStudySessions(userId, 100).getOrThrow()
            val cutoffTime = System.currentTimeMillis() - (daysBack * 24 * 60 * 60 * 1000L)
            
            val totalTime = sessions
                .filter { it.startTime >= cutoffTime && it.duration > 0 }
                .sumOf { it.duration }
            
            Result.success(totalTime)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getSimpleStudyHelp(question: String, fileUri: android.net.Uri? = null): String? {
        return geminiService.getSimpleStudyHelp(question, fileUri)
    }

    suspend fun getAssignmentStudyHelp(
        assignment: Assignment,
        userQuestion: String = "",
        selectedMaterials: Set<String> = emptySet(),
        uploadedFileUri: android.net.Uri? = null
    ): AssignmentStudyHelpResult {
        return geminiService.getAssignmentStudyHelp(
            assignment,
            userQuestion,
            selectedMaterials,
            uploadedFileUri
        )
    }
    
    suspend fun getStudyHelp(
        assignment: String,
        description: String?,
        userQuery: String,
        context: String? = null
    ): Result<AIStudyResponse> {
        return aiStudyService.getStudyHelp(assignment, description, userQuery, context)
    }
    
    suspend fun getMockStudyHelp(assignment: String, userQuery: String): AIStudyResponse {
        return aiStudyService.getMockStudyHelp(assignment, userQuery)
    }
    
    suspend fun moderateContent(content: String): Result<ContentModerationResponse> {
        return aiStudyService.moderateContent(content)
    }
    
    suspend fun getDashboardStats(userId: String): Result<DashboardStats> {
        return try {
            val assignments = getAllAssignments().getOrNull() ?: emptyList()
            val studyTime = getTotalStudyTime(userId, 1).getOrNull() ?: 0L
            val grades = getGrades().getOrNull() ?: emptyList()
            
            val upcomingCount = assignments.count { it.dueDate != null }
            
            val allGrades = grades.flatMap { it.assignments.mapNotNull { a -> a.grade } }
            val avgGrade = if (allGrades.isNotEmpty()) allGrades.average() else 0.0
            
            val hours = studyTime / (1000 * 60 * 60)
            val minutes = (studyTime % (1000 * 60 * 60)) / (1000 * 60)
            val studyTimeFormatted = "${hours}h ${minutes}m"
            
            val stats = DashboardStats(
                assignmentsDue = upcomingCount,
                studyTimeToday = studyTimeFormatted,
                currentGPA = String.format("%.1f", avgGrade / 25),
                totalCourses = grades.size
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

data class DashboardStats(
    val assignmentsDue: Int,
    val studyTimeToday: String,
    val currentGPA: String,
    val totalCourses: Int
)
