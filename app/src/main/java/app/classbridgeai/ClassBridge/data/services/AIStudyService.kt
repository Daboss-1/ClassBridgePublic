package app.classbridgeai.ClassBridge.data.services

import app.classbridgeai.ClassBridge.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

data class AIStudyRequest(
    val assignment: String,
    val description: String?,
    val userQuery: String,
    val context: String? = null
)

data class AIStudyResponse(
    val response: String,
    val suggestions: List<String>? = null,
    val resources: List<StudyResource>? = null
)

data class StudyResource(
    val title: String,
    val url: String,
    val description: String?
)

data class ContentModerationRequest(
    val content: String
)

data class ContentModerationResponse(
    val isAllowed: Boolean,
    val reason: String?,
    val categories: Map<String, Double>
)

interface AIStudyAPI {
    @POST("api/ai-study-help")
    suspend fun getStudyHelp(@Body request: AIStudyRequest): AIStudyResponse
    
    @POST("api/moderate-content")
    suspend fun moderateContent(@Body request: ContentModerationRequest): ContentModerationResponse
}

class AIStudyService {
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.AI_STUDY_BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(AIStudyAPI::class.java)
    
    suspend fun getStudyHelp(
        assignment: String,
        description: String?,
        userQuery: String,
        context: String? = null
    ): Result<AIStudyResponse> {
        return try {
            val request = AIStudyRequest(
                assignment = assignment,
                description = description,
                userQuery = userQuery,
                context = context
            )
            val response = api.getStudyHelp(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun moderateContent(content: String): Result<ContentModerationResponse> {
        return try {
            val request = ContentModerationRequest(content)
            val response = api.moderateContent(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getMockStudyHelp(
        assignment: String,
        userQuery: String
    ): AIStudyResponse {
        return AIStudyResponse(
            response = """
                Based on your question about "$assignment", here are some key points:
                
                1. Break down the problem into smaller, manageable parts
                2. Review your course materials and notes
                3. Practice similar problems to reinforce understanding
                4. Don't hesitate to ask your teacher or classmates for clarification
                
                Remember to:
                - Start early and avoid last-minute cramming
                - Take breaks to maintain focus
                - Review and test your understanding regularly
            """.trimIndent(),
            suggestions = listOf(
                "Create a study schedule",
                "Use flashcards for memorization",
                "Form a study group",
                "Practice active recall"
            ),
            resources = listOf(
                StudyResource(
                    title = "Khan Academy",
                    url = "https://www.khanacademy.org",
                    description = "Free educational resources"
                ),
                StudyResource(
                    title = "Quizlet",
                    url = "https://quizlet.com",
                    description = "Flashcards and study tools"
                )
            )
        )
    }
}
