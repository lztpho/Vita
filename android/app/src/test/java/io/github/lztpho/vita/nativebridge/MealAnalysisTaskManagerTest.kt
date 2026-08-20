// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class MealAnalysisTaskManagerTest {
    @Test fun diagnosticPhaseRecordsWhetherNotesArrivedWithoutTheirContent() {
        assertEquals("queued_without_notes", MealAnalysisTaskManager.queuedDiagnosticPhase(""))
        assertEquals("queued_without_notes", MealAnalysisTaskManager.queuedDiagnosticPhase("   "))
        assertEquals("queued_with_notes", MealAnalysisTaskManager.queuedDiagnosticPhase("米饭半碗，鸡肉不吃皮"))
    }
}
