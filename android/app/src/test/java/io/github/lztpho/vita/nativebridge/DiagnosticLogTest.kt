// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun `redacts credentials endpoints and local locations`() {
        val raw = "Bearer top.secret https://api.example.test/v1?token=abc content://photos/42 api_key=hidden sk-privatevalue"
        val redacted = DiagnosticLog.redact(raw)

        assertFalse(redacted.contains("top.secret"))
        assertFalse(redacted.contains("api.example.test"))
        assertFalse(redacted.contains("photos/42"))
        assertFalse(redacted.contains("hidden"))
        assertFalse(redacted.contains("privatevalue"))
    }

    @Test fun `exports bounded rotated logs and clears them`() {
        val directory = temporaryFolder.newFolder("diagnostics")
        val log = DiagnosticLog(directory) { mapOf("version" to "test", "endpoint" to "https://private.example.test/v1") }
        repeat(4_000) { log.record("meal_analysis", phase = "model", error = IllegalStateException("private meal description $it")) }

        val files = directory.listFiles().orEmpty()
        val exported = log.exportText()
        assertTrue(files.size <= 3)
        assertTrue(files.sumOf { it.length() } <= 3L * 128 * 1024)
        assertTrue(exported.contains("Vita diagnostic log"))
        assertFalse(exported.contains("private.example.test"))
        assertFalse(exported.contains("private meal description"))
        assertTrue(exported.contains("\"errorCategory\":\"operation\""))

        log.clear()
        assertTrue(directory.listFiles().isNullOrEmpty())
    }
}
