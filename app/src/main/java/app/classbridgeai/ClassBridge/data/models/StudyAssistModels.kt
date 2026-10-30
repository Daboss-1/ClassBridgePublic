package app.classbridgeai.ClassBridge.data.models

enum class UploadStatusType {
    UPLOADED,
    FAILED,
    SKIPPED
}

data class AttachmentUploadStatus(
    val label: String,
    val status: UploadStatusType,
    val detail: String? = null
)

data class AssignmentStudyHelpResult(
    val response: String,
    val attachmentStatuses: List<AttachmentUploadStatus> = emptyList()
)
