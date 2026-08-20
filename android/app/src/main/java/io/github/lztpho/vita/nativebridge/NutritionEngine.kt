// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

object NutritionEngine {
    private const val INVALID_MODEL_ANALYSIS = "模型未返回可用的餐食识别结果。所选模型可能不支持图片输入，或未按要求返回结构化 JSON；请在设置中更换多模态模型并重新测试"
    private val nutrientKeys = listOf("caloriesKcal", "proteinG", "carbohydrateG", "fatG", "fiberG", "totalSugarG", "freeSugarG")
    private val labels = mapOf("caloriesKcal" to "热量", "proteinG" to "蛋白质", "carbohydrateG" to "碳水", "fatG" to "脂肪", "fiberG" to "膳食纤维", "totalSugarG" to "总糖", "freeSugarG" to "游离糖")
    private val units = mapOf("caloriesKcal" to "千卡", "proteinG" to "克", "carbohydrateG" to "克", "fatG" to "克", "fiberG" to "克", "totalSugarG" to "克", "freeSugarG" to "克")
    private val modes = mapOf("caloriesKcal" to "range", "proteinG" to "minimum", "carbohydrateG" to "range", "fatG" to "range", "fiberG" to "minimum", "totalSugarG" to "observe", "freeSugarG" to "maximum")

    fun normalizeAnalysis(raw: JSONObject, draftId: String, consumedAtMs: Long, notes: String, thumbnailCount: Int): JSONObject {
        validateConsumedAt(consumedAtMs)
        val rawItems = raw.optJSONArray("items") ?: throw IllegalArgumentException(INVALID_MODEL_ANALYSIS)
        require(rawItems.length() in 1..30) { INVALID_MODEL_ANALYSIS }
        val items = JSONArray()
        repeat(rawItems.length()) { index ->
            val item = rawItems.optJSONObject(index) ?: throw IllegalArgumentException(INVALID_MODEL_ANALYSIS)
            val base = normalizeNutrients(item.optJSONObject("nutrients") ?: JSONObject())
            items.put(JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("name", item.optString("name").trim().take(80).ifEmpty { "未命名食物" })
                .put("amountLabel", item.optString("amountLabel").trim().take(80).ifEmpty { "份量待确认" })
                .put("confidence", normalizeConfidence(item.optString("confidence")))
                .put("assumptions", normalizeStrings(item.optJSONArray("assumptions"), 3, 120))
                .put("multiplier", 1.0)
                .put("removed", false)
                .put("baseNutrients", base)
                .put("nutrients", JSONObject(base.toString())))
        }
        val payload = JSONObject()
            .put("id", draftId)
            .put("consumedAtMs", consumedAtMs)
            .put("mealType", mealType(consumedAtMs))
            .put("mealTypeSource", "automatic")
            .put("recordingMethod", "photo_analysis")
            .put("overallMultiplier", 1.0)
            .put("items", items)
            .put("thumbnailCount", thumbnailCount)
            .put("referenceThumbnailCount", 0)
            .put("notes", notes.take(500))
            .put("confidence", normalizeConfidence(raw.optString("confidence")))
            .put("nutritionSummary", raw.optString("nutritionSummary").trim().take(320))
            .put("nutritionHighlights", normalizeStrings(raw.optJSONArray("nutritionHighlights"), 3, 160))
            .put("nutritionAttention", normalizeStrings(raw.optJSONArray("nutritionAttention"), 3, 160))
            .put("assumptions", normalizeStrings(raw.optJSONArray("assumptions"), 6, 160))
            .put("correctionConversation", JSONArray())
            .put("status", "draft")
        return recompute(payload)
    }

