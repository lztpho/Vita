// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

class NutritionEngineTest {
    private val zone = ZoneId.of("UTC")

    private fun timestamp(hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2024, 1, 15, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun classifiesMealWindows() {
        assertEquals("breakfast", NutritionEngine.mealType(timestamp(8), zone))
        assertEquals("lunch", NutritionEngine.mealType(timestamp(12), zone))
        assertEquals("snack", NutritionEngine.mealType(timestamp(15), zone))
        assertEquals("dinner", NutritionEngine.mealType(timestamp(20), zone))
        assertEquals("late_night", NutritionEngine.mealType(timestamp(23, 49), zone))
        assertEquals("late_night", NutritionEngine.mealType(timestamp(3), zone))
    }

    private fun profile(
        birthDate: String = "1996-01-01",
        weightKg: Double = 65.0,
        heightCm: Double = 170.0,
        equationSex: String = "female",
        activityLevel: String = "light",
        goalType: String = "maintenance",
        eligible: Boolean = true,
    ) = JSONObject()
        .put("birthDate", birthDate).put("weightKg", weightKg).put("heightCm", heightCm)
        .put("equationSex", equationSex).put("activityLevel", activityLevel)
        .put("goalType", goalType).put("generalHealthEligible", eligible)

    @Test fun goalEligibilityIsLimitedToHealthyAdults() {
        val today = LocalDate.of(2026, 1, 1)
        NutritionEngine.proposal(profile(birthDate = "2008-01-01"), today)
        assertThrows(IllegalArgumentException::class.java) { NutritionEngine.proposal(profile(birthDate = "2008-01-02"), today) }
        assertThrows(IllegalArgumentException::class.java) { NutritionEngine.proposal(profile(birthDate = "1961-01-01"), today) }
        assertThrows(IllegalArgumentException::class.java) { NutritionEngine.proposal(profile(eligible = false), today) }
        assertThrows(IllegalArgumentException::class.java) { NutritionEngine.proposal(profile(weightKg = 45.0, heightCm = 180.0), today) }
    }

    @Test fun calorieLimitsRefuseInsteadOfClamp() {
        val today = LocalDate.of(2026, 1, 1)
        assertThrows(IllegalArgumentException::class.java) {
            NutritionEngine.proposal(profile(birthDate = "1962-01-01", weightKg = 48.0, heightCm = 160.0, activityLevel = "sedentary"), today)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NutritionEngine.proposal(profile(birthDate = "2000-01-01", weightKg = 180.0, heightCm = 230.0, equationSex = "male", activityLevel = "high", goalType = "muscle_gain"), today)
        }
    }

    @Test fun goalFactorsStayWithinChosenBounds() {
        val today = LocalDate.of(2026, 1, 1)
        val maintenance = NutritionEngine.proposal(profile(equationSex = "male"), today).getJSONObject("targets").getJSONObject("caloriesKcal")
        val loss = NutritionEngine.proposal(profile(equationSex = "male", goalType = "fat_loss"), today).getJSONObject("targets").getJSONObject("caloriesKcal")
        val gain = NutritionEngine.proposal(profile(equationSex = "male", goalType = "muscle_gain"), today).getJSONObject("targets").getJSONObject("caloriesKcal")
        assertTrue(loss.getDouble("min") >= maintenance.getDouble("min") * 0.89)
        assertTrue(gain.getDouble("max") <= maintenance.getDouble("max") * 1.06)
    }

    @Test fun packagedFoodPromptTreatsFrontAndLabelAsOneItem() {
        val prompt = NutritionEngine.analysisPrompt(timestamp(9, 10), "")
        assertTrue(prompt.contains("正面、配料表和营养成分表属于同一件食物"))
        assertTrue(prompt.contains("同一包装不得跨照片重复计算"))
        assertTrue(prompt.contains("每100克营养值"))
    }

    @Test fun invalidMealResponseExplainsModelCapabilityInsteadOfItemCount() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NutritionEngine.normalizeAnalysis(JSONObject().put("items", JSONArray()), "draft", System.currentTimeMillis(), "", 1)
        }
        assertTrue(error.message.orEmpty().contains("可能不支持图片输入"))
        assertTrue(error.message.orEmpty().contains("结构化 JSON"))
        assertTrue(!error.message.orEmpty().contains("食物数量无效"))
    }
}
