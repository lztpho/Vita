// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import androidx.room.Room
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VitaDaoTest {
    private lateinit var database: VitaDatabase
    private lateinit var dao: VitaDao

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            VitaDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.dao()
    }

    @After fun tearDown() = database.close()

    @Test fun replacingChatSessionDeletesThePreviousSessionAndMessagesAtomically() {
        val old = ChatSessionEntity("old", "old", 1, 1)
        dao.putSession(old)
        dao.putMessage(ChatMessageEntity("message", old.id, "user", "sensitive", 1))
        val fresh = ChatSessionEntity("fresh", "新会话", 2, 2)

        dao.replaceChatSession(fresh)

        assertNull(dao.session(old.id))
        assertEquals(emptyList<ChatMessageEntity>(), dao.messages(old.id))
        assertEquals("fresh", dao.latestSession()?.id)
    }

    @Test fun clearAllRemovesEveryUserDataTable() {
        dao.putMeal(MealEntity("meal", 1, "lunch", "photo_analysis", null, null, 1, "{}", 0, 0, 1))
        dao.putDraft(DraftEntity("draft", "meal", "draft", "{}", 1, 1))
        dao.putGoal(GoalEntity("goal", 1, true, "{}", 1))
        dao.putSession(ChatSessionEntity("session", "title", 1, 1))
        dao.putMessage(ChatMessageEntity("message", "session", "user", "text", 1))

        dao.clearAllUserData()

        assertNull(dao.meal("meal"))
        assertNull(dao.draft("draft"))
        assertNull(dao.goal("goal"))
        assertNull(dao.session("session"))
        assertEquals(emptyList<ChatMessageEntity>(), dao.messages("session"))
    }

    @Test fun sourceReferenceCountProtectsThumbnailOwners() {
        dao.putMeal(MealEntity("root", 1, "lunch", "photo_analysis", null, null, 1, "{}", 1, 0, 1))
        dao.putMeal(MealEntity("reuse", 2, "lunch", "historical_reuse", "root", 1, 1, "{}", 0, 1, 2))
        assertEquals(1, dao.referenceCount("root"))
        assertEquals(0, dao.referenceCount("reuse"))
    }
}
