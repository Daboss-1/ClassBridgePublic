package com.example.ClassBridge.data.services

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import app.classbridgeai.ClassBridge.BuildConfig
import app.classbridgeai.ClassBridge.data.models.Assignment
import app.classbridgeai.ClassBridge.data.models.AssignmentStudyHelpResult
import app.classbridgeai.ClassBridge.data.models.AttachmentUploadStatus
import app.classbridgeai.ClassBridge.data.models.UploadStatusType
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

import app.classbridgeai.ClassBridge.data.models.GradeData

interface ClassroomDataProvider {
    suspend fun getCourses(): Result<List<app.classbridgeai.ClassBridge.data.models.Course>>
    suspend fun getAllAssignments(): Result<List<Assignment>>
    suspend fun getGrades(): Result<List<GradeData>>
}

class GeminiService(
    private val context: Context,
    private val classroomDataProvider: ClassroomDataProvider? = null
) {

    private companion object {
        private const val PRIMARY_MODEL = "gemini-3.5-flash-lite"
        private const val FALLBACK_VISION_MODEL = "gemini-3.5-flash"
        private const val MAX_INLINE_IMAGE_BYTES = 4 * 1024 * 1024
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private data class DownloadedDriveFile(
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    private data class DriveFileMetadata(
        val id: String,
        val name: String,
        val mimeType: String
    )

    private data class GeminiFilePart(
        val fileUri: String,
        val mimeType: String
    )

    private data class GeminiInlineImagePart(
        val mimeType: String,
        val base64Data: String
    )

    private data class PreparedAttachmentParts(
        val fileParts: List<GeminiFilePart> = emptyList(),
        val inlineImageParts: List<GeminiInlineImagePart> = emptyList()
    )

    private data class PreparedImage(
        val bytes: ByteArray,
        val mimeType: String,
        val transformed: Boolean = false
    )

    suspend fun getSimpleStudyHelp(
        question: String,
        fileUri: Uri? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            var contextStr = ""
            if (classroomDataProvider != null) {
                val q = question.lowercase(Locale.US)
                val needsClassroomData = q.contains("grade") || q.contains("gpa") || 
                                       q.contains("assignment") || q.contains("homework") || 
                                       q.contains("due") || q.contains("class") || 
                                       q.contains("course")
                
                if (needsClassroomData) {
                    try {
                        val courses = classroomDataProvider.getCourses().getOrNull() ?: emptyList()
                        contextStr += "User's Google Classroom Courses:\n"
                        if (courses.isEmpty()) contextStr += "None found.\n"
                        courses.forEach { course ->
                            contextStr += "- ${course.name} (ID: ${course.id})\n"
                        }
                        contextStr += "\n"
                        
                        val grades = classroomDataProvider.getGrades().getOrNull() ?: emptyList()
                        contextStr += "User's Google Classroom Grades:\n"
                        if (grades.isEmpty()) contextStr += "None found.\n"
                        grades.forEach { grade ->
                            contextStr += "- ${grade.courseName}: ${grade.letterGrade ?: "N/A"} (${grade.overallGrade ?: "N/A"}%)\n"
                        }
                        contextStr += "\n"
                        
                        val assignments = classroomDataProvider.getAllAssignments().getOrNull() ?: emptyList()
                        contextStr += "User's Google Classroom Recent Assignments:\n"
                        if (assignments.isEmpty()) contextStr += "None found.\n"
                        assignments.sortedByDescending { it.dueDate ?: "" }.take(15).forEach { assignment ->
                            contextStr += "- [${assignment.courseName}] ${assignment.title} (Due: ${assignment.dueDate ?: "No due date"}, Status: ${assignment.state})\n"
                        }
                        contextStr += "\n"
                    } catch (e: Exception) {
                        contextStr += "NOTE TO AI: Could not connect to Google Classroom to retrieve the user's data. Inform the user that you are unable to connect to Google Classroom right now.\n\n"
                    }
                }
            }

            val finalQuestion = if (contextStr.isNotBlank()) {
                "SYSTEM CONTEXT (From Google Classroom):\n$contextStr\n\nUSER QUESTION:\n$question"
            } else {
                question
            }
            
            val guidedPrompt = buildGuidedStudyPrompt(finalQuestion)

            if (fileUri == null) {
                return@withContext requestGenerateContent(PRIMARY_MODEL, guidedPrompt, emptyList(), emptyList())
            }

            val fileName = getFileName(fileUri)
            val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"
            val bytes = readUriBytes(fileUri)
                ?: return@withContext requestGenerateContent(PRIMARY_MODEL, guidedPrompt, emptyList(), emptyList())

            val prepared = prepareAttachmentParts(fileName, mimeType, bytes)
            if (prepared.fileParts.isEmpty() && prepared.inlineImageParts.isEmpty()) {
                return@withContext requestGenerateContent(PRIMARY_MODEL, guidedPrompt, emptyList(), emptyList())
            }

            generateContentWithAttachments(
                prompt = guidedPrompt,
                fileParts = prepared.fileParts,
                inlineImageParts = prepared.inlineImageParts
            ) ?: requestGenerateContent(PRIMARY_MODEL, guidedPrompt, emptyList(), emptyList())
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getAssignmentStudyHelp(
        assignment: Assignment,
        userQuestion: String = "",
        selectedMaterials: Set<String> = emptySet(),
        uploadedFileUri: Uri? = null
    ): AssignmentStudyHelpResult = withContext(Dispatchers.IO) {
        val statuses = mutableListOf<AttachmentUploadStatus>()

        try {
            val prompt = buildPrompt(assignment, userQuestion, selectedMaterials)
            val geminiFiles = mutableListOf<GeminiFilePart>()
            val inlineImageParts = mutableListOf<GeminiInlineImagePart>()

            if (uploadedFileUri != null) {
                val label = getFileName(uploadedFileUri)
                val mimeType = context.contentResolver.getType(uploadedFileUri) ?: "application/octet-stream"
                val bytes = readUriBytes(uploadedFileUri)

                if (bytes == null) {
                    statuses += AttachmentUploadStatus(label, UploadStatusType.FAILED, "Failed to read file from device")
                } else {
                    val prepared = prepareAttachmentParts(label, mimeType, bytes)
                    geminiFiles += prepared.fileParts
                    inlineImageParts += prepared.inlineImageParts

                    statuses += when {
                        prepared.inlineImageParts.isNotEmpty() ->
                            AttachmentUploadStatus(label, UploadStatusType.UPLOADED, "Attached as inline image bytes")
                        prepared.fileParts.isNotEmpty() ->
                            AttachmentUploadStatus(label, UploadStatusType.UPLOADED, "Uploaded from device")
                        else ->
                            AttachmentUploadStatus(label, UploadStatusType.FAILED, "Failed to attach file")
                    }
                }
            }

            val selected = assignment.materials
                ?.filter {
                    val id = it.driveFile?.id ?: it.link?.url ?: ""
                    id in selectedMaterials
                }
                .orEmpty()

            val driveToken = if (selected.isNotEmpty()) getDriveAuthToken() else null

            selected.forEach { material ->
                val drive = material.driveFile
                if (drive == null) {
                    val label = material.link?.title ?: material.link?.url ?: "Link material"
                    statuses += AttachmentUploadStatus(label, UploadStatusType.SKIPPED, "Link-only material, added as context")
                    return@forEach
                }

                if (driveToken.isNullOrBlank()) {
                    statuses += AttachmentUploadStatus(drive.title, UploadStatusType.FAILED, "Drive token unavailable")
                    return@forEach
                }

                val downloaded = downloadDriveFileForGemini(drive.id, driveToken)
                if (downloaded == null) {
                    statuses += AttachmentUploadStatus(drive.title, UploadStatusType.FAILED, "Failed to download from Google Drive")
                    return@forEach
                }

                val prepared = prepareAttachmentParts(downloaded.fileName, downloaded.mimeType, downloaded.bytes)
                geminiFiles += prepared.fileParts
                inlineImageParts += prepared.inlineImageParts

                statuses += when {
                    prepared.inlineImageParts.isNotEmpty() ->
                        AttachmentUploadStatus(drive.title, UploadStatusType.UPLOADED, "Downloaded from Drive and attached as inline image")
                    prepared.fileParts.isNotEmpty() ->
                        AttachmentUploadStatus(drive.title, UploadStatusType.UPLOADED, "Downloaded and uploaded from Google Drive")
                    else ->
                        AttachmentUploadStatus(drive.title, UploadStatusType.FAILED, "Uploaded but Gemini processing failed")
                }
            }

            val responseText = if (geminiFiles.isNotEmpty() || inlineImageParts.isNotEmpty()) {
                generateContentWithAttachments(
                    prompt = prompt,
                    fileParts = geminiFiles,
                    inlineImageParts = inlineImageParts
                )
            } else {
                null
            } ?: requestGenerateContent(PRIMARY_MODEL, prompt, emptyList(), emptyList()) ?: generateFallbackResponse(assignment)

            AssignmentStudyHelpResult(response = responseText, attachmentStatuses = statuses)
        } catch (e: Exception) {
            if (statuses.isEmpty()) {
                statuses += AttachmentUploadStatus("Attachment processing", UploadStatusType.FAILED, "No files were uploaded")
            }
            AssignmentStudyHelpResult(
                response = generateFallbackResponse(assignment),
                attachmentStatuses = statuses
            )
        }
    }

    private fun getDriveAuthToken(): String? {
        return try {
            val account = GoogleSignIn.getLastSignedInAccount(context)?.account ?: return null
            GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive.readonly")
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchDriveFileMetadata(fileId: String, token: String): DriveFileMetadata? {
        return try {
            val fields = URLEncoder.encode("id,name,mimeType", "UTF-8")
            val request = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=$fields")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return null
                if (!response.isSuccessful) return null
                val json = JSONObject(body)
                DriveFileMetadata(
                    id = json.optString("id", fileId),
                    name = json.optString("name", "drive_file_$fileId"),
                    mimeType = json.optString("mimeType", "application/octet-stream")
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun downloadDriveFileForGemini(fileId: String, token: String): DownloadedDriveFile? {
        val metadata = fetchDriveFileMetadata(fileId, token) ?: return null

        val (url, mimeType, fileName) = when (metadata.mimeType) {
            "application/vnd.google-apps.document" -> Triple(
                "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=${URLEncoder.encode("text/plain", "UTF-8")}",
                "text/plain",
                ensureExtension(metadata.name, "txt")
            )
            "application/vnd.google-apps.spreadsheet" -> Triple(
                "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=${URLEncoder.encode("text/csv", "UTF-8")}",
                "text/csv",
                ensureExtension(metadata.name, "csv")
            )
            "application/vnd.google-apps.presentation" -> Triple(
                "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=${URLEncoder.encode("text/plain", "UTF-8")}",
                "text/plain",
                ensureExtension(metadata.name, "txt")
            )
            else -> Triple(
                "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
                metadata.mimeType,
                metadata.name
            )
        }

        return try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bytes = response.body?.bytes() ?: return null
                if (!response.isSuccessful) return null
                DownloadedDriveFile(fileName = fileName, mimeType = mimeType, bytes = bytes)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureExtension(fileName: String, extension: String): String {
        val normalized = fileName.trim()
        return if (normalized.lowercase().endsWith(".$extension")) normalized else "$normalized.$extension"
    }

    private fun readUriBytes(uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun prepareAttachmentParts(
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): PreparedAttachmentParts {
        val normalizedName = fileName.ifBlank { "uploaded_file" }
        val normalizedMime = normalizeMimeType(fileName = normalizedName, mimeType = mimeType)

        if (normalizedMime.startsWith("image/")) {
            val preparedImage = prepareImageBytes(normalizedName, normalizedMime, bytes)

            if (preparedImage != null && preparedImage.bytes.size <= MAX_INLINE_IMAGE_BYTES) {
                return PreparedAttachmentParts(
                    inlineImageParts = listOf(
                        GeminiInlineImagePart(
                            mimeType = preparedImage.mimeType,
                            base64Data = Base64.encodeToString(preparedImage.bytes, Base64.NO_WRAP)
                        )
                    )
                )
            }
        }

        val uploaded = uploadBytesToGemini(normalizedName, normalizedMime, bytes)
        return if (uploaded != null && waitForFileProcessing(uploaded)) {
            PreparedAttachmentParts(fileParts = listOf(GeminiFilePart(uploaded, normalizedMime)))
        } else {
            PreparedAttachmentParts()
        }
    }

    private suspend fun uploadBytesToGemini(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        try {
            val escapedName = fileName.replace("\\", "\\\\").replace("\"", "\\\"")
            val boundary = "gemini_${System.currentTimeMillis()}"
            val nl = "\r\n"
            val metaJson = """{"file":{"display_name":"$escapedName"}}"""
            val header = "--$boundary${nl}Content-Type: application/json; charset=UTF-8${nl}${nl}$metaJson${nl}--$boundary${nl}Content-Type: $mimeType${nl}${nl}"
            val footer = "${nl}--$boundary--"
            val bodyBytes = header.toByteArray(Charsets.UTF_8) + fileBytes + footer.toByteArray(Charsets.UTF_8)

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/upload/v1beta/files?key=${BuildConfig.GEMINI_API_KEY}")
                .post(bodyBytes.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
                .addHeader("X-Goog-Upload-Protocol", "multipart")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                if (!response.isSuccessful) return@use null
                JSONObject(body).getJSONObject("file").getString("uri")
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun waitForFileProcessing(fileUri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            repeat(10) {
                delay(2000)
                val request = Request.Builder()
                    .url("$fileUri?key=${BuildConfig.GEMINI_API_KEY}")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: return@use
                    if (!response.isSuccessful) return@use
                    val state = JSONObject(body).optString("state")
                    when (state) {
                        "ACTIVE" -> return@withContext true
                        "FAILED" -> return@withContext false
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun generateContentWithAttachments(
        prompt: String,
        fileParts: List<GeminiFilePart>,
        inlineImageParts: List<GeminiInlineImagePart>
    ): String? = withContext(Dispatchers.IO) {
        val primaryResponse = requestGenerateContent(
            modelName = PRIMARY_MODEL,
            prompt = prompt,
            fileParts = fileParts,
            inlineImageParts = inlineImageParts
        )

        if (!looksLikeFileAccessFailure(primaryResponse)) {
            return@withContext primaryResponse
        }

        requestGenerateContent(
            modelName = FALLBACK_VISION_MODEL,
            prompt = prompt,
            fileParts = fileParts,
            inlineImageParts = inlineImageParts
        )
    }

    private suspend fun requestGenerateContent(
        modelName: String,
        prompt: String,
        fileParts: List<GeminiFilePart>,
        inlineImageParts: List<GeminiInlineImagePart>
    ): String? = withContext(Dispatchers.IO) {
        try {
            val requestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                            fileParts.forEach { part ->
                                put(JSONObject().apply {
                                    put("fileData", JSONObject().apply {
                                        put("mimeType", part.mimeType)
                                        put("fileUri", part.fileUri)
                                    })
                                })
                            }
                            inlineImageParts.forEach { part ->
                                put(JSONObject().apply {
                                    put("inlineData", JSONObject().apply {
                                        put("mimeType", part.mimeType)
                                        put("data", part.base64Data)
                                    })
                                })
                            }
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("topK", 40)
                    put("topP", 0.95)
                    put("maxOutputTokens", 8192)
                })
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@use null
                if (!response.isSuccessful) return@use null
                val json = JSONObject(body)
                val candidates = json.optJSONArray("candidates") ?: return@use null
                if (candidates.length() == 0) return@use null
                val content = candidates.getJSONObject(0).optJSONObject("content") ?: return@use null
                val parts = content.optJSONArray("parts") ?: return@use null
                if (parts.length() == 0) return@use null
                val text = parts.getJSONObject(0).optString("text", "")
                return@use text.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeMimeType(fileName: String, mimeType: String): String {
        if (mimeType.isNotBlank() && mimeType != "application/octet-stream") {
            return mimeType
        }

        return when {
            fileName.lowercase(Locale.US).endsWith(".webp") -> "image/webp"
            fileName.lowercase(Locale.US).endsWith(".png") -> "image/png"
            fileName.lowercase(Locale.US).endsWith(".jpg") || fileName.lowercase(Locale.US).endsWith(".jpeg") -> "image/jpeg"
            else -> "application/octet-stream"
        }
    }

    private fun prepareImageBytes(fileName: String, mimeType: String, bytes: ByteArray): PreparedImage? {
        if (mimeType != "image/webp") {
            return PreparedImage(bytes = bytes, mimeType = mimeType, transformed = false)
        }

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return try {
            val out = java.io.ByteArrayOutputStream()
            val compressed = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            if (!compressed) return null
            PreparedImage(
                bytes = out.toByteArray(),
                mimeType = "image/png",
                transformed = true
            )
        } catch (_: Exception) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun looksLikeFileAccessFailure(responseText: String?): Boolean {
        if (responseText.isNullOrBlank()) return true

        val lowered = responseText.lowercase(Locale.US)
        val failureSignals = listOf(
            "unable to access",
            "cannot access",
            "can't access",
            "unable to read",
            "cannot read",
            "can't read",
            "text-based information",
            "cannot interpret image",
            "can't interpret image",
            "provided .webp",
            "file could not be read"
        )
        return failureSignals.any { lowered.contains(it) }
    }

    private fun getFileName(uri: Uri): String {
        var fileName = "uploaded_file"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                fileName = cursor.getString(nameIndex)
            }
        }
        return fileName
    }

    private fun buildPrompt(
        assignment: Assignment,
        userQuestion: String,
        selectedMaterials: Set<String>
    ): String {
        return buildString {
            appendLine("You are an educational AI assistant helping a student with their assignment. Provide structured, detailed study help.")
            appendLine("Do NOT provide the direct final answer when the student asks for the exact answer.")
            appendLine("Instead, guide the student with hints, step-by-step reasoning, and a short self-check.")
            appendLine("If needed, ask one clarifying question before proceeding.")
            appendLine()
            appendLine("ASSIGNMENT DETAILS:")
            appendLine("Title: ${assignment.title}")
            appendLine("Course: ${assignment.courseName}")
            assignment.description?.let { appendLine("Description: $it") }
            assignment.dueDate?.let { appendLine("Due Date: $it") }
            assignment.maxPoints?.let { appendLine("Maximum Points: $it") }

            if (selectedMaterials.isNotEmpty() && !assignment.materials.isNullOrEmpty()) {
                appendLine()
                appendLine("ASSIGNMENT MATERIALS:")
                assignment.materials.filter { material ->
                    val materialId = material.driveFile?.id ?: material.link?.url ?: ""
                    materialId in selectedMaterials
                }.forEach { material ->
                    val title = material.driveFile?.title ?: material.link?.title ?: "Material"
                    val reference = material.driveFile?.alternateLink ?: material.link?.url ?: material.driveFile?.id ?: ""
                    if (reference.isNotBlank()) appendLine("- $title ($reference)") else appendLine("- $title")
                }
            }

            if (userQuestion.isNotBlank()) {
                appendLine()
                appendLine("STUDENT'S QUESTION:")
                appendLine(userQuestion)
            }

            appendLine()
            appendLine("FILE INTERPRETATION RULES:")
            appendLine("- If attachment content is available, answer based on what is actually visible/readable in the file.")
            appendLine("- Do not guess or invent file contents.")
            appendLine("- If files are missing/unreadable, explicitly say that the file content could not be read.")

            appendLine()
            appendLine("TUTORING STYLE RULES:")
            appendLine("- Prioritize guidance over direct answers.")
            appendLine("- Use progressive hints: Concept hint -> Setup hint -> Next-step hint.")
            appendLine("- For math/science/code problems, show method and reasoning, not a copyable final answer.")
            appendLine("- End with a quick check question the student should answer.")

            appendLine()
            appendLine("Return ONLY valid JSON with this structure:")
            appendLine("""
{
  "sections": [
    {"id": "overview", "title": "Assignment Overview", "content": "..."},
    {"id": "key-concepts", "title": "Key Concepts", "content": "..."},
    {"id": "answer-to-question", "title": "Answer to the Question", "content": "..."},
    {"id": "example-solution", "title": "Example Approach", "content": "..."},
    {"id": "common-mistakes", "title": "Common Mistakes to Avoid", "content": "..."},
    {"id": "approach", "title": "Getting Started", "content": "..."}
  ]
}
            """.trimIndent())
        }
    }

    private fun buildGuidedStudyPrompt(question: String): String {
        return buildString {
            appendLine("You are an AI study assistant integrated with Google Classroom.")
            appendLine("If the user asks a general study question or for help with homework, act as a tutor:")
            appendLine("- Help the student learn instead of giving the final answer directly.")
            appendLine("- Use this response format: 1) Short explanation of concept 2) Step-by-step guidance 3) One hint for the next step 4) One quick self-check question")
            appendLine("HOWEVER, if the user asks about their Google Classroom data (e.g., grades, assignments, courses) based on the SYSTEM CONTEXT provided below:")
            appendLine("- Provide the direct answer clearly and concisely.")
            appendLine("- Do NOT act like a tutor in this case.")
            appendLine("- If the requested data is not in the context, inform the user you don't have that information.")
            appendLine()
            appendLine("STUDENT QUESTION (MAY INCLUDE SYSTEM CONTEXT):")
            appendLine(question)
        }
    }

    private fun generateFallbackResponse(assignment: Assignment): String {
        return """
            {
              "sections": [
                {
                  "id": "overview",
                  "title": "Assignment Overview",
                  "content": "This assignment **${assignment.title}** is part of your ${assignment.courseName} course."
                },
                {
                  "id": "key-concepts",
                  "title": "Key Concepts",
                  "content": "• Understand the requirements\\n• Break the work into steps\\n• Use your course materials"
                },
                {
                  "id": "example-solution",
                  "title": "Example Approach",
                  "content": "1. Read instructions\\n2. Outline your work\\n3. Solve section-by-section"
                },
                {
                  "id": "common-mistakes",
                  "title": "Common Mistakes to Avoid",
                  "content": "• Skipping instructions\\n• Starting too late\\n• Not reviewing your answers"
                },
                {
                  "id": "approach",
                  "title": "Getting Started",
                  "content": "Start with the easiest part first, then build momentum."
                }
              ]
            }
        """.trimIndent()
    }
}