    fun reuseDraft(source: MealEntity, draftId: String, consumedAtMs: Long): JSONObject {
        validateConsumedAt(consumedAtMs)
        val old = JSONObject(source.payloadJson)
        val copiedItems = JSONArray()
        val items = old.optJSONArray("items") ?: JSONArray()
        repeat(items.length()) { index ->
            val item = JSONObject(items.getJSONObject(index).toString())
            item.put("id", UUID.randomUUID().toString()).put("multiplier", 1.0).put("removed", false)
            item.put("baseNutrients", JSONObject(item.optJSONObject("baseNutrients")?.toString() ?: item.getJSONObject("nutrients").toString()))
            copiedItems.put(item)
        }
        return recompute(JSONObject()
            .put("id", draftId)
            .put("consumedAtMs", consumedAtMs)
            .put("mealType", mealType(consumedAtMs))
            .put("mealTypeSource", "automatic")
            .put("recordingMethod", "historical_reuse")
            .put("sourceMealId", source.id)
            .put("sourceRevision", source.revision)
            .put("overallMultiplier", 1.0)
            .put("items", copiedItems)
            .put("thumbnailCount", 0)
            .put("referenceThumbnailCount", source.thumbnailCount.takeIf { it > 0 } ?: source.referenceThumbnailCount)
            .put("notes", "")
            .put("confidence", old.optString("confidence", "medium"))
            .put("nutritionSummary", old.optString("nutritionSummary"))
            .put("nutritionHighlights", old.optJSONArray("nutritionHighlights") ?: JSONArray())
            .put("nutritionAttention", old.optJSONArray("nutritionAttention") ?: JSONArray())
            .put("assumptions", old.optJSONArray("assumptions") ?: JSONArray())
            .put("correctionConversation", JSONArray())
            .put("status", "draft"))
    }

    fun refineDraft(current: JSONObject, raw: JSONObject, message: String): JSONObject {
        require(current.optString("recordingMethod") == "photo_analysis") { "历史复用餐食请直接调整份量或重新拍照" }
        val refined = normalizeAnalysis(
            raw,
            current.getString("id"),
            current.getLong("consumedAtMs"),
            current.optString("notes"),
            current.optInt("thumbnailCount"),
        )
        if (current.optString("mealTypeSource") == "user_override") {
            refined.put("mealType", current.getString("mealType")).put("mealTypeSource", "user_override")
        }
        val conversation = current.optJSONArray("correctionConversation") ?: JSONArray()
        conversation.put(JSONObject().put("role", "user").put("content", message.trim().take(500)))
        conversation.put(JSONObject().put("role", "assistant").put(
            "content",
            raw.optString("correctionReply").trim().take(240).ifEmpty { "已根据你的说明更新食物和份量估算。" },
        ))
        refined.put("correctionConversation", conversation)
        return refined
    }

    fun updateDraft(payload: JSONObject, options: JSONObject): JSONObject {
        if (options.has("consumedAtMs")) {
            val time = options.getLong("consumedAtMs"); validateConsumedAt(time); payload.put("consumedAtMs", time)
            if (!options.has("mealType")) payload.put("mealType", mealType(time))
        }
        if (options.has("mealType")) {
            val type = options.getString("mealType")
            require(type in listOf("breakfast", "lunch", "dinner", "snack", "late_night")) { "餐次无效" }
            payload.put("mealType", type).put("mealTypeSource", "user_override")
        }
        if (options.has("overallMultiplier")) {
            val value = options.getDouble("overallMultiplier")
            require(value in 0.25..3.0) { "整餐份量倍率必须在 0.25–3 之间" }
            payload.put("overallMultiplier", value)
        }
        val multipliers = options.optJSONObject("itemMultipliers") ?: JSONObject()
        val removed = options.optJSONArray("removedItemIds")?.let { array -> (0 until array.length()).map { array.getString(it) }.toSet() }
        val items = payload.getJSONArray("items")
        repeat(items.length()) { index ->
            val item = items.getJSONObject(index)
            val id = item.getString("id")
            if (multipliers.has(id)) {
                val value = multipliers.getDouble(id); require(value in 0.25..3.0) { "单项份量倍率必须在 0.25–3 之间" }; item.put("multiplier", value)
            }
            if (removed != null) item.put("removed", removed.contains(id))
        }
        return recompute(payload)
    }

