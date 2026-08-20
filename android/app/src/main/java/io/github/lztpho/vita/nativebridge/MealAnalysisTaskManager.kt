// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

object MealAnalysisTaskManager {
    private const val MAX_TOTAL_UPLOAD_BYTES = 24 * 1024 * 1024
    private val worker = Executors.newSingleThreadExecutor()
    private val activeTasks = AtomicInteger(0)

    @Synchronized fun start(context: Context, sourceImages: JSONArray, consumedAtMs: Long, notes: String): String {
        require(sourceImages.length() in 1..4) { "每餐可选择 1–4 张图片" }
        require(activeTasks.get() == 0) { "已有餐食分析正在进行，请等待完成" }
        val appContext = context.applicationContext
        val taskId = UUID.randomUUID().toString()
        val draftId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val images = JSONArray(sourceImages.toString())
        val store = SecureStore(appContext)
        val diagnosticLog = DiagnosticLog.from(appContext)
        save(store, taskId, draftId, "queued", "prepare", startedAt, 0, images.length(), 0)
        diagnosticLog.record("meal_analysis", phase = queuedDiagnosticPhase(notes))
        activeTasks.incrementAndGet()
        MealAnalysisService.start(appContext)
        worker.execute {
            val imageVault = ImageVault(appContext, store)
            try {
                val prepared = mutableListOf<PreparedImage>()
                for (index in 0 until images.length()) {
                    save(store, taskId, draftId, "running", "prepare", startedAt, index, images.length(), 0)
                    prepared += imageVault.prepare(images.getJSONObject(index), draftId, index)
                    require(prepared.sumOf { it.uploadBytes } <= MAX_TOTAL_UPLOAD_BYTES) { "图片总上传大小超过 24 MiB" }
                }
                save(store, taskId, draftId, "running", "upload", startedAt, prepared.size, prepared.size, 0)
                var lastSavedProgress = -5
                val raw = ModelClient(store).analyze(
                    NutritionEngine.analysisPrompt(consumedAtMs, notes.take(500)),
                    prepared,
                ) { percent ->
                    val normalized = percent.coerceIn(0, 100)
                    if (normalized >= lastSavedProgress + 5 || normalized == 100) {
                        lastSavedProgress = normalized
                        val phase = if (normalized >= 100) "model" else "upload"
                        save(store, taskId, draftId, "running", phase, startedAt, prepared.size, prepared.size, normalized)
                    }
                }
                save(store, taskId, draftId, "running", "validate", startedAt, prepared.size, prepared.size, 100)
                val payload = NutritionEngine.normalizeAnalysis(raw, draftId, consumedAtMs, notes.take(500), prepared.size)
                val now = System.currentTimeMillis()
                VitaDatabase.get(appContext, store).dao().putDraft(DraftEntity(draftId, "meal", "draft", payload.toString(), now, now))
                save(store, taskId, draftId, "succeeded", "done", startedAt, prepared.size, prepared.size, 100)
                diagnosticLog.record("meal_analysis", durationMs = now - startedAt, phase = "done")
            } catch (error: Throwable) {
                imageVault.cancelDraft(draftId)
                val message = error.message?.takeIf(String::isNotBlank) ?: "餐食分析没有完成"
                val failed = state(taskId, draftId, "failed", "failed", startedAt, 0, images.length(), 0)
                    .put("completedAt", System.currentTimeMillis())
                    .put("message", message.take(500))
                store.saveMealTask(taskId, failed)
                diagnosticLog.record("meal_analysis", "error", System.currentTimeMillis() - startedAt, error, "failed")
            } finally {
                if (activeTasks.decrementAndGet() <= 0) MealAnalysisService.stop(appContext)
            }
        }
        return taskId
    }

    internal fun queuedDiagnosticPhase(notes: String): String =
        if (notes.isBlank()) "queued_without_notes" else "queued_with_notes"

    fun get(context: Context, taskId: String): JSONObject {
        val appContext = context.applicationContext
        val store = SecureStore(appContext)
        var task = store.mealTask(taskId) ?: throw IllegalArgumentException("餐食分析任务不存在或已清理")
        if (task.optString("status") in setOf("queued", "running") && activeTasks.get() == 0) {
            task = JSONObject(task.toString())
                .put("status", "failed")
                .put("phase", "failed")
                .put("completedAt", System.currentTimeMillis())
                .put("message", "系统回收了分析进程，请重新分析")
            store.saveMealTask(taskId, task)
        }
        if (task.optString("status") == "succeeded") {
            val draft = VitaDatabase.get(appContext, store).dao().draft(task.getString("draftId"))
                ?: throw IllegalStateException("分析完成，但餐食草稿无法读取")
            task = JSONObject(task.toString()).put("draft", JSONObject(draft.payloadJson))
        }
        return task
    }

    fun latest(context: Context): JSONObject {
        val store = SecureStore(context.applicationContext)
        val taskId = store.latestMealTaskId()
        return if (taskId.isBlank()) JSONObject().put("status", "none") else get(context, taskId)
    }

    fun forget(context: Context, taskId: String) = SecureStore(context.applicationContext).clearMealTask(taskId)

    fun hasActiveTasks(): Boolean = activeTasks.get() > 0

    private fun save(
        store: SecureStore,
        taskId: String,
        draftId: String,
        status: String,
        phase: String,
        startedAt: Long,
        processedImages: Int,
        totalImages: Int,
        uploadPercent: Int,
    ) = store.saveMealTask(taskId, state(taskId, draftId, status, phase, startedAt, processedImages, totalImages, uploadPercent))

    private fun state(
        taskId: String,
        draftId: String,
        status: String,
        phase: String,
        startedAt: Long,
        processedImages: Int,
        totalImages: Int,
        uploadPercent: Int,
    ) = JSONObject()
        .put("taskId", taskId)
        .put("draftId", draftId)
        .put("status", status)
        .put("phase", phase)
        .put("startedAt", startedAt)
        .put("processedImages", processedImages)
        .put("totalImages", totalImages)
        .put("uploadPercent", uploadPercent)
}
