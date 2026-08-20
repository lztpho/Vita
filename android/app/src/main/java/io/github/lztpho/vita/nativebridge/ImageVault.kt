// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import kotlin.math.max

data class PreparedImage(
    val modelBase64: String,
    val mimeType: String,
    val encryptedThumbnail: ByteArray,
    val uploadBytes: Int,
)

internal data class OrientationSpec(val rotation: Float = 0f, val flipX: Boolean = false, val flipY: Boolean = false)

internal data class SanitizedImage(
    val modelBytes: ByteArray,
    val thumbnailBytes: ByteArray,
    val width: Int,
    val height: Int,
)

internal fun orientationSpec(orientation: Int): OrientationSpec = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> OrientationSpec(flipX = true)
    ExifInterface.ORIENTATION_ROTATE_180 -> OrientationSpec(rotation = 180f)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> OrientationSpec(flipY = true)
    ExifInterface.ORIENTATION_TRANSPOSE -> OrientationSpec(rotation = 90f, flipX = true)
    ExifInterface.ORIENTATION_ROTATE_90 -> OrientationSpec(rotation = 90f)
    ExifInterface.ORIENTATION_TRANSVERSE -> OrientationSpec(rotation = 270f, flipX = true)
    ExifInterface.ORIENTATION_ROTATE_270 -> OrientationSpec(rotation = 270f)
    else -> OrientationSpec()
}

internal fun sanitizeImageForUpload(bytes: ByteArray): SanitizedImage {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    requireSupportedImageFormat(bytes)
    val decoded = decodeSampledImage(bytes, 3200)
    val oriented = orientImage(decoded, orientation)
    if (oriented !== decoded) decoded.recycle()
    val resized = resizeImage(oriented, 3200)
    if (resized !== oriented) oriented.recycle()
    val flattened = flattenImageForJpeg(resized)
    if (flattened !== resized) resized.recycle()
    try {
        val encoded = compressImage(flattened, Bitmap.CompressFormat.JPEG, 90)
        val modelBytes = try {
            stripJpegMetadata(encoded)
        } finally {
            encoded.fill(0)
        }
        require(modelBytes.size <= ImageVault.MAX_UPLOAD_BYTES) { "图片重新编码后超过 8 MiB，请裁剪后重试" }
        val thumbnail = resizeImage(flattened, 768)
        val thumbnailFormat = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        val thumbnailBytes = try {
            compressImage(thumbnail, thumbnailFormat, 78)
        } finally {
            if (thumbnail !== flattened) thumbnail.recycle()
        }
        return SanitizedImage(modelBytes, thumbnailBytes, flattened.width, flattened.height)
    } finally {
        flattened.recycle()
    }
}

class ImageVault(private val context: Context, private val secureStore: SecureStore) {
    companion object {
        const val MAX_UPLOAD_BYTES = 8 * 1024 * 1024
        private const val MAX_SOURCE_BYTES = 32 * 1024 * 1024
    }

    private val root = File(context.filesDir, "meal-thumbnails-v1").apply { mkdirs() }

    fun prepare(image: JSONObject, draftId: String, index: Int): PreparedImage {
        require(draftId.isNotBlank()) { "草稿编号无效" }
        val bytes = readInput(image)
        require(bytes.size <= MAX_SOURCE_BYTES) { "图片源文件过大" }
        val sanitized = try {
            sanitizeImageForUpload(bytes)
        } finally {
            bytes.fill(0)
        }
        try {
            val aad = "draft:$draftId:$index"
            val sealed = secureStore.encryptBytes(sanitized.thumbnailBytes, aad)
            File(root, "draft-$draftId-$index.vimg").writeBytes(sealed)
            return PreparedImage(
                Base64.encodeToString(sanitized.modelBytes, Base64.NO_WRAP),
                "image/jpeg",
                sealed,
                sanitized.modelBytes.size,
            )
        } finally {
            sanitized.thumbnailBytes.fill(0)
            sanitized.modelBytes.fill(0)
        }
    }

    fun confirmDraft(draftId: String, mealId: String, count: Int) {
        require(draftId.isNotBlank() && mealId.isNotBlank()) { "餐食或草稿编号无效" }
        repeat(count) { index ->
            val source = File(root, "draft-$draftId-$index.vimg")
            if (!source.exists()) return@repeat
            val clear = secureStore.decryptBytes(source.readBytes(), "draft:$draftId:$index")
            File(root, "meal-$mealId-$index.vimg").writeBytes(secureStore.encryptBytes(clear, "meal:$mealId:$index"))
            clear.fill(0)
            source.delete()
        }
    }

    fun cancelDraft(draftId: String) {
        require(draftId.isNotBlank()) { "草稿编号无效" }
        root.listFiles { file -> file.name.startsWith("draft-$draftId-") }?.forEach { it.delete() }
    }

    fun deleteMeal(mealId: String) {
        require(mealId.isNotBlank()) { "餐食编号无效" }
        root.listFiles { file -> file.name.startsWith("meal-$mealId-") }?.forEach { it.delete() }
    }

    fun clearAll() {
        root.listFiles()?.forEach { file -> if (file.isFile) file.delete() }
        root.delete()
    }

