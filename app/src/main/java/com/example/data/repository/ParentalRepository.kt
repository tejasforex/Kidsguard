package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AppRule
import com.example.data.model.ChildProfile
import com.example.data.model.FamilySettings
import com.example.data.model.RewardQuest
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.example.data.remote.unwrapFirestoreException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ParentalRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDoc(userId: String) = db.collection("users").document(userId)

    private fun childrenCol(userId: String) = userDoc(userId).collection("children")

    private fun appRulesCol(userId: String, childId: String) =
        childrenCol(userId).document(childId).collection("appRules")

    private fun questsCol(userId: String, childId: String) =
        childrenCol(userId).document(childId).collection("quests")

    fun observeFamilySettings(userId: String): Flow<FamilySettings?> {
        val docRef = userDoc(userId)
        return docRef.snapshots()
            .map { snapshot ->
                if (!snapshot.exists()) null
                else snapshot.toObject(
                    FamilySettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.GET, docRef.path)
                throw error
            }
    }

    suspend fun ensureFamilySettings(userId: String, displayName: String): FamilySettings {
        val docRef = userDoc(userId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(
                    FamilySettings::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ) ?: FamilySettings(id = userId, userId = userId, displayName = displayName)
            } else {
                val safeName = displayName.trim().ifBlank { "Parent" }.take(80)
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "parentPinHash" to "",
                    "hasParentPin" to false,
                    "kidModeActive" to false,
                    "activeKidModeChildId" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(payload).await()
                FamilySettings(
                    id = userId,
                    userId = userId,
                    displayName = safeName,
                    parentPinHash = "",
                    hasParentPin = false,
                    kidModeActive = false,
                    activeKidModeChildId = "",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun setParentPin(userId: String, displayName: String, pin: String) {
        val docRef = userDoc(userId)
        val safeName = displayName.trim().ifBlank { "Parent" }.take(80)
        val pinHash = hashPin(pin)
        try {
            docRef.update(
                mapOf(
                    "displayName" to safeName,
                    "parentPinHash" to pinHash,
                    "hasParentPin" to true,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun setKidMode(userId: String, active: Boolean, childId: String) {
        val docRef = userDoc(userId)
        try {
            docRef.update(
                mapOf(
                    "kidModeActive" to active,
                    "activeKidModeChildId" to if (active) childId else "",
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeChildren(userId: String): Flow<List<ChildProfile>> {
        val col = childrenCol(userId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ChildProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.name.lowercase() }
            }
            .catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun createChildProfile(
        userId: String,
        name: String,
        age: Long,
        avatarKey: String,
        dailyLimitMinutes: Long,
        weekendLimitMinutes: Long,
        contentRating: String,
    ): String {
        val col = childrenCol(userId)
        val docRef = col.document()
        val payload = mapOf(
            "userId" to userId,
            "name" to name.trim().take(40),
            "age" to age.coerceIn(1L, 17L),
            "avatarKey" to avatarKey,
            "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(15L, 720L),
            "weekendLimitMinutes" to weekendLimitMinutes.coerceIn(15L, 720L),
            "usedMinutesToday" to 0L,
            "bonusMinutesToday" to 0L,
            "isPaused" to false,
            "bedtimeEnabled" to true,
            "bedtimeStartMinutes" to (20L * 60L + 30L),
            "bedtimeEndMinutes" to (7L * 60L),
            "studyFocusEnabled" to false,
            "studyStartMinutes" to (8L * 60L + 30L),
            "studyEndMinutes" to (15L * 60L),
            "contentRating" to contentRating,
            "safeSearchEnabled" to true,
            "blockPurchases" to true,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateChildProfileInfo(
        userId: String,
        childId: String,
        name: String,
        age: Long,
        avatarKey: String,
        dailyLimitMinutes: Long,
        weekendLimitMinutes: Long,
    ) {
        val docRef = childrenCol(userId).document(childId)
        try {
            docRef.update(
                mapOf(
                    "name" to name.trim().take(40),
                    "age" to age.coerceIn(1L, 17L),
                    "avatarKey" to avatarKey,
                    "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(15L, 720L),
                    "weekendLimitMinutes" to weekendLimitMinutes.coerceIn(15L, 720L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateChildControls(
        userId: String,
        childId: String,
        isPaused: Boolean,
        usedMinutesToday: Long,
        bonusMinutesToday: Long,
    ) {
        val docRef = childrenCol(userId).document(childId)
        try {
            docRef.update(
                mapOf(
                    "isPaused" to isPaused,
                    "usedMinutesToday" to usedMinutesToday.coerceIn(0L, 1440L),
                    "bonusMinutesToday" to bonusMinutesToday.coerceIn(0L, 360L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun updateChildScheduleAndSafety(
        userId: String,
        child: ChildProfile,
    ) {
        require(child.id.isNotBlank()) { "Child ID must not be blank" }
        val docRef = childrenCol(userId).document(child.id)
        try {
            docRef.update(
                mapOf(
                    "bedtimeEnabled" to child.bedtimeEnabled,
                    "bedtimeStartMinutes" to child.bedtimeStartMinutes.coerceIn(0L, 1439L),
                    "bedtimeEndMinutes" to child.bedtimeEndMinutes.coerceIn(0L, 1439L),
                    "studyFocusEnabled" to child.studyFocusEnabled,
                    "studyStartMinutes" to child.studyStartMinutes.coerceIn(0L, 1439L),
                    "studyEndMinutes" to child.studyEndMinutes.coerceIn(0L, 1439L),
                    "contentRating" to child.contentRating,
                    "safeSearchEnabled" to child.safeSearchEnabled,
                    "blockPurchases" to child.blockPurchases,
                    "dailyLimitMinutes" to child.dailyLimitMinutes.coerceIn(15L, 720L),
                    "weekendLimitMinutes" to child.weekendLimitMinutes.coerceIn(15L, 720L),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteChildProfile(userId: String, childId: String) {
        val docRef = childrenCol(userId).document(childId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeAppRules(userId: String, childId: String): Flow<List<AppRule>> {
        val col = appRulesCol(userId, childId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    AppRule::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedBy { it.appName.lowercase() }
            }
            .catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun upsertAppRule(
        userId: String,
        childId: String,
        existingRuleId: String?,
        packageName: String,
        appName: String,
        category: String,
        status: String,
        dailyLimitMinutes: Long,
        usedMinutesToday: Long,
    ): String {
        val col = appRulesCol(userId, childId)
        return try {
            if (!existingRuleId.isNullOrBlank()) {
                val docRef = col.document(existingRuleId)
                docRef.update(
                    mapOf(
                        "appName" to appName.trim().take(80),
                        "category" to category.trim().take(40),
                        "status" to status,
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(0L, 720L),
                        "usedMinutesToday" to usedMinutesToday.coerceIn(0L, 1440L),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                ).await()
                existingRuleId
            } else {
                val safeId = packageName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
                val docRef = col.document(safeId)
                val existingSnap = docRef.get().await()
                if (existingSnap.exists()) {
                    docRef.update(
                        mapOf(
                            "appName" to appName.trim().take(80),
                            "category" to category.trim().take(40),
                            "status" to status,
                            "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(0L, 720L),
                            "usedMinutesToday" to usedMinutesToday.coerceIn(0L, 1440L),
                            "updatedAt" to FieldValue.serverTimestamp(),
                        )
                    ).await()
                } else {
                    val payload = mapOf(
                        "userId" to userId,
                        "childId" to childId,
                        "packageName" to packageName.take(160),
                        "appName" to appName.trim().take(80),
                        "category" to category.trim().take(40),
                        "status" to status,
                        "dailyLimitMinutes" to dailyLimitMinutes.coerceIn(0L, 720L),
                        "usedMinutesToday" to usedMinutesToday.coerceIn(0L, 1440L),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    docRef.set(payload).await()
                }
                docRef.id
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, col.path)
            throw e
        }
    }

    suspend fun deleteAppRule(userId: String, childId: String, ruleId: String) {
        val docRef = appRulesCol(userId, childId).document(ruleId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    fun observeQuests(userId: String, childId: String): Flow<List<RewardQuest>> {
        val col = questsCol(userId, childId)
        return col.snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RewardQuest::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { error ->
                val fsError = error.unwrapFirestoreException()
                if (fsError == null && error is CancellationException) throw error
                handleFirestoreError(fsError ?: error, OperationType.LIST, col.path)
                throw error
            }
    }

    suspend fun createQuest(
        userId: String,
        childId: String,
        title: String,
        rewardMinutes: Long,
    ): String {
        val col = questsCol(userId, childId)
        val docRef = col.document()
        val payload = mapOf(
            "userId" to userId,
            "childId" to childId,
            "title" to title.trim().take(80),
            "rewardMinutes" to rewardMinutes.coerceIn(5L, 120L),
            "status" to RewardQuest.STATUS_OPEN,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        return try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun requestQuestApproval(userId: String, childId: String, questId: String) {
        val docRef = questsCol(userId, childId).document(questId)
        try {
            docRef.update(
                mapOf(
                    "status" to RewardQuest.STATUS_PENDING_APPROVAL,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun approveQuestAndGrantBonus(
        userId: String,
        child: ChildProfile,
        quest: RewardQuest,
    ) {
        val questRef = questsCol(userId, child.id).document(quest.id)
        val childRef = childrenCol(userId).document(child.id)
        val newBonus = (child.bonusMinutesToday + quest.rewardMinutes).coerceAtMost(360L)
        try {
            val batch = db.batch()
            batch.update(
                questRef,
                mapOf(
                    "status" to RewardQuest.STATUS_APPROVED,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.update(
                childRef,
                mapOf(
                    "isPaused" to child.isPaused,
                    "usedMinutesToday" to child.usedMinutesToday,
                    "bonusMinutesToday" to newBonus,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            )
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, questRef.path)
            throw e
        }
    }

    suspend fun declineQuestApproval(userId: String, childId: String, quest: RewardQuest) {
        val docRef = questsCol(userId, childId).document(quest.id)
        try {
            docRef.update(
                mapOf(
                    "title" to quest.title,
                    "rewardMinutes" to quest.rewardMinutes,
                    "status" to RewardQuest.STATUS_OPEN,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteQuest(userId: String, childId: String, questId: String) {
        val docRef = questsCol(userId, childId).document(questId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    companion object {
        fun hashPin(pin: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest("kidguard:$pin".toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}
