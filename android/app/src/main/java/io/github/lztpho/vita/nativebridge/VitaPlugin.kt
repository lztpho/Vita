// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.app.AlertDialog
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import androidx.activity.result.ActivityResult
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import java.util.concurrent.Executors

@CapacitorPlugin(name = "Vita")
class VitaPlugin : Plugin() {
    companion object { private const val MAX_TOTAL_UPLOAD_BYTES = 24 * 1024 * 1024 }
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var secureStore: SecureStore
    private lateinit var db: VitaDatabase
    private lateinit var imageVault: ImageVault
    private lateinit var models: ModelClient
    private lateinit var diagnosticLog: DiagnosticLog

    override fun load() {
        secureStore = SecureStore(context)
        db = VitaDatabase.get(context, secureStore)
        imageVault = ImageVault(context, secureStore)
        models = ModelClient(secureStore)
        diagnosticLog = DiagnosticLog.from(context)
        diagnosticLog.record("app_open")
    }

    @PluginMethod fun getAppState(call: PluginCall) = async(call) {
        val today = daySummary(LocalDate.now())
        JSONObject().put("provider", models.sanitizedProvider()).put("today", today).putOpt("currentGoal", today.optJSONObject("goal"))
    }

    @PluginMethod fun configureProvider(call: PluginCall) = async(call) {
        JSONObject().put("provider", models.configure(JSONObject(call.data.toString())))
    }

