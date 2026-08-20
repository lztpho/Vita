// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test fun keyCanBeStagedWithAnEndpointBeforeTheProviderIsComplete() {
        val staged = ProviderBinding.stageKey(JSONObject(), "secret", "https://models.example/v1/", "anthropic")
        assertEquals("secret", ProviderBinding.keyFor(staged, "https://models.example/v1"))
        assertEquals("anthropic", staged.getJSONObject("provider").getString("protocol"))
        assertEquals("https://models.example/v1", staged.getJSONObject("provider").getString("baseUrl"))

        val completed = ProviderBinding.replaceProvider(staged, provider("https://models.example/v1"))
        assertEquals("secret", ProviderBinding.keyFor(completed, "https://models.example/v1"))
        assertEquals("", ProviderBinding.keyFor(completed, "https://other.example/v1"))
    }

    @Test fun stagingForANewEndpointCannotReuseThePreviousModelOrKey() {
        val first = ProviderBinding.bindKey(
            ProviderBinding.replaceProvider(JSONObject(), provider("https://one.example/v1")),
            "old-secret",
            "https://one.example/v1",
        )
        val staged = ProviderBinding.stageKey(first, "new-secret", "https://two.example/v1", "openai")

        assertFalse(staged.getJSONObject("provider").has("visionModel"))
        assertEquals("", ProviderBinding.keyFor(staged, "https://one.example/v1"))
        assertEquals("new-secret", ProviderBinding.keyFor(staged, "https://two.example/v1"))
    }
}
