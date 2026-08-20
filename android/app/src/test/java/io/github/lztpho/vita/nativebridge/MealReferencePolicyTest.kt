// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MealReferencePolicyTest {
    private fun meal(id: String, source: String? = null, thumbnails: Int = 0) = MealEntity(
        id, 0, "lunch", "historical_reuse", source, 1, 1, "{}", thumbnails,
        if (thumbnails == 0) 1 else 0, 0,
    )

    @Test fun multiLevelReuseResolvesToTheRootWithRealThumbnails() {
        val root = meal("root", thumbnails = 2)
        val second = meal("second", source = "root")
        val third = meal("third", source = "second")
        val all = listOf(root, second, third).associateBy { it.id }
        assertEquals("root", MealReferencePolicy.resolveImageSource(third, all::get).id)
    }

    @Test fun missingAndCyclicReferencesFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            MealReferencePolicy.resolveImageSource(meal("missing", source = "gone")) { null }
        }
        val first = meal("first", source = "second")
        val second = meal("second", source = "first")
        val all = listOf(first, second).associateBy { it.id }
        assertThrows(IllegalArgumentException::class.java) {
            MealReferencePolicy.resolveImageSource(first, all::get)
        }
    }
}