    fun summary(localDate: LocalDate, meals: List<MealEntity>, goal: GoalEntity?): JSONObject {
        val total = zeroNutrients()
        meals.forEach { addNutrients(total, JSONObject(it.payloadJson).getJSONObject("totals")) }
        val goalPayload = goal?.let { JSONObject(it.payloadJson) }
        val targets = goalPayload?.optJSONObject("targets")
        val metrics = JSONArray()
        nutrientKeys.forEach { key ->
            val intake = total.getJSONObject(key)
            val target = if (key == "totalSugarG") null else targets?.optJSONObject(key)
            val mode = modes.getValue(key)
            val progress = progress(intake, target, mode, meals.isNotEmpty())
            metrics.put(JSONObject().put("key", key).put("label", labels.getValue(key)).put("unit", units.getValue(key))
                .put("intake", intake).putOpt("target", target).put("mode", mode).put("state", progress.first).put("progress", progress.second))
        }
        val mealArray = JSONArray().also { array -> meals.forEach { array.put(recordJson(it)) } }
        return JSONObject().put("localDate", localDate.toString()).put("metrics", metrics).put("meals", mealArray)
            .putOpt("goal", goalPayload).putOpt("goalMatchScore", score(total, targets, meals.isNotEmpty()))
            .put("complete", localDate.isBefore(LocalDate.now()) || localDate.isEqual(LocalDate.now()))
    }

    fun recordJson(entity: MealEntity): JSONObject = JSONObject(entity.payloadJson)
        .put("revision", entity.revision).put("status", "confirmed").put("createdAtMs", entity.createdAtMs)

    fun proposal(profile: JSONObject, today: LocalDate = LocalDate.now()): JSONObject {
        val id = UUID.randomUUID().toString()
        require(profile.optBoolean("generalHealthEligible", false)) { "此功能只面向一般健康成人；如有妊娠/哺乳、饮食障碍、慢性病、用药或医生规定饮食，请咨询专业人员" }
        val weight = profile.getDouble("weightKg")
        val height = profile.getDouble("heightCm")
        require(weight in 30.0..300.0 && height in 120.0..230.0) { "身高或体重不在支持范围内" }
        val birthDate = LocalDate.parse(profile.getString("birthDate"))
        val age = ChronoUnit.YEARS.between(birthDate, today).toInt()
        require(age in 18..64) { "数值目标只支持 18–64 岁的一般健康成人" }
        val bmi = weight / ((height / 100.0) * (height / 100.0))
        require(bmi in 18.5..34.9) { "当前 BMI 不在此通用目标工具的 18.5–34.9 适用范围内" }
        val equationSex = profile.optString("equationSex")
        require(equationSex == "female" || equationSex == "male") { "能量方程参数无效" }
        val sexOffset = if (equationSex == "female") -161 else 5
        val bmr = 10 * weight + 6.25 * height - 5 * age + sexOffset
        val activity = when (profile.optString("activityLevel")) { "sedentary" -> 1.2; "moderate" -> 1.55; "high" -> 1.725; else -> 1.375 }
        val goalType = profile.optString("goalType", "maintenance")
        require(goalType in listOf("muscle_gain", "fat_loss", "recomposition", "maintenance")) { "阶段目标无效" }
        val factor = when (goalType) { "muscle_gain" -> 1.05; "fat_loss" -> 0.90; else -> 1.0 }
        val center = bmr * activity * factor
        val minimumCalories = if (equationSex == "female") 1200.0 else 1500.0
        val safeCalories = range(round5(center * .97), round5(center * 1.03))
        require(safeCalories.getDouble("min") >= minimumCalories) { "计算结果低于此通用工具的安全下限，不生成数值目标" }
        require(safeCalories.getDouble("max") <= 4500.0) { "计算结果高于此通用工具的支持上限，不生成数值目标" }
        val proteinFactor = when (goalType) { "fat_loss" -> 1.9 to 2.2; "muscle_gain", "recomposition" -> 1.6 to 2.0; else -> 1.4 to 1.8 }
        val targets = JSONObject()
            .put("caloriesKcal", safeCalories)
            .put("proteinG", range(weight * proteinFactor.first, weight * proteinFactor.second))
            .put("fatG", range(safeCalories.getDouble("min") * .22 / 9, safeCalories.getDouble("max") * .30 / 9))
            .put("fiberG", range(25.0, 35.0))
            .put("freeSugarG", range(0.0, min(25.0, safeCalories.getDouble("max") * .05 / 4)))
        val proteinMid = midpoint(targets.getJSONObject("proteinG"))
        val fatMid = midpoint(targets.getJSONObject("fatG"))
        targets.put("carbohydrateG", range(max(80.0, (safeCalories.getDouble("min") - proteinMid * 4 - fatMid * 9) / 4), max(100.0, (safeCalories.getDouble("max") - proteinMid * 4 - fatMid * 9) / 4)))
        return JSONObject().put("id", id).put("goalType", goalType)
            .put("explanation", "目标完全由本机依据 Mifflin–St Jeor 能量方程、活动水平与阶段目标确定计算；减脂缺口不超过 10%，增肌盈余不超过 5%。")
            .put("targets", targets)
            .put("warnings", JSONArray().put("健康管理参考，非医学诊断；身体状况变化时请停止使用数值目标并咨询专业人员。"))
            .put("confirmed", false)
    }