    fun thumbnail(mealId: String, index: Int): String {
        require(mealId.isNotBlank() && index in 0..3) { "缩略图参数无效" }
        val file = File(root, "meal-$mealId-$index.vimg")
        require(file.exists()) { "缩略图已经不存在" }
        val clear = secureStore.decryptBytes(file.readBytes(), "meal:$mealId:$index")
        val result = "data:image/webp;base64,${Base64.encodeToString(clear, Base64.NO_WRAP)}"
        clear.fill(0)
        return result
    }

    private fun readInput(image: JSONObject): ByteArray {
        val dataUrl = image.optString("dataUrl")
        if (dataUrl.startsWith("data:")) {
            require(dataUrl.length <= MAX_SOURCE_BYTES * 2) { "图片编码过大" }
            val comma = dataUrl.indexOf(',')
            require(comma > 0 && dataUrl.substring(0, comma).contains(";base64")) { "图片编码无效" }
            return Base64.decode(dataUrl.substring(comma + 1), Base64.DEFAULT)
        }
        val raw = image.optString("uri").trim()
        require(raw.isNotBlank()) { "图片地址缺失" }
        val uri = Uri.parse(raw)
        return when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)?.use(::readLimited)
                ?: throw IllegalArgumentException("无法读取图片")
            "file" -> readAppOwnedFile(File(uri.path ?: throw IllegalArgumentException("图片路径无效")))
            null, "" -> readAppOwnedFile(File(raw))
            else -> throw IllegalArgumentException("只支持 content:// 或应用自身文件")
        }
    }

    private fun readAppOwnedFile(file: File): ByteArray {
        val canonical = file.canonicalFile
        val roots = buildList {
            add(context.filesDir)
            add(context.cacheDir)
            add(context.noBackupFilesDir)
            context.externalCacheDir?.let(::add)
            context.getExternalFilesDirs(null).filterNotNull().forEach(::add)
        }.map { it.canonicalFile }
        require(roots.any { canonical.toPath().startsWith(it.toPath()) }) { "只能读取应用自身目录中的图片文件" }
        require(canonical.isFile) { "图片文件不存在" }
        return canonical.inputStream().use(::readLimited)
    }

    private fun readLimited(stream: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_SOURCE_BYTES) { "图片源文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

}

private const val MAX_IMAGE_PIXELS = 80_000_000L

private fun decodeSampledImage(bytes: ByteArray, maxDimension: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= MAX_IMAGE_PIXELS) { "图片尺寸无效或过大" }
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }) ?: throw IllegalArgumentException("图片无法解码")
}

private fun requireSupportedImageFormat(bytes: ByteArray) {
    val jpeg = bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
    val png = bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
    val webp = bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
    val heic = bytes.size >= 12 &&
        String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" &&
        String(bytes, 8, 4, Charsets.US_ASCII) in setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1", "avif", "avis")
    require(jpeg || png || webp || heic) { "图片格式暂不支持" }
}

private fun orientImage(source: Bitmap, orientation: Int): Bitmap {
    val spec = orientationSpec(orientation)
    if (spec == OrientationSpec()) return source
    val matrix = Matrix().apply {
        if (spec.rotation != 0f) postRotate(spec.rotation)
        if (spec.flipX || spec.flipY) postScale(if (spec.flipX) -1f else 1f, if (spec.flipY) -1f else 1f)
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun resizeImage(source: Bitmap, longest: Int): Bitmap {
    val current = max(source.width, source.height)
    if (current <= longest) return source
    val scale = longest.toDouble() / current
    return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
}

private fun flattenImageForJpeg(source: Bitmap): Bitmap {
    if (!source.hasAlpha()) return source
    return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { output ->
        Canvas(output).apply { drawColor(Color.WHITE); drawBitmap(source, 0f, 0f, null) }
    }
}

private fun compressImage(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray = ByteArrayOutputStream().use { output ->
    require(bitmap.compress(format, quality, output)) { "图片压缩失败" }
    output.toByteArray()
}

/** Removes EXIF/XMP, Photoshop metadata, and JPEG comments while preserving encoded pixels. */
internal fun stripJpegMetadata(input: ByteArray): ByteArray {
    require(input.size >= 4 && input[0] == 0xff.toByte() && input[1] == 0xd8.toByte()) { "JPEG 编码无效" }
    val output = ByteArrayOutputStream(input.size)
    output.write(input, 0, 2)
    var offset = 2
    while (offset < input.size) {
        require(input[offset] == 0xff.toByte()) { "JPEG 段结构无效" }
        val segmentStart = offset
        while (offset < input.size && input[offset] == 0xff.toByte()) offset += 1
        require(offset < input.size) { "JPEG 段结构无效" }
        val marker = input[offset].toInt() and 0xff
        offset += 1
        if (marker == 0xda) {
            output.write(input, segmentStart, input.size - segmentStart)
            return output.toByteArray()
        }
        if (marker == 0xd9) {
            output.write(input, segmentStart, offset - segmentStart)
            return output.toByteArray()
        }
        if (marker == 0x01 || marker in 0xd0..0xd7) {
            output.write(input, segmentStart, offset - segmentStart)
            continue
        }
        require(offset + 1 < input.size) { "JPEG 段长度无效" }
        val length = ((input[offset].toInt() and 0xff) shl 8) or (input[offset + 1].toInt() and 0xff)
        require(length >= 2 && offset + length <= input.size) { "JPEG 段长度无效" }
        if (marker !in setOf(0xe1, 0xed, 0xfe)) {
            output.write(input, segmentStart, offset + length - segmentStart)
        }
        offset += length
    }
    throw IllegalArgumentException("JPEG 缺少图像数据")
}
