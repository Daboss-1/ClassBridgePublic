package app.classbridgeai.ClassBridge.data.services

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import app.classbridgeai.ClassBridge.BuildConfig
import app.classbridgeai.ClassBridge.data.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.*

interface ClassroomAPI {
    @GET("v1/courses")
    suspend fun getCourses(
        @Query("courseStates") courseStates: String = "ACTIVE",
        @Query("pageSize") pageSize: Int = 100
    ): CoursesResponse
    
    @GET("v1/courses/{courseId}/courseWork")
    suspend fun getCourseWork(
        @Path("courseId") courseId: String,
        @Query("pageSize") pageSize: Int = 100
    ): CourseWorkResponse
    
    @GET("v1/courses/{courseId}/courseWork/{courseWorkId}/studentSubmissions")
    suspend fun getStudentSubmissions(
        @Path("courseId") courseId: String,
        @Path("courseWorkId") courseWorkId: String
    ): SubmissionsResponse

    @GET("v1/courses/{courseId}/teachers")
    suspend fun getCourseTeachers(
        @Path("courseId") courseId: String,
        @Query("pageSize") pageSize: Int = 100
    ): TeachersResponse

    @GET("v1/userProfiles/{userId}")
    suspend fun getUserProfile(
        @Path("userId") userId: String
    ): UserProfileDTO
}

data class CoursesResponse(val courses: List<CourseDTO>?)
data class CourseWorkResponse(val courseWork: List<CourseWorkDTO>?)
data class SubmissionsResponse(val studentSubmissions: List<StudentSubmissionDTO>?)
data class TeachersResponse(val teachers: List<TeacherDTO>?)

data class TeacherDTO(val userId: String = "", val profile: TeacherProfileDTO? = null)
data class TeacherProfileDTO(val name: TeacherNameDTO? = null, val emailAddress: String? = null)
data class TeacherNameDTO(val fullName: String? = null)
data class UserProfileDTO(
    val id: String? = null,
    val name: TeacherNameDTO? = null,
    val emailAddress: String? = null
)

data class CourseDTO(
    val id: String,
    val name: String,
    val section: String?,
    val descriptionHeading: String?,
    val room: String?,
    val ownerId: String,
    val courseState: String,
    val alternateLink: String?,
    val guardiansEnabled: Boolean?,
    val calendarId: String?
)

data class CourseWorkDTO(
    val id: String,
    val courseId: String,
    val title: String,
    val description: String?,
    val state: String,
    val alternateLink: String?,
    val maxPoints: Double?,
    val workType: String,
    val dueDate: DueDateDTO?,
    val materials: List<MaterialDTO>?
)

data class DueDateDTO(
    val year: Int,
    val month: Int,
    val day: Int
)

data class MaterialDTO(
    val driveFile: DriveFileDTO?,
    val link: LinkDTO?
)

data class DriveFileDTO(
    val driveFile: DriveFileInfoDTO
)

data class DriveFileInfoDTO(
    val id: String,
    val title: String,
    val alternateLink: String?,
    val thumbnailUrl: String?
)

data class LinkDTO(
    val url: String,
    val title: String?
)

data class StudentSubmissionDTO(
    val id: String,
    val courseId: String,
    val courseWorkId: String,
    val userId: String,
    val state: String,
    val late: Boolean?,
    val draftGrade: Double?,
    val assignedGrade: Double?,
    val alternateLink: String?,
    val courseWorkType: String?
)

