// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCompatibilityTest {
    @Test
    fun `OpenRouter asks its model list for image input models`() {
        assertEquals(
            "https://openrouter.ai/api/v1/models?input_modalities=image&output_modalities=text",
            ProviderCompatibility.modelListUrl("https://openrouter.ai/api/v1"),
        )
    }

    @Test
    fun `custom model capability remains unknown without provider metadata`() {
        assertNull(ProviderCompatibility.inferredImageCapability("https://models.example.test/v1", "chat-model"))
        assertFalse(ProviderCompatibility.inferredImageCapability("https://models.example.test/v1", "embedding-model")!!)
    }

    @Test
    fun `domestic cloud presets only retain likely image-capable models`() {
        assertTrue(ProviderCompatibility.inferredImageCapability("https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-vision-1.5-instruct")!!)
        assertFalse(ProviderCompatibility.inferredImageCapability("https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbos-latest")!!)
        assertTrue(ProviderCompatibility.inferredImageCapability("https://open.bigmodel.cn/api/paas/v4", "glm-5v-turbo")!!)
        assertFalse(ProviderCompatibility.inferredImageCapability("https://open.bigmodel.cn/api/paas/v4", "glm-5.2")!!)
        assertTrue(ProviderCompatibility.inferredImageCapability("https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2-0-lite-260215")!!)
        assertFalse(ProviderCompatibility.inferredImageCapability("https://ark.cn-beijing.volces.com/api/v3", "doubao-embedding-large")!!)
    }
}
