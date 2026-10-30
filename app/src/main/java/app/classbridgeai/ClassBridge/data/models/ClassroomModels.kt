package app.classbridgeai.ClassBridge.data.models

data class Assignment(
    val id: String,
    val title: String,
    val description: String?,
    val dueDate: String?,
    val courseId: String,
    val courseName: String,
    val maxPoints: Double?,
    val state: String,
    val workType: String,
    val alternateLink: String?,
    val materials: List<Material>? = null,
    val submissionState: String? = null,
    val assignedGrade: Double? = null
)

data class Material(
    val driveFile: DriveFile? = null,
    val link: Link? = null
)

data class DriveFile(
    val id: String,
    val title: String,
    val alternateLink: String?,
    val thumbnailUrl: String?
)

data class Link(
    val url: String,
    val title: String?
)

data class Course(
    val id: String,
    val name: String,
    val section: String?,
    val descriptionHeading: String?,
    val room: String?,
    val ownerId: String,
    val courseState: String,
    val alternateLink: String?,
    val teacherFolder: DriveFolder? = null,
    val guardiansEnabled: Boolean = false,
    val calendarId: String? = null
)

data class DriveFolder(
    val id: String,
    val title: String,
    val alternateLink: String?
)

data class StudentSubmission(
    val id: String,
    val courseId: String,
    val courseWorkId: String,
    val userId: String,
    val state: String,
    val late: Boolean,
    val draftGrade: Double?,
    val assignedGrade: Double?,
    val alternateLink: String?,
    val courseWorkType: String?,
    val submissionHistory: List<SubmissionHistory>? = null
)

data class SubmissionHistory(
    val stateHistory: StateHistory? = null,
    val gradeHistory: GradeHistory? = null
)

data class StateHistory(
    val state: String,
    val stateTimestamp: String,
    val actorUserId: String
)

data class GradeHistory(
    val pointsEarned: Double,
    val maxPoints: Double,
    val gradeTimestamp: String,
    val actorUserId: String,
    val gradeChangeType: String
)

data class Teacher(
    val userId: String,
    val name: String,
    val email: String
)

data class GradeData(
    val courseId: String,
    val courseName: String,
    val overallGrade: Double?,
    val letterGrade: String?,
    val assignments: List<GradeAssignment>
)

data class GradeAssignment(
    val id: String,
    val title: String,
    val dueDate: String?,
    val maxPoints: Double?,
    val earnedPoints: Double?,
    val grade: Double?,
    val state: String,
    val late: Boolean
)