    fun analysisPrompt(consumedAtMs: Long, notes: String): String = """
        把全部图片作为同一餐分析，跨图片重复出现的食物只计算一次。逐项列出画面中实际可区分的食物，不要把鸡腿和鸡翅之类不同食物合并成“鸡腿/鸡翅”，也不要用斜杠给出多个候选名称。
        包装食品的正面、配料表和营养成分表属于同一件食物。优先读取包装标示的净含量及每100克营养值，再按预计实际摄入量换算整份营养；同一包装不得跨照片重复计算。
        只输出 JSON：{"confidence":"high|medium|low","nutritionSummary":"一句中文整餐总结","nutritionHighlights":["最多3条"],"nutritionAttention":["最多3条"],"assumptions":["主要估算依据"],"items":[{"name":"单一食物名","amountLabel":"约 100–150 克","confidence":"high|medium|low","assumptions":["该项估算依据"],"nutrients":{"caloriesKcal":{"min":0,"max":0},"proteinG":{"min":0,"max":0},"carbohydrateG":{"min":0,"max":0},"fatG":{"min":0,"max":0},"fiberG":{"min":0,"max":0},"totalSugarG":{"min":0,"max":0},"freeSugarG":{"min":0,"max":0}}}]}
        总糖包括天然糖；游离糖包括添加糖以及蜂蜜、糖浆、果汁中的糖。饮料按普通食物项处理，不另建饮料分类。
        实际进餐时间毫秒：$consumedAtMs。用户补充：${notes.ifBlank { "无" }}。
        保留估算上下界，不把无法识别的项目编造成精确值。总结和提醒只描述本餐，不做医学诊断。
    """.trimIndent()

    fun refinementPrompt(current: JSONObject, message: String): String = """
        用户正在修正一份尚未确认的餐食识别结果。根据用户说明更新食物、份量、营养范围和整餐解读；没有被修正的内容保持不变。不要添加用户和原结果都没有依据的新食物。
        当前结果：$current
        用户修正：${message.trim().take(500)}
        只输出与餐食分析相同的 JSON 结构，并额外返回 "correctionReply":"一句中文说明已修改什么"。每个 items 项必须是单一食物，不要使用斜杠候选名称。
    """.trimIndent()

    private fun normalizeNutrients(raw: JSONObject): JSONObject = JSONObject().also { normalized -> nutrientKeys.forEach { key ->
        val value = raw.optJSONObject(key) ?: JSONObject()
        val minValue = value.optDouble("min", 0.0).takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val maxValue = value.optDouble("max", minValue).takeIf { it.isFinite() }?.coerceAtLeast(minValue) ?: minValue
        val cap = if (key == "caloriesKcal") 5000.0 else 1000.0
        normalized.put(key, range(minValue.coerceAtMost(cap), maxValue.coerceAtMost(cap)))
    } }

    private fun recompute(payload: JSONObject): JSONObject {
        val total = zeroNutrients()
        val overall = payload.optDouble("overallMultiplier", 1.0)
        val items = payload.getJSONArray("items")
        repeat(items.length()) { index ->
            val item = items.getJSONObject(index)
            val base = item.optJSONObject("baseNutrients") ?: item.getJSONObject("nutrients")
            val factor = overall * item.optDouble("multiplier", 1.0)
            val scaled = scaleNutrients(base, factor)
            item.put("nutrients", scaled)
            if (!item.optBoolean("removed", false)) addNutrients(total, scaled)
        }
        return payload.put("totals", total)
    }