class GoogleClassroomService(
    private val context: Context,
    private val onConsentRequired: ((android.content.Intent) -> Unit)? = null
) {

    private fun getAuthToken(): String? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && account.account != null) {
                val scopes = "oauth2:https://www.googleapis.com/auth/classroom.courses.readonly " +
                           "https://www.googleapis.com/auth/classroom.coursework.me.readonly " +
                           "https://www.googleapis.com/auth/classroom.student-submissions.me.readonly " +
                           "https://www.googleapis.com/auth/classroom.rosters.readonly " +
                           "https://www.googleapis.com/auth/classroom.profile.emails"
                com.google.android.gms.auth.GoogleAuthUtil.getToken(context, account.account!!, scopes)
            } else {
                android.util.Log.w("GoogleClassroomService", "No signed in account found")
                null
            }
        } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
            android.util.Log.w("GoogleClassroomService", "Consent required for Classroom scopes")
            e.intent?.let { onConsentRequired?.invoke(it) }
            null
        } catch (e: Exception) {
            android.util.Log.e("GoogleClassroomService", "Error getting auth token", e)
            null
        }
    }
    
    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val newRequest = try {
            val token = getAuthToken()
            if (token != null) {
                request.newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                android.util.Log.w("GoogleClassroomService", "No auth token available")
                request
            }
        } catch (e: Exception) {
            android.util.Log.e("GoogleClassroomService", "Error in auth interceptor", e)
            request
        }
        chain.proceed(newRequest)
    }
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BASIC
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .build()
    
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://classroom.googleapis.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val api = retrofit.create(ClassroomAPI::class.java)
    
    suspend fun getCourses(): Result<List<Course>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getCourses()
            
            val courses = response.courses?.map { course ->
                Course(
                    id = course.id,
                    name = course.name,
                    section = course.section,
                    descriptionHeading = course.descriptionHeading,
                    room = course.room,
                    ownerId = course.ownerId,
                    courseState = course.courseState,
                    alternateLink = course.alternateLink,
                    teacherFolder = null,
                    guardiansEnabled = course.guardiansEnabled ?: false,
                    calendarId = course.calendarId
                )
            } ?: emptyList()
            
            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getCourseWork(courseId: String): Result<List<Assignment>> = 
        withContext(Dispatchers.IO) {
        try {
            val courses = getCourses().getOrNull() ?: emptyList()
            val courseName = courses.find { it.id == courseId }?.name ?: ""
            
            val response = api.getCourseWork(courseId)
            
            val assignments = response.courseWork?.map { work ->
                Assignment(
                    id = work.id,
                    title = work.title,
                    description = work.description,
                    dueDate = work.dueDate?.let { date ->
                        formatDate(date.year, date.month, date.day)
                    },
                    courseId = courseId,
                    courseName = courseName,
                    maxPoints = work.maxPoints,
                    state = work.state,
                    workType = work.workType,
                    alternateLink = work.alternateLink,
                    materials = work.materials?.mapNotNull { material ->
                        Material(
                            driveFile = material.driveFile?.let {
                                DriveFile(
                                    id = it.driveFile.id,
                                    title = it.driveFile.title,
                                    alternateLink = it.driveFile.alternateLink,
                                    thumbnailUrl = it.driveFile.thumbnailUrl
                                )
                            },
                            link = material.link?.let {
                                Link(url = it.url, title = it.title)
                            }
                        )
                    }
                )
            } ?: emptyList()
            
            Result.success(assignments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getAllAssignments(): Result<List<Assignment>> = withContext(Dispatchers.IO) {
        try {
            val courses = getCourses().getOrNull() ?: return@withContext Result.failure(
                Exception("Failed to get courses")
            )
            
            val allAssignments = mutableListOf<Assignment>()
            
            courses.forEach { course ->
                val assignments = getCourseWork(course.id).getOrNull()
                if (assignments != null) {
                    allAssignments.addAll(assignments)
                }
            }
            
            Result.success(allAssignments)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getStudentSubmissions(courseId: String, courseWorkId: String): 
        Result<List<StudentSubmission>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getStudentSubmissions(courseId, courseWorkId)
            
            val submissions = response.studentSubmissions?.map { submission ->
                StudentSubmission(
                    id = submission.id,
                    courseId = submission.courseId,
                    courseWorkId = submission.courseWorkId,
                    userId = submission.userId,
                    state = submission.state,
                    late = submission.late ?: false,
                    draftGrade = submission.draftGrade,
                    assignedGrade = submission.assignedGrade,
                    alternateLink = submission.alternateLink,
                    courseWorkType = submission.courseWorkType
                )
            } ?: emptyList()
            
            Result.success(submissions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getTeachersForCourse(courseId: String): Result<List<Teacher>> =
        withContext(Dispatchers.IO) {
        try {
            val response = api.getCourseTeachers(courseId)
            val teachers = response.teachers?.map { dto ->
                val profile = try {
                    api.getUserProfile(dto.userId)
                } catch (e: Exception) {
                    null
                }

                val email = dto.profile?.emailAddress ?: run {
                    profile?.emailAddress
                } ?: ""

                val displayName = dto.profile?.name?.fullName
                    ?: profile?.name?.fullName
                    ?: if (email.isNotBlank()) email else "Teacher"

                Teacher(
                    userId = dto.userId,
                    name = displayName,
                    email = email
                )
            } ?: emptyList()

            val hiddenEmailCount = teachers.count { it.email.isBlank() }
            if (hiddenEmailCount > 0) {
                android.util.Log.w(
                    "GoogleClassroomService",
                    "Teacher emails hidden for $hiddenEmailCount teacher(s) in course $courseId"
                )
            }

            Result.success(teachers)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getGrades(): Result<List<GradeData>> = withContext(Dispatchers.IO) {
        try {
            val courses = getCourses().getOrNull() ?: return@withContext Result.failure(
                Exception("Failed to get courses")
            )
            
            val gradesData = mutableListOf<GradeData>()
            
            courses.forEach { course ->
                val assignments = getCourseWork(course.id).getOrNull() ?: emptyList()
                val gradeAssignments = mutableListOf<GradeAssignment>()
                
                assignments.forEach { assignment ->
                    val submissions = getStudentSubmissions(course.id, assignment.id).getOrNull()
                    val userSubmission = submissions?.firstOrNull()
                    
                    gradeAssignments.add(
                        GradeAssignment(
                            id = assignment.id,
                            title = assignment.title,
                            dueDate = assignment.dueDate,
                            maxPoints = assignment.maxPoints,
                            earnedPoints = userSubmission?.assignedGrade,
                            grade = userSubmission?.assignedGrade?.let { earned ->
                                assignment.maxPoints?.let { max ->
                                    if (max > 0) (earned / max) * 100 else null
                                }
                            },
                            state = userSubmission?.state ?: "NOT_SUBMITTED",
                            late = userSubmission?.late ?: false
                        )
                    )
                }
                
                val gradedAssignments = gradeAssignments.filter { 
                    it.earnedPoints != null && it.maxPoints != null 
                }
                val overallGrade = if (gradedAssignments.isNotEmpty()) {
                    val totalEarned = gradedAssignments.sumOf { it.earnedPoints ?: 0.0 }
                    val totalMax = gradedAssignments.sumOf { it.maxPoints ?: 0.0 }
                    if (totalMax > 0) (totalEarned / totalMax) * 100 else null
                } else null
                
                gradesData.add(
                    GradeData(
                        courseId = course.id,
                        courseName = course.name,
                        overallGrade = overallGrade,
                        letterGrade = overallGrade?.let { calculateLetterGrade(it) },
                        assignments = gradeAssignments
                    )
                )
            }
            
            Result.success(gradesData)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun formatDate(year: Int, month: Int, day: Int): String {
        val calendar = Calendar.getInstance().apply {
            set(year, month - 1, day)
        }
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return format.format(calendar.time)
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
}
