// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "meals", primaryKeys = ["id"])
data class MealEntity(
    val id: String,
    val consumedAtMs: Long,
    val mealType: String,
    val recordingMethod: String,
    val sourceMealId: String?,
    val sourceRevision: Int?,
    val revision: Int,
    val payloadJson: String,
    val thumbnailCount: Int,
    val referenceThumbnailCount: Int,
    val createdAtMs: Long,
)

@Entity(tableName = "drafts", primaryKeys = ["id"])
data class DraftEntity(val id: String, val kind: String, val status: String, val payloadJson: String, val createdAtMs: Long, val updatedAtMs: Long)

@Entity(tableName = "goals", primaryKeys = ["id"])
data class GoalEntity(val id: String, val effectiveFromMs: Long, val confirmed: Boolean, val payloadJson: String, val createdAtMs: Long)

@Entity(tableName = "chat_sessions", primaryKeys = ["id"])
data class ChatSessionEntity(val id: String, val title: String, val createdAtMs: Long, val updatedAtMs: Long)

@Entity(tableName = "chat_messages", primaryKeys = ["id"])
data class ChatMessageEntity(val id: String, val sessionId: String, val role: String, val content: String, val createdAtMs: Long)

@Dao
interface VitaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putMeal(entity: MealEntity)
    @Query("SELECT * FROM meals WHERE id = :id LIMIT 1") fun meal(id: String): MealEntity?
    @Query("SELECT * FROM meals WHERE consumedAtMs >= :from AND consumedAtMs < :to ORDER BY consumedAtMs") fun mealsBetween(from: Long, to: Long): List<MealEntity>
    @Query("SELECT * FROM meals WHERE consumedAtMs >= :from ORDER BY consumedAtMs DESC LIMIT :limit") fun recentMeals(from: Long, limit: Int): List<MealEntity>
    @Query("DELETE FROM meals WHERE id = :id") fun deleteMeal(id: String): Int
    @Query("SELECT COUNT(*) FROM meals WHERE sourceMealId = :id") fun referenceCount(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putDraft(entity: DraftEntity)
    @Query("SELECT * FROM drafts WHERE id = :id LIMIT 1") fun draft(id: String): DraftEntity?
    @Query("DELETE FROM drafts WHERE id = :id") fun deleteDraft(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putGoal(entity: GoalEntity)
    @Query("SELECT * FROM goals WHERE confirmed = 1 AND effectiveFromMs <= :time ORDER BY effectiveFromMs DESC LIMIT 1") fun currentGoal(time: Long): GoalEntity?
    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1") fun goal(id: String): GoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putSession(entity: ChatSessionEntity)
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAtMs DESC LIMIT 1") fun latestSession(): ChatSessionEntity?
    @Query("SELECT * FROM chat_sessions WHERE id = :id LIMIT 1") fun session(id: String): ChatSessionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putMessage(entity: ChatMessageEntity)
    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAtMs, id") fun messages(sessionId: String): List<ChatMessageEntity>
    @Query("DELETE FROM chat_messages") fun deleteAllMessages()
    @Query("DELETE FROM chat_sessions") fun deleteAllSessions()
    @Query("DELETE FROM meals") fun deleteAllMeals()
    @Query("DELETE FROM drafts") fun deleteAllDrafts()
    @Query("DELETE FROM goals") fun deleteAllGoals()

    @Transaction fun replaceChatSession(entity: ChatSessionEntity) {
        deleteAllMessages()
        deleteAllSessions()
        putSession(entity)
    }

    @Transaction fun clearAllUserData() {
        deleteAllMessages()
        deleteAllSessions()
        deleteAllMeals()
        deleteAllDrafts()
        deleteAllGoals()
    }
}

@Database(
    entities = [MealEntity::class, DraftEntity::class, GoalEntity::class, ChatSessionEntity::class, ChatMessageEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VitaDatabase : RoomDatabase() {
    abstract fun dao(): VitaDao

    companion object {
        @Volatile private var instance: VitaDatabase? = null
        fun get(context: Context, secureStore: SecureStore): VitaDatabase = instance ?: synchronized(this) {
            instance ?: run {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(secureStore.databaseKey())
                Room.databaseBuilder(context.applicationContext, VitaDatabase::class.java, "vita.db")
                    .openHelperFactory(factory)
                    .build()
                    .also { instance = it }
            }
        }

        @Synchronized fun reset(context: Context) {
            instance?.close()
            instance = null
            check(context.applicationContext.deleteDatabase("vita.db")) { "无法删除本地数据库" }
        }
    }
}