    @PluginMethod fun promptApiKey(call: PluginCall) {
        if (call.getBoolean("clear", false) == true) {
            secureStore.clearApiKey()
            diagnosticLog.record("api_key_clear")
            call.resolve(JSObject().put("hasApiKey", false))
            return
        }
        val suppliedBaseUrl = call.getString("baseUrl").orEmpty().trim()
        val protocol = call.getString("protocol") ?: "openai"
        val validatedSuppliedBaseUrl = suppliedBaseUrl.takeIf { it.isNotBlank() }?.let { value ->
            runCatching { NetworkPolicy.validate(value).toString().trimEnd('/') }.getOrNull()
        }
        activity.runOnUiThread {
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int) = (value * density + 0.5f).toInt()
            fun background(color: String, radius: Int, strokeColor: String? = null, strokeWidth: Int = 0) =
                GradientDrawable().apply {
                    setColor(Color.parseColor(color))
                    cornerRadius = dp(radius).toFloat()
                    strokeColor?.let { setStroke(dp(strokeWidth), Color.parseColor(it)) }
                }
            fun textView(text: String, size: Float, color: String, bold: Boolean = false) = TextView(activity).apply {
                this.text = text
                textSize = size
                setTextColor(Color.parseColor(color))
                if (bold) setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
            }
            fun fieldLayout(topMargin: Int = 8) = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(52),
            ).apply { this.topMargin = dp(topMargin) }
            fun styleInput(input: EditText) = input.apply {
                textSize = 16f
                setTextColor(Color.parseColor("#202231"))
                setHintTextColor(Color.parseColor("#9295A4"))
                setPadding(dp(16), 0, dp(16), 0)
                background = background("#FAFAFD", 14, "#D7D8E2", 1)
            }
            val endpointInput = if (validatedSuppliedBaseUrl == null) {
                styleInput(EditText(activity)).apply {
                    hint = "API Base URL"
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setSingleLine(true)
                    setText(suppliedBaseUrl)
                }
            } else null
            val keyInput = styleInput(EditText(activity)).apply {
                hint = "API Key"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                transformationMethod = PasswordTransformationMethod.getInstance()
                setSingleLine(true)
                setPadding(dp(16), 0, dp(72), 0)
            }
            var keyVisible = false
            val visibilityToggle = textView("显示", 14f, "#292F69", true).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "显示 API Key"
                setPadding(dp(10), 0, dp(10), 0)
                setOnClickListener {
                    keyVisible = !keyVisible
                    keyInput.transformationMethod = if (keyVisible) {
                        HideReturnsTransformationMethod.getInstance()
                    } else {
                        PasswordTransformationMethod.getInstance()
                    }
                    text = if (keyVisible) "隐藏" else "显示"
                    contentDescription = if (keyVisible) "隐藏 API Key" else "显示 API Key"
                    keyInput.setSelection(keyInput.text?.length ?: 0)
                }
            }
            val keyField = FrameLayout(activity).apply {
                addView(keyInput, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
                addView(visibilityToggle, FrameLayout.LayoutParams(dp(64), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.END))
            }
            val cancelButton = Button(activity).apply {
                text = "取消"
                textSize = 16f
                isAllCaps = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#292F69"))
                background = background("#F8F8FC", 14, "#D7D8E2", 1)
                stateListAnimator = null
            }
            val saveButton = Button(activity).apply {
                text = "保存"
                textSize = 16f
                isAllCaps = false
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = background("#292F69", 14)
                stateListAnimator = null
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                isFocusableInTouchMode = true
                setPadding(dp(24), dp(22), dp(24), dp(20))
                background = background("#FFFFFF", 24)

                addView(textView("填写 API Key", 23f, "#202231", true))
                addView(textView(
                    if (endpointInput == null) "加密保存在本机，仅用于当前接口"
                    else "填写 HTTPS 接口地址和 Key，并加密保存在本机",
                    14f,
                    "#747786",
                ), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(8) })

                endpointInput?.let {
                    addView(textView("API Base URL", 13f, "#4B4E5C", true), LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = dp(20) })
                    addView(it, fieldLayout())
                }
                addView(textView("API Key", 13f, "#4B4E5C", true), LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(if (endpointInput == null) 20 else 16) })
                addView(keyField, fieldLayout())

                val actions = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(cancelButton, LinearLayout.LayoutParams(0, dp(50), 1f))
                    addView(Space(activity), LinearLayout.LayoutParams(dp(12), 1))
                    addView(saveButton, LinearLayout.LayoutParams(0, dp(50), 1f))
                }
                addView(actions, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(22) })
                requestFocus()
            }
            val dialog = AlertDialog.Builder(activity)
                .setView(content)
                .create()
            var completed = false
            dialog.setOnCancelListener {
                if (!completed) {
                    completed = true
                    call.reject("已取消")
                }
            }
            dialog.setOnShowListener {
                dialog.window?.apply {
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    decorView.setPadding(0, 0, 0, 0)
                    addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                    val availableWidth = activity.resources.displayMetrics.widthPixels - dp(32)
                    setLayout(availableWidth.coerceAtMost(dp(480)), WindowManager.LayoutParams.WRAP_CONTENT)
                }
                cancelButton.setOnClickListener {
                    if (!completed) {
                        completed = true
                        call.reject("已取消")
                    }
                    dialog.dismiss()
                }
                saveButton.setOnClickListener {
                    val rawBaseUrl = endpointInput?.text?.toString().orEmpty().trim().ifBlank { validatedSuppliedBaseUrl.orEmpty() }
                    val keyScope = runCatching {
                        NetworkPolicy.validate(rawBaseUrl).toString().trimEnd('/')
                    }.getOrElse { error ->
                        endpointInput?.error = error.message ?: "请填写有效的 HTTPS 公网地址"
                        return@setOnClickListener
                    }
                    runCatching {
                        secureStore.saveApiKey(keyInput.text?.toString().orEmpty(), keyScope, protocol)
                    }.onSuccess {
                        completed = true
                        keyInput.text?.clear()
                        dialog.dismiss()
                        diagnosticLog.record("api_key_save")
                        call.resolve(JSObject().put("hasApiKey", true).put("baseUrl", keyScope))
                    }.onFailure { error ->
                        diagnosticLog.record("api_key_save", "error", error = error)
                        keyInput.error = error.message ?: "API Key 保存失败"
                    }
                }
            }
            dialog.show()
        }
    }

    @PluginMethod fun testProvider(call: PluginCall) = async(call) { models.test() }

    @PluginMethod fun listProviderModels(call: PluginCall) = async(call) {
        models.listModels(
            call.getString("protocol") ?: "openai",
            call.getString("baseUrl") ?: throw IllegalArgumentException("请先选择 AI 厂商或填写 API Base URL"),
        )
    }

    @PluginMethod fun analyzeMeal(call: PluginCall) = async(call) {
        val images = call.getArray("images") ?: throw IllegalArgumentException("请选择图片")
        require(images.length() in 1..4) { "每餐可选择 1–4 张图片" }
        val consumedAt = call.getLong("consumedAtMs") ?: System.currentTimeMillis()
        val notes = call.getString("notes").orEmpty().trim()
        require(notes.length <= 500) { "补充说明不能超过 500 字" }
        val draftId = UUID.randomUUID().toString()
        try {
            val prepared = (0 until images.length()).map { index -> imageVault.prepare(images.getJSONObject(index), draftId, index) }
            require(prepared.sumOf { it.uploadBytes } <= MAX_TOTAL_UPLOAD_BYTES) { "图片总上传大小超过 24 MiB" }
            val raw = models.analyze(NutritionEngine.analysisPrompt(consumedAt, notes), prepared)
            val payload = NutritionEngine.normalizeAnalysis(raw, draftId, consumedAt, notes, prepared.size)
            saveDraft(payload, "meal")
            JSONObject().put("draft", payload)
        } catch (error: Throwable) {
            imageVault.cancelDraft(draftId)
            throw error
        }
    }

    @PluginMethod fun startMealAnalysis(call: PluginCall) = async(call) {
        val images = call.getArray("images") ?: throw IllegalArgumentException("请选择图片")
        require(images.length() in 1..4) { "每餐可选择 1–4 张图片" }
        val taskId = MealAnalysisTaskManager.start(
            context,
            images,
            call.getLong("consumedAtMs") ?: System.currentTimeMillis(),
            call.getString("notes").orEmpty().trim().also { require(it.length <= 500) { "补充说明不能超过 500 字" } },
        )
        JSONObject().put("taskId", taskId)
    }

    @PluginMethod fun getMealAnalysisTask(call: PluginCall) = async(call) {
        MealAnalysisTaskManager.get(context, call.getString("taskId") ?: throw IllegalArgumentException("缺少分析任务编号"))
    }

    @PluginMethod fun getLatestMealAnalysisTask(call: PluginCall) = async(call) {
        MealAnalysisTaskManager.latest(context)
    }

    @PluginMethod fun forgetMealAnalysisTask(call: PluginCall) = async(call) {
        MealAnalysisTaskManager.forget(context, call.getString("taskId") ?: throw IllegalArgumentException("缺少分析任务编号"))
        JSONObject().put("forgotten", true)
    }

    @PluginMethod fun listMealTemplates(call: PluginCall) = async(call) {
        val days = (call.getInt("days") ?: 90).coerceIn(1, 365)
        val limit = (call.getInt("limit") ?: 30).coerceIn(1, 100)
        val query = call.getString("query").orEmpty().trim().lowercase()
        val meals = db.dao().recentMeals(System.currentTimeMillis() - days * 86_400_000L, 300)
            .filter { entity ->
                query.isBlank() || JSONObject(entity.payloadJson).optJSONArray("items").toObjects().any { it.optString("name").lowercase().contains(query) }
            }
            .take(limit)
        JSONObject().put("meals", JSONArray().also { output -> meals.forEach { entity ->
            val meal = JSONObject(entity.payloadJson)
            val items = meal.optJSONArray("items") ?: JSONArray()
            output.put(JSONObject()
                .put("mealId", entity.id).put("revision", entity.revision).put("consumedAtMs", entity.consumedAtMs)
                .put("mealType", entity.mealType).put("summary", items.toObjects().filterNot { it.optBoolean("removed") }.joinToString("、") { it.optString("name") })
                .put("thumbnailCount", entity.thumbnailCount.takeIf { it > 0 } ?: entity.referenceThumbnailCount)
                .put("caloriesKcal", meal.getJSONObject("totals").getJSONObject("caloriesKcal"))
                .put("proteinG", meal.getJSONObject("totals").getJSONObject("proteinG"))
                .put("items", items))
        } })
    }

    @PluginMethod fun createHistoricalReuseDraft(call: PluginCall) = async(call) {
        val source = db.dao().meal(call.getString("mealId") ?: "") ?: throw IllegalArgumentException("历史餐食不存在")
        val expectedRevision = call.getInt("revision") ?: source.revision
        require(expectedRevision == source.revision) { "历史餐食版本已经变化，请重新选择" }
        val imageSource = MealReferencePolicy.resolveImageSource(source, db.dao()::meal)
        val payload = NutritionEngine.reuseDraft(source, UUID.randomUUID().toString(), call.getLong("consumedAtMs") ?: System.currentTimeMillis())
            .put("sourceMealId", imageSource.id)
            .put("sourceRevision", imageSource.revision)
            .put("referenceThumbnailCount", imageSource.thumbnailCount)
        saveDraft(payload, "meal")
        JSONObject().put("draft", payload)
    }

    @PluginMethod fun updateMealDraft(call: PluginCall) = async(call) {
        val entity = requireDraft(call.getString("draftId"))
        val payload = NutritionEngine.updateDraft(JSONObject(entity.payloadJson), JSONObject(call.data.toString()))
        saveDraft(payload, "meal")
        JSONObject().put("draft", payload)
    }

    @PluginMethod fun refineMealDraft(call: PluginCall) = async(call) {
        val entity = requireDraft(call.getString("draftId"))
        val current = JSONObject(entity.payloadJson)
        val message = call.getString("message").orEmpty().trim()
        require(message.isNotBlank()) { "请先说明需要修正的内容" }
        val raw = models.structuredText(NutritionEngine.refinementPrompt(current, message))
        val payload = NutritionEngine.refineDraft(current, raw, message)
        saveDraft(payload, "meal")
        JSONObject().put("draft", payload)
    }

    @PluginMethod fun confirmMealDraft(call: PluginCall) = async(call) {
        val entity = requireDraft(call.getString("draftId"))
        val payload = JSONObject(entity.payloadJson)
        val mealId = UUID.randomUUID().toString()
        payload.put("id", mealId).put("status", "confirmed")
        val created = System.currentTimeMillis()
        val meal = MealEntity(
            mealId, payload.getLong("consumedAtMs"), payload.getString("mealType"), payload.getString("recordingMethod"),
            payload.optString("sourceMealId").takeIf { it.isNotBlank() }, payload.optInt("sourceRevision").takeIf { payload.has("sourceRevision") },
            1, payload.toString(), payload.optInt("thumbnailCount"), payload.optInt("referenceThumbnailCount"), created,
        )
        db.dao().putMeal(meal)
        imageVault.confirmDraft(entity.id, mealId, meal.thumbnailCount)
        db.dao().deleteDraft(entity.id)
        JSONObject().put("meal", NutritionEngine.recordJson(meal))
    }

    @PluginMethod fun cancelMealDraft(call: PluginCall) = async(call) {
        val entity = requireDraft(call.getString("draftId"))
        val id = entity.id
        imageVault.cancelDraft(id)
        db.dao().deleteDraft(id)
        JSONObject()
    }

    @PluginMethod fun getTodayNutrition(call: PluginCall) = async(call) { JSONObject().put("summary", daySummary(LocalDate.now())) }

    @PluginMethod fun getNutritionDay(call: PluginCall) = async(call) {
        JSONObject().put("summary", daySummary(LocalDate.parse(call.getString("localDate"))))
    }

    @PluginMethod fun getNutritionMonth(call: PluginCall) = async(call) {
        val month = YearMonth.parse(call.getString("month"))
        val days = JSONArray()
        for (day in 1..month.lengthOfMonth()) {
            val date = month.atDay(day)
            val summary = daySummary(date)
            days.put(JSONObject().put("localDate", date.toString()).put("mealCount", summary.getJSONArray("meals").length())
                .putOpt("score", summary.opt("goalMatchScore")).put("complete", summary.optBoolean("complete", true)))
        }
        JSONObject().put("month", month.toString()).put("days", days)
    }

    @PluginMethod fun getMealThumbnail(call: PluginCall) = async(call) {
        val mealId = call.getString("mealId") ?: throw IllegalArgumentException("缺少餐食 ID")
        val meal = db.dao().meal(mealId) ?: throw IllegalArgumentException("餐食不存在")
        val reference = call.getBoolean("reference", false) == true || meal.thumbnailCount == 0
        val source = if (reference) meal.sourceMealId ?: meal.id else meal.id
        JSONObject().put("dataUrl", imageVault.thumbnail(source, call.getInt("index") ?: 0))
    }

    @PluginMethod fun deleteMeal(call: PluginCall) = async(call) {
        val mealId = call.getString("mealId")?.trim().orEmpty()
        require(mealId.isNotBlank()) { "缺少餐食 ID" }
        require(db.dao().meal(mealId) != null) { "餐食不存在或已经删除" }
        require(db.dao().referenceCount(mealId) == 0) { "该餐食的图片仍被历史复用记录引用，请先删除引用记录" }
        require(db.dao().deleteMeal(mealId) == 1) { "餐食删除失败" }
        imageVault.deleteMeal(mealId)
        JSONObject().put("deleted", true)
    }

    @PluginMethod fun createGoalProposal(call: PluginCall) = async(call) {
        val profile = call.getObject("profile") ?: throw IllegalArgumentException("请填写资料")
        val proposal = NutritionEngine.proposal(profile)
        saveDraft(proposal, "goal")
        JSONObject().put("proposal", proposal)
    }

    @PluginMethod fun confirmGoal(call: PluginCall) = async(call) {
        val entity = requireDraft(call.getString("proposalId"), "goal")
        val proposal = JSONObject(entity.payloadJson)
        val targets = call.getObject("targets") ?: proposal.getJSONObject("targets")
        validateTargets(targets)
        val now = System.currentTimeMillis()
        proposal.put("targets", targets).put("confirmed", true).put("effectiveFromMs", now)
        db.dao().putGoal(GoalEntity(entity.id, now, true, proposal.toString(), now))
        db.dao().deleteDraft(entity.id)
        JSONObject().put("goal", proposal)
    }

    @PluginMethod fun getChatSession(call: PluginCall) = async(call) {
        val requested = call.getString("sessionId")?.let { db.dao().session(it) }
        val session = requested ?: db.dao().latestSession() ?: createSession()
        JSONObject().put("session", sessionJson(session))
    }

    @PluginMethod fun newChatSession(call: PluginCall) = async(call) { JSONObject().put("session", sessionJson(createSession(replaceExisting = true))) }

    @PluginMethod fun streamChat(call: PluginCall) {
        val runId = UUID.randomUUID().toString()
        val sessionId = call.getString("sessionId") ?: ""
        val message = call.getString("message").orEmpty().trim()
        if (message.isBlank()) {
            val error = IllegalArgumentException("请输入问题")
            diagnosticLog.record("streamChat", "error", error = error)
            call.reject(error.message!!)
            return
        }
        if (message.length > 2000) {
            val error = IllegalArgumentException("问题不能超过 2000 字")
            diagnosticLog.record("streamChat", "error", error = error)
            call.reject(error.message!!)
            return
        }
        call.resolve(JSObject().put("runId", runId))
        worker.execute {
            val started = System.currentTimeMillis()
            try {
                val session = db.dao().session(sessionId) ?: throw IllegalArgumentException("会话不存在")
                val now = System.currentTimeMillis()
                db.dao().putMessage(ChatMessageEntity(UUID.randomUUID().toString(), session.id, "user", message.take(4000), now))
                val history = db.dao().messages(session.id).takeLast(12).joinToString("\n") { "${it.role}: ${it.content}" }
                val answer = models.text(chatPrompt(history, recentContext()))
                db.dao().putMessage(ChatMessageEntity(UUID.randomUUID().toString(), session.id, "assistant", answer.take(20_000), System.currentTimeMillis()))
                db.dao().putSession(session.copy(title = if (session.title == "新会话") message.take(18) else session.title, updatedAtMs = System.currentTimeMillis()))
                answer.chunked(24).forEach { notifyListeners("chatDelta", JSObject().put("runId", runId).put("delta", it)) }
                notifyListeners("chatDone", JSObject().put("runId", runId).put("session", sessionJson(db.dao().session(session.id)!!)))
                diagnosticLog.record("streamChat", durationMs = System.currentTimeMillis() - started)
            } catch (error: Throwable) {
                diagnosticLog.record("streamChat", "error", System.currentTimeMillis() - started, error)
                notifyListeners("chatError", JSObject().put("runId", runId).put("message", safeMessage(error)))
            }
        }
    }

    private fun daySummary(date: LocalDate): JSONObject {
        val bounds = NutritionEngine.bounds(date)
        return NutritionEngine.summary(date, db.dao().mealsBetween(bounds.first, bounds.second), db.dao().currentGoal(bounds.second - 1))
    }

    private fun recentContext(): JSONObject {
        val days = JSONArray()
        repeat(7) { offset -> days.put(daySummary(LocalDate.now().minusDays(offset.toLong()))) }
        return JSONObject().put("days", days)
    }

    private fun chatPrompt(history: String, context: JSONObject) = """
        你是营养助手 Vita。请用简洁中文回答，不做医学诊断。
        下面是用户已确认的目标、今日和最近七天饮食汇总，数字有估算上下界。不得声称看过未提供的照片。
        饮食上下文：$context
        会话：$history
        请直接回答最后一个用户问题；信息不足时明确指出。
    """.trimIndent()

    private fun saveDraft(payload: JSONObject, kind: String) {
        val now = System.currentTimeMillis()
        db.dao().putDraft(DraftEntity(payload.getString("id"), kind, "draft", payload.toString(), now, now))
    }

    private fun requireDraft(id: String?, kind: String = "meal"): DraftEntity {
        val entity = db.dao().draft(id ?: "") ?: throw IllegalArgumentException("草稿不存在或已经确认")
        require(entity.kind == kind) { "草稿类型不匹配" }
        return entity
    }

    private fun validateTargets(targets: JSONObject) {
        listOf("caloriesKcal", "proteinG", "carbohydrateG", "fatG", "fiberG", "freeSugarG").forEach { key ->
            val value = targets.optJSONObject(key) ?: throw IllegalArgumentException("目标缺少 $key")
            val min = value.optDouble("min", Double.NaN); val max = value.optDouble("max", Double.NaN)
            require(min.isFinite() && max.isFinite() && min >= 0 && max >= min) { "目标范围无效" }
        }
    }

    @PluginMethod fun clearAllLocalData(call: PluginCall) = async(call) {
        require(call.getBoolean("confirmed", false) == true) { "需要明确确认清空全部本地数据" }
        require(!MealAnalysisTaskManager.hasActiveTasks()) { "餐食分析进行中，暂时不能清空数据" }
        db.dao().clearAllUserData()
        imageVault.clearAll()
        VitaDatabase.reset(context)
        secureStore.clearAll()
        secureStore = SecureStore(context)
        db = VitaDatabase.get(context, secureStore)
        imageVault = ImageVault(context, secureStore)
        models = ModelClient(secureStore)
        diagnosticLog.clear()
        JSONObject().put("cleared", true)
    }

    @PluginMethod fun exportDiagnosticLogs(call: PluginCall) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, diagnosticLog.exportFileName())
        }
        startActivityForResult(call, intent, "finishDiagnosticLogExport")
    }

    @ActivityCallback
    private fun finishDiagnosticLogExport(call: PluginCall?, result: ActivityResult) {
        if (call == null) return
        if (result.resultCode != Activity.RESULT_OK) {
            call.resolve(JSObject().put("exported", false))
            return
        }
        val destination = result.data?.data
        if (destination == null) {
            val error = IllegalStateException("没有获得日志保存位置")
            diagnosticLog.record("exportDiagnosticLogs", "error", error = error)
            call.reject(error.message!!, error)
            return
        }
        worker.execute {
            val started = System.currentTimeMillis()
            try {
                val text = diagnosticLog.exportText()
                context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                    output.write(text.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入所选位置")
                diagnosticLog.record("exportDiagnosticLogs", durationMs = System.currentTimeMillis() - started)
                call.resolve(JSObject().put("exported", true))
            } catch (error: Throwable) {
                diagnosticLog.record("exportDiagnosticLogs", "error", System.currentTimeMillis() - started, error)
                call.reject("诊断日志导出失败", error as? Exception ?: Exception(error))
            }
        }
    }

    private fun createSession(replaceExisting: Boolean = false): ChatSessionEntity = ChatSessionEntity(
        UUID.randomUUID().toString(), "新会话", System.currentTimeMillis(), System.currentTimeMillis(),
    ).also { if (replaceExisting) db.dao().replaceChatSession(it) else db.dao().putSession(it) }

    private fun sessionJson(session: ChatSessionEntity) = JSONObject().put("id", session.id).put("title", session.title).put("updatedAtMs", session.updatedAtMs)
        .put("messages", JSONArray().also { array -> db.dao().messages(session.id).forEach { array.put(JSONObject().put("id", it.id).put("role", it.role).put("content", it.content).put("createdAtMs", it.createdAtMs)) } })

    private fun JSONArray?.toObjects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun async(call: PluginCall, block: () -> JSONObject) {
        worker.execute {
            val started = System.currentTimeMillis()
            try {
                val result = block()
                if (call.methodName in loggedSuccessOperations) {
                    diagnosticLog.record(call.methodName, durationMs = System.currentTimeMillis() - started)
                }
                call.resolve(JSObject(result.toString()))
            } catch (error: Throwable) {
                diagnosticLog.record(call.methodName, "error", System.currentTimeMillis() - started, error)
                call.reject(safeMessage(error), error as? Exception ?: Exception(error))
            }
        }
    }

    private val loggedSuccessOperations = setOf(
        "configureProvider", "testProvider", "listProviderModels", "analyzeMeal", "startMealAnalysis",
        "refineMealDraft", "confirmMealDraft", "deleteMeal", "createGoalProposal", "confirmGoal", "newChatSession",
    )

    private fun safeMessage(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: "操作没有完成，请稍后重试"

    override fun handleOnDestroy() {
        worker.shutdownNow()
        super.handleOnDestroy()
    }
}
