// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class ImageSanitizationInstrumentedTest {
    @Test fun reencodingAppliesAllOrientationsAndStripsGpsExif() {
        (ExifInterface.ORIENTATION_NORMAL..ExifInterface.ORIENTATION_ROTATE_270).forEach { orientation ->
            val input = jpegWithExif(12, 8, orientation)
            val result = sanitizeImageForUpload(input)
            try {
                val decoded = BitmapFactory.decodeByteArray(result.modelBytes, 0, result.modelBytes.size)
                val swapsAxes = orientation in setOf(
                    ExifInterface.ORIENTATION_TRANSPOSE,
                    ExifInterface.ORIENTATION_ROTATE_90,
                    ExifInterface.ORIENTATION_TRANSVERSE,
                    ExifInterface.ORIENTATION_ROTATE_270,
                )
                assertEquals(if (swapsAxes) 8 else 12, decoded.width)
                assertEquals(if (swapsAxes) 12 else 8, decoded.height)
                decoded.recycle()

                val cleanExif = ExifInterface(ByteArrayInputStream(result.modelBytes))
                assertTrue(cleanExif.getAttribute(ExifInterface.TAG_ORIENTATION) in setOf(null, "0"))
                assertNull(cleanExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                assertNull(cleanExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
                assertTrue(!String(result.modelBytes, Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
            } finally {
                result.modelBytes.fill(0)
                result.thumbnailBytes.fill(0)
            }
        }
    }

    private fun jpegWithExif(width: Int, height: Int, orientation: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 120, 210))
        val file = File.createTempFile("vita-image-", ".jpg")
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,48/1,3000/100")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "122/1,24/1,1500/100")
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
