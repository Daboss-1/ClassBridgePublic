package app.classbridgeai.ClassBridge.data.models

data class ChatRoom(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val subject: String = "General",
    val isPrivate: Boolean = false,
    val creatorId: String = "",
    val creatorName: String = "",
    val members: List<String> = emptyList(),
    val moderators: List<String> = emptyList(),
    val maxMembers: Int = 50,
    val joinCode: String? = null,
    val allowedEmails: List<String> = emptyList(),
    val requireJoinCode: Boolean = false,
    val allowFileUploads: Boolean = false,
    val moderationLevel: String = "medium",
    val createdAt: java.util.Date? = null,
    val lastMessageAt: java.util.Date? = null,
    val bannedUsers: List<String> = emptyList()
)

data class ChatMessage(
    val id: String = "",
    val roomId: String = "",
    val userId: String = "",
    val userName: String = "",
    val userPhotoUrl: String? = null,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val edited: Boolean = false,
    val deleted: Boolean = false,
    val replyTo: String? = null,
    val attachments: List<MessageAttachment> = emptyList(),
    val reactions: Map<String, List<String>> = emptyMap(),
    val moderationStatus: String? = null,
    val assignmentAttachment: AssignmentAttachment? = null
)

data class MessageAttachment(
    val type: String = "",
    val url: String = "",
    val name: String = "",
    val size: Long? = null,
    val thumbnailUrl: String? = null
)

data class AssignmentAttachment(
    val id: String = "",
    val title: String = "",
    val courseName: String = "",
    val courseId: String = "",
    val description: String? = null,
    val dueDate: String? = null,
    val maxPoints: Double? = null,
    val alternateLink: String? = null
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoURL: String? = null,
    val grade: String? = null,
    val school: String? = null,
    val bio: String? = null,
    val joinedRooms: List<String> = emptyList(),
    val createdRooms: List<String> = emptyList(),
    val favoriteRooms: List<String> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)

data class UserSettings(
    val notifications: Boolean = true,
    val emailNotifications: Boolean = false,
    val tipsEnabled: Boolean = true,
    val theme: String = "system"
)

data class StudySession(
    val id: String = "",
    val userId: String = "",
    val courseId: String? = null,
    val courseName: String? = null,
    val assignmentId: String? = null,
    val assignmentName: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val duration: Long = 0,
    val notes: String? = null,
    val productivity: Int? = null
)
