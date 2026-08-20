// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageOrientationTest {
    @Test fun allEightExifOrientationsHaveAnExplicitTransform() {
        val expected = mapOf(
            ExifInterface.ORIENTATION_NORMAL to OrientationSpec(),
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL to OrientationSpec(flipX = true),
            ExifInterface.ORIENTATION_ROTATE_180 to OrientationSpec(rotation = 180f),
            ExifInterface.ORIENTATION_FLIP_VERTICAL to OrientationSpec(flipY = true),
            ExifInterface.ORIENTATION_TRANSPOSE to OrientationSpec(rotation = 90f, flipX = true),
            ExifInterface.ORIENTATION_ROTATE_90 to OrientationSpec(rotation = 90f),
            ExifInterface.ORIENTATION_TRANSVERSE to OrientationSpec(rotation = 270f, flipX = true),
            ExifInterface.ORIENTATION_ROTATE_270 to OrientationSpec(rotation = 270f),
        )
        expected.forEach { (orientation, spec) -> assertEquals(spec, orientationSpec(orientation)) }
    }
}
