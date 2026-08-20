// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita

import android.os.Environment
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraFileProviderInstrumentedTest {
    @Test fun cameraPicturesDirectoryIsAvailableAndOtherDirectoriesAreRejected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val authority = "${context.packageName}.fileprovider"
        val picturesDirectory = requireNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES))

        val cameraUri = FileProvider.getUriForFile(context, authority, picturesDirectory.resolve("photo.jpg"))
        assertEquals("content", cameraUri.scheme)

        assertThrows(IllegalArgumentException::class.java) {
            FileProvider.getUriForFile(context, authority, context.filesDir.resolve("private.txt"))
        }
    }
}
