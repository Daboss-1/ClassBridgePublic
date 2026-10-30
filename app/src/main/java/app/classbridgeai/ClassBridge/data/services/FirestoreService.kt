package app.classbridgeai.ClassBridge.data.services

import android.util.Log
import app.classbridgeai.ClassBridge.data.models.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*

class FirestoreService {
    
    private val db: FirebaseFirestore
    private val auth: FirebaseAuth
    
    init {
        try {
            Log.d(TAG, "Initializing FirestoreService")
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
            Log.d(TAG, "FirestoreService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirestoreService", e)
            throw e
        }
    }
    
    companion object {
        private const val TAG = "FirestoreService"
    }
    
    suspend fun createOrUpdateUserProfile(profile: UserProfile): Result<Unit> = 
        withContext(Dispatchers.IO) {
        try {
            db.collection("users")
                .document(profile.uid)
                .set(profile)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserProfile(uid: String): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val document = db.collection("users")
                .document(uid)
                .get()
                .await()
            
            val profile = document.toObject(UserProfile::class.java)
            if (profile != null) {
                Result.success(profile)
            } else {
                Result.failure(Exception("User profile not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createChatRoom(room: ChatRoom): Result<String> = withContext(Dispatchers.IO) {
        Log.d("CHAT_DEBUG", "FirestoreService.createChatRoom: name='${room.name}' creatorId='${room.creatorId}'")
        try {
            val roomWithCode = room.copy(
                joinCode = generateJoinCode(),
                members = listOf(room.creatorId),
                moderators = listOf(room.creatorId),
                createdAt = java.util.Date(),
                lastMessageAt = java.util.Date()
            )
            Log.d("CHAT_DEBUG", "FirestoreService.createChatRoom: writing to Firestore with joinCode='${roomWithCode.joinCode}' members=${roomWithCode.members}")

            val docRef = db.collection("chatRooms")
                .add(roomWithCode)
                .await()

            Log.d("CHAT_DEBUG", "FirestoreService.createChatRoom: SUCCESS docId='${docRef.id}'")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "FirestoreService.createChatRoom: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun getChatRoom(roomId: String): Result<ChatRoom> = withContext(Dispatchers.IO) {
        try {
            val document = db.collection("chatRooms")
                .document(roomId)
                .get()
                .await()
            
            val room = document.toObject(ChatRoom::class.java)?.copy(id = document.id)
            if (room != null) {
                Result.success(room)
            } else {
                Result.failure(Exception("Chat room not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserChatRooms(userId: String): Result<List<ChatRoom>> =
        withContext(Dispatchers.IO) {
        Log.d("CHAT_DEBUG", "FirestoreService.getUserChatRooms: querying for userId='$userId'")
        try {
            val snapshot = db.collection("chatRooms")
                .whereArrayContains("members", userId)
                .get()
                .await()

            Log.d("CHAT_DEBUG", "FirestoreService.getUserChatRooms: snapshot has ${snapshot.documents.size} docs")
            snapshot.documents.forEach { doc ->
                Log.d("CHAT_DEBUG", "  doc id='${doc.id}' members=${doc.get("members")} name='${doc.getString("name")}'")
            }

            val rooms = snapshot.documents.mapNotNull { doc ->
                val room = doc.toObject(ChatRoom::class.java)?.copy(id = doc.id)
                if (room == null) Log.w("CHAT_DEBUG", "  WARNING: doc '${doc.id}' failed to deserialize")
                room
            }.sortedByDescending { it.lastMessageAt?.time ?: 0L }

            Log.d("CHAT_DEBUG", "FirestoreService.getUserChatRooms: returning ${rooms.size} deserialized rooms")
            Result.success(rooms)
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "FirestoreService.getUserChatRooms: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun joinChatRoom(roomId: String, userId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
        Log.d("CHAT_DEBUG", "FirestoreService.joinChatRoom: roomId='$roomId' userId='$userId'")
        try {
            db.collection("chatRooms")
                .document(roomId)
                .update("members", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            Log.d("CHAT_DEBUG", "FirestoreService.joinChatRoom: SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "FirestoreService.joinChatRoom: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun joinChatRoomWithCode(code: String, userId: String, userEmail: String):
        Result<String> = withContext(Dispatchers.IO) {
        Log.d("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: code='$code' userId='$userId' email='$userEmail'")
        try {
            val snapshot = db.collection("chatRooms")
                .whereEqualTo("joinCode", code.uppercase())
                .get()
                .await()

            Log.d("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: query returned ${snapshot.documents.size} docs, isEmpty=${snapshot.isEmpty}")

            if (snapshot.isEmpty) {
                Log.w("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: no room found for code='${code.uppercase()}'")
                return@withContext Result.failure(Exception("Invalid join code"))
            }

            val doc = snapshot.documents.first()
            val room = doc.toObject(ChatRoom::class.java)
            Log.d("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: found doc='${doc.id}' room=$room")

            if (room != null) {
                Log.d("CHAT_DEBUG", "  allowedEmails=${room.allowedEmails} bannedUsers=${room.bannedUsers} members=${room.members} maxMembers=${room.maxMembers}")

                if (room.allowedEmails.isNotEmpty() && !room.allowedEmails.contains(userEmail)) {
                    Log.w("CHAT_DEBUG", "  REJECTED: email not in allowedEmails")
                    return@withContext Result.failure(Exception("Your email is not authorized for this room"))
                }

                if (room.bannedUsers.contains(userId)) {
                    Log.w("CHAT_DEBUG", "  REJECTED: user is banned")
                    return@withContext Result.failure(Exception("You are banned from this room"))
                }

                if (room.members.size >= room.maxMembers) {
                    Log.w("CHAT_DEBUG", "  REJECTED: room full (${room.members.size}/${room.maxMembers})")
                    return@withContext Result.failure(Exception("Room is full"))
                }

                Log.d("CHAT_DEBUG", "  all checks passed, joining room '${doc.id}'")
                joinChatRoom(doc.id, userId).getOrThrow()
                Result.success(doc.id)
            } else {
                Log.w("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: doc deserialized to null")
                Result.failure(Exception("Room not found"))
            }
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "FirestoreService.joinChatRoomWithCode: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }
    
    suspend fun leaveChatRoom(roomId: String, userId: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
        try {
            db.collection("chatRooms")
                .document(roomId)
                .update("members", com.google.firebase.firestore.FieldValue.arrayRemove(userId))
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendMessage(message: ChatMessage): Result<String> = withContext(Dispatchers.IO) {
        Log.d("CHAT_DEBUG", "FirestoreService.sendMessage: roomId='${message.roomId}' userId='${message.userId}' text='${message.message.take(40)}'")
        try {
            val docRef = db.collection("chatRooms")
                .document(message.roomId)
                .collection("messages")
                .add(message)
                .await()

            Log.d("CHAT_DEBUG", "FirestoreService.sendMessage: message written as doc='${docRef.id}'")

            db.collection("chatRooms")
                .document(message.roomId)
                .update("lastMessageAt", java.util.Date(message.timestamp))
                .await()

            Log.d("CHAT_DEBUG", "FirestoreService.sendMessage: lastMessageAt updated")
            Result.success(docRef.id)
        } catch (e: Exception) {
            Log.e("CHAT_DEBUG", "FirestoreService.sendMessage: EXCEPTION - ${e.message}", e)
            Result.failure(e)
        }
    }

    fun observeMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        Log.d("CHAT_DEBUG", "FirestoreService.observeMessages: subscribing to roomId='$roomId'")
        val subscription = db.collection("chatRooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CHAT_DEBUG", "observeMessages: listener error - ${error.message}", error)
                    close(error)
                    return@addSnapshotListener
                }

                Log.d("CHAT_DEBUG", "observeMessages: snapshot received, ${snapshot?.documents?.size ?: 0} docs")
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("CHAT_DEBUG", "observeMessages: failed to deserialize doc '${doc.id}': ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d("CHAT_DEBUG", "observeMessages: emitting ${messages.size} messages")
                trySend(messages)
            }

        awaitClose {
            Log.d("CHAT_DEBUG", "observeMessages: unsubscribing from roomId='$roomId'")
            subscription.remove()
        }
    }
    
    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> = 
        withContext(Dispatchers.IO) {
        try {
            db.collection("chatRooms")
                .document(roomId)
                .collection("messages")
                .document(messageId)
                .update("deleted", true, "message", "[Deleted]")
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun createStudySession(session: StudySession): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            val docRef = db.collection("studySessions")
                .add(session)
                .await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun endStudySession(sessionId: String, endTime: Long): Result<Unit> = 
        withContext(Dispatchers.IO) {
        try {
            val docSnapshot = db.collection("studySessions")
                .document(sessionId)
                .get()
                .await()
            
            val session = docSnapshot.toObject(StudySession::class.java)
            if (session != null) {
                val duration = endTime - session.startTime
                db.collection("studySessions")
                    .document(sessionId)
                    .update(
                        mapOf(
                            "endTime" to endTime,
                            "duration" to duration
                        )
                    )
                    .await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Session not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getUserStudySessions(userId: String, limit: Int = 50): 
        Result<List<StudySession>> = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("studySessions")
                .whereEqualTo("userId", userId)
                .orderBy("startTime", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
            
            val sessions = snapshot.documents.mapNotNull { doc ->
                doc.toObject(StudySession::class.java)?.copy(id = doc.id)
            }
            
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateJoinCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..6)
            .map { chars.random() }
            .joinToString("")
    }
    
    suspend fun regenerateJoinCode(roomId: String): Result<String> = 
        withContext(Dispatchers.IO) {
        try {
            val newCode = generateJoinCode()
            db.collection("chatRooms")
                .document(roomId)
                .update("joinCode", newCode)
                .await()
            Result.success(newCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
