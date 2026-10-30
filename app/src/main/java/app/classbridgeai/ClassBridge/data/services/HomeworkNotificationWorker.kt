package app.classbridgeai.ClassBridge.data.services

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.classbridgeai.ClassBridge.R
import app.classbridgeai.ClassBridge.data.models.Assignment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeworkNotificationWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val assignments = AssignmentCache.load(context)
        val dueAssignments = getAssignmentsForNext2Days(assignments)

        if (dueAssignments.isEmpty()) return Result.success()

        val style = NotificationCompat.InboxStyle()
            .setBigContentTitle("Homework Due Soon")
            .setSummaryText("${dueAssignments.size} assignment(s) in the next 2 days")

        dueAssignments.forEach { assignment ->
            style.addLine("${assignment.title}  —  ${assignment.courseName}")
        }

        val contentText = when (dueAssignments.size) {
            1 -> "${dueAssignments[0].title} — ${dueAssignments[0].courseName}"
            else -> "${dueAssignments.size} assignments due in the next 2 days"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Homework Due Soon")
            .setContentText(contentText)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)

        return Result.success()
    }

    private fun getAssignmentsForNext2Days(assignments: List<Assignment>): List<Assignment> {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayAfter = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 2) }

        return assignments
            .filter { it.state != "TURNED_IN" && it.dueDate != null }
            .filter { assignment ->
                val parsed = try {
                    format.parse(assignment.dueDate!!)
                } catch (e: Exception) {
                    null
                } ?: return@filter false
                val due = Calendar.getInstance().apply { time = parsed }
                !due.before(today) && due.before(dayAfter)
            }
    }

    companion object {
        const val CHANNEL_ID = "homework_reminders"
        const val CHANNEL_NAME = "Homework Reminders"
        const val NOTIFICATION_ID = 1001
    }
}