    private fun zeroNutrients(): JSONObject = JSONObject().also { result -> nutrientKeys.forEach { result.put(it, range(0.0, 0.0)) } }
    private fun scaleNutrients(source: JSONObject, factor: Double): JSONObject = JSONObject().also { result -> nutrientKeys.forEach { key -> val value = source.getJSONObject(key); result.put(key, range(value.getDouble("min") * factor, value.getDouble("max") * factor)) } }
    private fun addNutrients(target: JSONObject, source: JSONObject) { nutrientKeys.forEach { key -> val a = target.getJSONObject(key); val b = source.getJSONObject(key); a.put("min", a.getDouble("min") + b.getDouble("min")).put("max", a.getDouble("max") + b.getDouble("max")) } }
    private fun range(minValue: Double, maxValue: Double) = JSONObject().put("min", round1(minValue)).put("max", round1(max(maxValue, minValue)))
    private fun round1(value: Double) = round(value * 10) / 10
    private fun round5(value: Double) = round(value / 5) * 5
    private fun midpoint(value: JSONObject) = (value.getDouble("min") + value.getDouble("max")) / 2

    private fun progress(intake: JSONObject, target: JSONObject?, mode: String, hasMeals: Boolean): Pair<String, Double> {
        if (!hasMeals) return "unknown" to 0.0
        if (target == null || mode == "observe") return "good" to min(100.0, midpoint(intake))
        val value = midpoint(intake); val lower = target.getDouble("min"); val upper = target.getDouble("max")
        val state = if (mode == "maximum") { if (value <= upper) "good" else "high" } else if (value < lower) "low" else if (value > upper) "high" else "good"
        val percent = if (mode == "maximum") value / max(upper, 1.0) * 100 else value / max(lower, 1.0) * 100
        return state to percent.coerceIn(0.0, 100.0)
    }

    private fun score(total: JSONObject, targets: JSONObject?, hasMeals: Boolean): Int? {
        if (!hasMeals || targets == null) return null
        val weights = mapOf("caloriesKcal" to .30, "proteinG" to .30, "carbohydrateG" to .15, "fatG" to .15, "fiberG" to .10)
        var score = 0.0
        weights.forEach { (key, weight) ->
            val value = midpoint(total.getJSONObject(key)); val target = targets.getJSONObject(key); val lower = target.getDouble("min"); val upper = target.getDouble("max")
            val part = if (value in lower..upper) 100.0 else if (value < lower) value / max(lower, 1.0) * 100 else upper / max(value, 1.0) * 100
            score += part * weight
        }
        return score.toInt().coerceIn(0, 100)
    }

    fun bounds(localDate: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> =
        localDate.atStartOfDay(zone).toInstant().toEpochMilli() to localDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    fun monthBounds(month: YearMonth, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> = bounds(month.atDay(1), zone).first to bounds(month.plusMonths(1).atDay(1), zone).first
    fun localDate(time: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    fun mealType(time: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = Instant.ofEpochMilli(time).atZone(zone)
        val minutes = local.hour * 60 + local.minute
        return when (minutes) {
            in 5 * 60 until 10 * 60 + 30 -> "breakfast"
            in 10 * 60 + 30 until 14 * 60 + 30 -> "lunch"
            in 14 * 60 + 30 until 17 * 60 -> "snack"
            in 17 * 60 until 21 * 60 + 30 -> "dinner"
            else -> "late_night"
        }
    }
    private fun normalizeConfidence(value: String): String = value.takeIf { it in listOf("high", "medium", "low") } ?: "medium"
    private fun normalizeStrings(source: JSONArray?, limit: Int, maxLength: Int): JSONArray = JSONArray().also { output ->
        if (source != null) repeat(min(source.length(), limit)) { index ->
            source.optString(index).trim().take(maxLength).takeIf { it.isNotBlank() }?.let(output::put)
        }
    }
    private fun validateConsumedAt(time: Long) { require(time <= System.currentTimeMillis() + 60_000 && time >= System.currentTimeMillis() - 7 * 86_400_000L) { "实际进餐时间只能选择最近七天，且不能晚于当前时间" } }
}
