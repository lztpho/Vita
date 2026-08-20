// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderBindingTest {
    private fun provider(url: String) = JSONObject().put("baseUrl", url).put("protocol", "openai").put("visionModel", "vision")

    @Test fun keyCannotCrossEndpoints() {
        val configured = ProviderBinding.replaceProvider(JSONObject(), provider("https://api.example.com/v1"))
        val bound = ProviderBinding.bindKey(configured, "secret", "https://api.example.com/v1/")
        assertEquals("secret", ProviderBinding.keyFor(bound, "HTTPS://API.EXAMPLE.COM/v1"))
        assertEquals("", ProviderBinding.keyFor(bound, "https://api.example.com/V1"))
        assertEquals("", ProviderBinding.keyFor(bound, "https://other.example.com/v1"))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderBinding.bindKey(bound, "secret", "https://other.example.com/v1")
        }
    }

    @Test fun changingEndpointAtomicallyDropsTheOldKey() {
        val first = ProviderBinding.bindKey(
            ProviderBinding.replaceProvider(JSONObject(), provider("https://one.example/v1")),
            "secret",
            "https://one.example/v1",
        )
        val changed = ProviderBinding.replaceProvider(first, provider("https://two.example/v1"))
        assertEquals("", ProviderBinding.keyFor(changed, "https://one.example/v1"))
        assertEquals("", ProviderBinding.keyFor(changed, "https://two.example/v1"))
    }
}
