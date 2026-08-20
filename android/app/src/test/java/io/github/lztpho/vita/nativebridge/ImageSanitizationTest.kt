// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ImageSanitizationTest {
    @Test fun metadataSegmentsAreRemovedWithoutChangingImageSegments() {
        val app0 = jpegSegment(0xe0, byteArrayOf(0x11, 0x22))
        val app1 = jpegSegment(0xe1, "Exif\u0000\u0000private".toByteArray(Charsets.ISO_8859_1))
        val app13 = jpegSegment(0xed, byteArrayOf(0x33, 0x44))
        val comment = jpegSegment(0xfe, "private comment".toByteArray())
        val quantization = jpegSegment(0xdb, byteArrayOf(0x55, 0x66))
        val scan = byteArrayOf(0xff.toByte(), 0xda.toByte(), 0x00, 0x02, 0x01, 0x02, 0xff.toByte(), 0xd9.toByte())
        val input = byteArrayOf(0xff.toByte(), 0xd8.toByte()) + app0 + app1 + app13 + comment + quantization + scan

        val stripped = stripJpegMetadata(input)

        assertTrue(stripped.contentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte()) + app0 + quantization + scan))
        assertTrue(!String(stripped, Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
    }

    @Test fun allExifOrientationsAreAppliedAndMetadataIsRemoved() {
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
                // Robolectric's JPEG encoder writes a synthetic "0" sentinel; neither it nor a missing tag
                // carries the source orientation. A real Android encoder is covered by the device test.
                assertTrue(cleanExif.getAttribute(ExifInterface.TAG_ORIENTATION) in setOf(null, "0"))
                assertNull(cleanExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                assertNull(cleanExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE))
                assertTrue(!String(result.modelBytes, Charsets.ISO_8859_1).contains("Exif\u0000\u0000"))
                assertTrue(result.modelBytes.size <= ImageVault.MAX_UPLOAD_BYTES)
            } finally {
                result.modelBytes.fill(0)
                result.thumbnailBytes.fill(0)
            }
        }
    }

    @Test fun longestEdgeIsReducedToUploadLimit() {
        val result = sanitizeImageForUpload(jpegWithExif(4000, 10, ExifInterface.ORIENTATION_NORMAL))
        try {
            assertEquals(3200, max(result.width, result.height))
        } finally {
            result.modelBytes.fill(0)
            result.thumbnailBytes.fill(0)
        }
    }

    @Test fun forgedContainerHeaderIsRejectedWhenPixelsCannotBeDecoded() {
        val forged = ByteArray(64).also {
            "ftyp".toByteArray(Charsets.US_ASCII).copyInto(it, 4)
        }
        assertThrows(IllegalArgumentException::class.java) { sanitizeImageForUpload(forged) }
    }

    private fun jpegWithExif(width: Int, height: Int, orientation: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 120, 210))
        val file = File.createTempFile("vita-image-", ".jpg")
        try {
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
            }
            bitmap.recycle()
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

    private fun jpegSegment(marker: Int, payload: ByteArray): ByteArray {
        val length = payload.size + 2
        return byteArrayOf(
            0xff.toByte(),
            marker.toByte(),
            (length ushr 8).toByte(),
            length.toByte(),
        ) + payload
    }
}
