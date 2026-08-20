// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderCompatibilityTest {
    @Test
    fun `LongCat uses its documented model-list endpoint`() {
        assertEquals(
            "https://api.longcat.chat/v1/models",
            ProviderCompatibility.modelListUrl("https://api.longcat.chat/openai/v1"),
        )
    }

    @Test
    fun `LongCat text-only cloud model is not presented as image capable`() {
        assertFalse(ProviderCompatibility.likelyMultimodal("https://api.longcat.chat/openai/v1", "LongCat-2.0"))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCompatibility.validateSelectedModel("https://api.longcat.chat/openai/v1", "LongCat-2.0")
        }
        assertTrue(ProviderCompatibility.likelyMultimodal("https://api.longcat.chat/openai/v1", "LongCat-Flash-Omni"))
    }

    @Test
    fun `domestic cloud presets only retain likely image-capable models`() {
        assertTrue(ProviderCompatibility.likelyMultimodal("https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-vision-1.5-instruct"))
        assertFalse(ProviderCompatibility.likelyMultimodal("https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbos-latest"))
        assertTrue(ProviderCompatibility.likelyMultimodal("https://open.bigmodel.cn/api/paas/v4", "glm-5v-turbo"))
        assertFalse(ProviderCompatibility.likelyMultimodal("https://open.bigmodel.cn/api/paas/v4", "glm-5.2"))
        assertTrue(ProviderCompatibility.likelyMultimodal("https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-2-0-lite-260215"))
        assertFalse(ProviderCompatibility.likelyMultimodal("https://ark.cn-beijing.volces.com/api/v3", "doubao-embedding-large"))
    }
}
