package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class FamilySettings(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val parentPinHash: String = "",
    val hasParentPin: Boolean = false,
    val kidModeActive: Boolean = false,
    val activeKidModeChildId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ChildProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val age: Long = 8L,
    val avatarKey: String = "fox",
    val dailyLimitMinutes: Long = 120L,
    val weekendLimitMinutes: Long = 180L,
    val usedMinutesToday: Long = 0L,
    val bonusMinutesToday: Long = 0L,
    val isPaused: Boolean = false,
    val bedtimeEnabled: Boolean = true,
    val bedtimeStartMinutes: Long = 20L * 60L + 30L,
    val bedtimeEndMinutes: Long = 7L * 60L,
    val studyFocusEnabled: Boolean = false,
    val studyStartMinutes: Long = 8L * 60L + 30L,
    val studyEndMinutes: Long = 15L * 60L,
    val contentRating: String = "preteen_8_12",
    val safeSearchEnabled: Boolean = true,
    val blockPurchases: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    fun totalAllowanceMinutes(isWeekend: Boolean): Long {
        val base = if (isWeekend) weekendLimitMinutes else dailyLimitMinutes
        return base + bonusMinutesToday
    }

    fun remainingMinutes(isWeekend: Boolean): Long {
        return (totalAllowanceMinutes(isWeekend) - usedMinutesToday).coerceAtLeast(0L)
    }
}

@IgnoreExtraProperties
data class AppRule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val childId: String = "",
    val packageName: String = "",
    val appName: String = "",
    val category: String = "General",
    val status: String = STATUS_ALLOWED,
    val dailyLimitMinutes: Long = 0L,
    val usedMinutesToday: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val STATUS_ALLOWED = "allowed"
        const val STATUS_LIMITED = "limited"
        const val STATUS_BLOCKED = "blocked"
    }
}

@IgnoreExtraProperties
data class RewardQuest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val childId: String = "",
    val title: String = "",
    val rewardMinutes: Long = 15L,
    val status: String = STATUS_OPEN,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
) {
    companion object {
        const val STATUS_OPEN = "open"
        const val STATUS_PENDING_APPROVAL = "pending_approval"
        const val STATUS_APPROVED = "approved"
    }
}
