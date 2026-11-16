package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppRule
import com.example.data.model.RewardQuest
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ParentalRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun familySettings_andChildCrud_succeedsForOwner() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = ParentalRepository(firestore)

        val settings = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureFamilySettings(uid, "Alice Parent")
        }
        assertEquals(uid, settings.userId)

        val childId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChildProfile(
                userId = uid,
                name = "Maya",
                age = 8L,
                avatarKey = "fox",
                dailyLimitMinutes = 120L,
                weekendLimitMinutes = 180L,
                contentRating = "preteen_8_12"
            )
        }
        assertTrue(childId.isNotBlank())

        val children = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChildren(uid).first { list -> list.any { it.id == childId } }
        }
        val maya = children.first { it.id == childId }
        assertEquals("Maya", maya.name)

        val ruleId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertAppRule(
                userId = uid,
                childId = childId,
                existingRuleId = null,
                packageName = "com.example.kidsgame",
                appName = "Puzzle World",
                category = "Games",
                status = AppRule.STATUS_LIMITED,
                dailyLimitMinutes = 30L,
                usedMinutesToday = 10L
            )
        }
        assertTrue(ruleId.isNotBlank())

        val questId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createQuest(uid, childId, "Finish reading", 20L)
        }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.requestQuestApproval(uid, childId, questId)
        }
        val questList = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeQuests(uid, childId).first { list ->
                list.any { it.id == questId && it.status == RewardQuest.STATUS_PENDING_APPROVAL }
            }
        }
        val quest = questList.first { it.id == questId }
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.approveQuestAndGrantBonus(uid, maya, quest)
        }

        val updatedChildren = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChildren(uid).first { list ->
                list.any { it.id == childId && it.bonusMinutesToday == 20L }
            }
        }
        assertEquals(20L, updatedChildren.first { it.id == childId }.bonusMinutesToday)
    }

    @Test
    fun crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = ParentalRepository(firestore)
        val childId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChildProfile(
                userId = aliceUid,
                name = "Leo",
                age = 10L,
                avatarKey = "owl",
                dailyLimitMinutes = 90L,
                weekendLimitMinutes = 150L,
                contentRating = "preteen_8_12"
            )
        }

        signInTestUser(BOB_EMAIL)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChildren(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's children")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repo.updateChildControls(
                    userId = aliceUid,
                    childId = childId,
                    isPaused = true,
                    usedMinutesToday = 10L,
                    bonusMinutesToday = 0L
                )
            }
            fail("Expected PERMISSION_DENIED when Bob updates Alice's child")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    @Test
    fun unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ParentalRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeChildren("some_user_uid").first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsError = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsError)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError!!.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice_parent@test.com"
        const val BOB_EMAIL = "bob_parent@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
