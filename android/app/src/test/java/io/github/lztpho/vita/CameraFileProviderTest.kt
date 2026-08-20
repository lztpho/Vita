// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita

import android.content.pm.PackageManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class CameraFileProviderTest {
    @Test fun `camera provider declares only the app pictures directory`() {
        val context = RuntimeEnvironment.getApplication()
        val authority = "${context.packageName}.fileprovider"
        val provider = context.packageManager.resolveContentProvider(authority, PackageManager.GET_META_DATA)

        assertNotNull(provider)
        assertFalse(provider!!.exported)
        assertEquals("androidx.core.content.FileProvider", provider.name)

        val parser = provider.loadXmlMetaData(context.packageManager, "android.support.FILE_PROVIDER_PATHS")
        while (parser.eventType != XmlPullParser.START_TAG) {
            parser.next()
        }
        parser.nextTag()
        assertEquals("external-files-path", parser.name)
        assertEquals("camera_images", parser.getAttributeValue(null, "name"))
        assertEquals("Pictures/", parser.getAttributeValue(null, "path"))
        assertEquals(XmlPullParser.END_TAG, parser.nextTag())
    }
}
