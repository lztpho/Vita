// SPDX-License-Identifier: Apache-2.0
package io.github.lztpho.vita.nativebridge

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ModelClient(private val secureStore: SecureStore) {
    companion object {
        private const val MAX_PROMPT_CHARS = 24_000
        private const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L
    }
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .dns(NetworkPolicy.publicDns())
        .build()

    fun sanitizedProvider(): JSONObject {
        val source = secureStore.provider()
        return JSONObject()
            .put("protocol", source.optString("protocol").ifBlank { "openai" })
            .put("baseUrl", source.optString("baseUrl"))
            .put("visionModel", source.optString("visionModel"))
            .put("textModel", source.optString("textModel"))
            .put("hasApiKey", secureStore.hasApiKey(source.optString("baseUrl")))
            .put("configured", source.optString("baseUrl").isNotBlank() && source.optString("visionModel").isNotBlank())
    }

    fun configure(input: JSONObject): JSONObject {
        val protocol = input.optString("protocol", "openai")
        require(protocol == "openai" || protocol == "anthropic") { "不支持的模型协议" }
        val base = NetworkPolicy.validateForRequest(input.getString("baseUrl"))
        val vision = input.optString("visionModel").trim()
        require(vision.isNotEmpty()) { "请填写视觉模型" }
        ProviderCompatibility.validateSelectedModel(base.toString(), vision)
        val saved = JSONObject()
            .put("protocol", protocol)
            .put("baseUrl", base.toString().trimEnd('/'))
            .put("visionModel", vision.take(160))
            .put("textModel", input.optString("textModel").trim().take(160))
        secureStore.saveProvider(saved)
        return sanitizedProvider()
    }

    fun analyze(prompt: String, images: List<PreparedImage>, onUploadProgress: ((Int) -> Unit)? = null): JSONObject {
        require(images.isNotEmpty()) { "至少需要一张图片" }
        return extractJson(complete(prompt, images, useTextModel = false, structured = true, onUploadProgress))
    }

    fun structuredText(prompt: String): JSONObject = extractJson(complete(prompt, emptyList(), useTextModel = true, structured = true))

    fun text(prompt: String): String = complete(prompt, emptyList(), useTextModel = true, structured = false)

    fun test(): JSONObject {
        val started = System.currentTimeMillis()
        val provider = secureStore.provider()
        val selectedModel = provider.optString("visionModel").trim()
        val listedAttempt = runCatching {
            listModels(provider.optString("protocol", "openai"), provider.getString("baseUrl"), timeoutMs = 8_000).optJSONArray("models")
        }
        val listedModels = listedAttempt.getOrNull()
        val selectedModelListed = listedModels != null && (0 until listedModels.length()).any { index ->
            listedModels.optJSONObject(index)?.optString("id")?.equals(selectedModel, ignoreCase = true) == true
        }
        if (selectedModelListed) {
            return successfulTestResult(started, "API 连接正常，已找到所选模型")
        }
        val failure = listedAttempt.exceptionOrNull()
        if (failure is ModelHttpException && failure.statusCode in listOf(401, 403)) throw failure
        return inconclusiveTestResult(started)
    }

    private fun successfulTestResult(started: Long, detail: String): JSONObject {
        return JSONObject()
            .put("usable", true)
            .put("text", true)
            .put("vision", JSONObject.NULL)
            .put("structured", JSONObject.NULL)
            .put("latencyMs", System.currentTimeMillis() - started)
            .put("detail", detail)
    }

    private fun inconclusiveTestResult(started: Long): JSONObject = JSONObject()
        .put("usable", false)
        .put("verified", false)
        .put("text", JSONObject.NULL)
        .put("vision", JSONObject.NULL)
        .put("structured", JSONObject.NULL)
        .put("latencyMs", System.currentTimeMillis() - started)
        .put("detail", "测试接口未及时返回；不代表模型不可用，可直接用拍餐验证")

    fun listModels(protocol: String, rawBaseUrl: String, timeoutMs: Long? = null): JSONObject {
        require(protocol == "openai" || protocol == "anthropic") { "不支持的模型协议" }
        val baseUrl = NetworkPolicy.validate(rawBaseUrl).toString().trimEnd('/')
        val key = secureStore.apiKey(baseUrl)
        require(key.isNotBlank()) { "请先填写 API Key" }
        val listUrl = ProviderCompatibility.modelListUrl(baseUrl)
        val headers = if (protocol == "anthropic") {
            mapOf("x-api-key" to key.takeIf { it.isNotBlank() }, "anthropic-version" to "2023-06-01")
        } else {
            mapOf("Authorization" to key.takeIf { it.isNotBlank() }?.let { "Bearer $it" })
        }
        val root = JSONObject(get(listUrl, headers, timeoutMs))
        val data = root.optJSONArray("data") ?: root.optJSONArray("models") ?: root.optJSONArray("Models") ?: JSONArray()
        val output = mutableListOf<JSONObject>()
        var capabilityKnown = false
        for (index in 0 until data.length()) {
            val source = when (val value = data.opt(index)) {
                is JSONObject -> value
                is String -> JSONObject().put("id", value)
                else -> continue
            }
            val id = source.optString("id").ifBlank { source.optString("name") }.trim()
            if (id.isBlank()) continue
            val modalities = inputModalities(source)
            if (modalities.isNotEmpty()) capabilityKnown = true
            if (modalities.isNotEmpty() && modalities.none { it.equals("image", ignoreCase = true) }) continue
            if (modalities.isEmpty() && !ProviderCompatibility.likelyMultimodal(baseUrl, id)) continue
            output += JSONObject().put("id", id).put("name", source.optString("display_name").ifBlank { source.optString("name") }.ifBlank { id })
                .also { if (modalities.isNotEmpty()) it.put("supportsImage", true) }
        }
        val unique = output.distinctBy { it.getString("id") }.sortedBy { it.getString("id").lowercase() }.take(240)
        return JSONObject()
            .put("models", JSONArray().also { array -> unique.forEach(array::put) })
            .put("capabilityKnown", capabilityKnown)
    }

    private fun complete(
        prompt: String,
        images: List<PreparedImage>,
        useTextModel: Boolean,
        structured: Boolean,
        onUploadProgress: ((Int) -> Unit)? = null,
        maxOutputTokens: Int = 4096,
    ): String {
        val provider = secureStore.provider()
        require(provider.optString("baseUrl").isNotBlank()) { "请先配置模型服务" }
        require(prompt.length <= MAX_PROMPT_CHARS) { "发送给模型的内容过长" }
        require(secureStore.apiKey(provider.getString("baseUrl")).isNotBlank()) { "请先填写 API Key" }
        return when (provider.optString("protocol", "openai")) {
            "anthropic" -> anthropic(provider, prompt, images, useTextModel, onUploadProgress, maxOutputTokens)
            else -> openAi(provider, prompt, images, useTextModel, structured, onUploadProgress, maxOutputTokens)
        }
    }

    private fun openAi(
        provider: JSONObject,
        prompt: String,
        images: List<PreparedImage>,
        useTextModel: Boolean,
        structured: Boolean,
        onUploadProgress: ((Int) -> Unit)?,
        maxOutputTokens: Int,
    ): String {
        val model = model(provider, useTextModel)
        val content: Any = if (images.isEmpty()) prompt else JSONArray().apply {
            put(JSONObject().put("type", "text").put("text", prompt))
            images.forEach { image -> put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:${image.mimeType};base64,${image.modelBase64}"))) }
        }
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val isMiniMax = provider.getString("baseUrl").contains("api.minimaxi.com", ignoreCase = true)
        if (isMiniMax) {
            if (model.equals("MiniMax-M3", ignoreCase = true)) {
                body.put("thinking", JSONObject().put("type", "disabled"))
                body.put("max_completion_tokens", maxOutputTokens)
            }
        } else if (structured) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
        val endpoint = endpoint(provider.getString("baseUrl"), "chat/completions")
        val response = post(endpoint, body, mapOf("Authorization" to secureStore.apiKey(provider.getString("baseUrl")).takeIf { it.isNotBlank() }?.let { "Bearer $it" }), onUploadProgress)
        val root = JSONObject(response)
        return root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?.takeIf { it.isNotBlank() } ?: throw IllegalStateException("模型没有返回内容")
    }

    private fun anthropic(
        provider: JSONObject,
        prompt: String,
        images: List<PreparedImage>,
        useTextModel: Boolean,
        onUploadProgress: ((Int) -> Unit)?,
        maxOutputTokens: Int,
    ): String {
        val blocks = JSONArray()
        images.forEach { image -> blocks.put(JSONObject().put("type", "image").put("source", JSONObject().put("type", "base64").put("media_type", image.mimeType).put("data", image.modelBase64))) }
        blocks.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", model(provider, useTextModel))
            .put("max_tokens", maxOutputTokens)
            .put("temperature", 0.1)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", blocks)))
        val endpoint = endpoint(provider.getString("baseUrl"), "messages")
        val response = post(endpoint, body, mapOf(
            "x-api-key" to secureStore.apiKey(provider.getString("baseUrl")).takeIf { it.isNotBlank() },
            "anthropic-version" to "2023-06-01",
        ), onUploadProgress)
        val content = JSONObject(response).optJSONArray("content") ?: throw IllegalStateException("模型没有返回内容")
        return (0 until content.length()).mapNotNull { content.optJSONObject(it)?.takeIf { block -> block.optString("type") == "text" }?.optString("text") }.joinToString("\n")
    }

    private fun model(provider: JSONObject, useTextModel: Boolean): String {
        val text = provider.optString("textModel").trim()
        return if (useTextModel && text.isNotEmpty()) text else provider.getString("visionModel")
    }

    private fun endpoint(base: String, suffix: String): String {
        val clean = base.trimEnd('/')
        return if (clean.endsWith("/$suffix")) clean else "$clean/$suffix"
    }

    private fun inputModalities(model: JSONObject): List<String> {
        val arrays = listOfNotNull(
            model.optJSONArray("input_modalities"),
            model.optJSONArray("inputModalities"),
            model.optJSONObject("architecture")?.optJSONArray("input_modalities"),
            model.optJSONObject("capabilities")?.optJSONArray("input_modalities"),
        )
        return arrays.flatMap { array -> (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) } }
    }

    private fun get(url: String, headers: Map<String, String?>, timeoutMs: Long? = null): String {
        val current = NetworkPolicy.validateForRequest(url)
        val builder = Request.Builder().url(current).get().header("Accept", "application/json")
        headers.forEach { (name, value) -> if (!value.isNullOrBlank()) builder.header(name, value) }
        val requestClient = client.newBuilder().also { if (timeoutMs != null) it.callTimeout(timeoutMs, TimeUnit.MILLISECONDS) }.build()
        requestClient.newCall(builder.build()).execute().use { response ->
            HttpResponsePolicy.rejectRedirect(response.code)
            val text = readLimited(response.body)
            if (!response.isSuccessful) {
                if (response.code == 404) throw IllegalStateException("该接口未提供模型列表，请手动填写模型 ID")
                val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw ModelHttpException(response.code, message?.takeIf { it.isNotBlank() } ?: "获取模型失败（HTTP ${response.code}）")
            }
            return text
        }
    }

    private class ModelHttpException(val statusCode: Int, message: String) : IllegalStateException(message)

    private fun post(url: String, body: JSONObject, headers: Map<String, String?>, onUploadProgress: ((Int) -> Unit)? = null): String {
        val current = NetworkPolicy.validateForRequest(url)
        val sourceBody = body.toString().toRequestBody(jsonType)
        val requestBody = if (onUploadProgress == null) sourceBody else ProgressRequestBody(sourceBody, onUploadProgress)
        val builder = Request.Builder().url(current).post(requestBody).header("Accept", "application/json")
        headers.forEach { (name, value) -> if (!value.isNullOrBlank()) builder.header(name, value) }
        client.newCall(builder.build()).execute().use { response ->
            HttpResponsePolicy.rejectRedirect(response.code)
            val text = readLimited(response.body)
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(text).optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(message?.takeIf { it.isNotBlank() } ?: "模型服务请求失败（HTTP ${response.code}）")
            }
            return text
        }
    }

    private fun readLimited(body: okhttp3.ResponseBody?): String {
        if (body == null) return ""
        val length = body.contentLength()
        require(length < 0 || length <= MAX_RESPONSE_BYTES) { "模型响应超过 2 MiB 限制" }
        val source = body.source()
        require(!source.request(MAX_RESPONSE_BYTES + 1)) { "模型响应超过 2 MiB 限制" }
        return source.readUtf8()
    }

    private fun extractJson(text: String): JSONObject {
        val clean = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = clean.indexOf('{')
        val end = clean.lastIndexOf('}')
        require(start >= 0 && end > start) { "模型没有返回有效 JSON" }
        return JSONObject(clean.substring(start, end + 1))
    }

    private class ProgressRequestBody(
        private val delegate: RequestBody,
        private val onProgress: (Int) -> Unit,
    ) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()

        override fun writeTo(sink: BufferedSink) {
            val total = contentLength().coerceAtLeast(1L)
            var written = 0L
            val counting = object : ForwardingSink(sink) {
                override fun write(source: okio.Buffer, byteCount: Long) {
                    super.write(source, byteCount)
                    written += byteCount
                    onProgress(((written * 100L) / total).toInt().coerceIn(0, 100))
                }
            }
            val buffered = counting.buffer()
            delegate.writeTo(buffered)
            buffered.flush()
        }
    }
}

internal object ProviderCompatibility {
    private val excludedModelFragments = listOf(
        "embedding", "rerank", "moderation", "whisper", "transcrib", "tts", "speech",
        "realtime", "-live", "gpt-image", "dall-e", "imagen", "veo", "flux",
        "stable-diffusion", "cogview", "kolors", "text-embedding",
    )

    fun modelListUrl(baseUrl: String): String {
        val clean = baseUrl.trimEnd('/')
        return when {
            clean.contains("api.longcat.chat", ignoreCase = true) -> "https://api.longcat.chat/v1/models"
            clean.contains("openrouter.ai", ignoreCase = true) -> "$clean/models?input_modalities=image&output_modalities=text"
            else -> "$clean/models"
        }
    }

    fun validateSelectedModel(baseUrl: String, modelId: String) {
        val base = baseUrl.lowercase()
        if (base.contains("api.longcat.chat")) {
            require(likelyMultimodal(baseUrl, modelId)) {
                "LongCat 当前官方云 API 仅开放纯文本模型；请选择官方后续开放的 vision/omni 模型"
            }
        }
    }

    fun likelyMultimodal(baseUrl: String, modelId: String): Boolean {
        val base = baseUrl.lowercase()
        val id = modelId.lowercase()
        if (excludedModelFragments.any(id::contains)) return false
        return when {
            base.contains("api.openai.com") -> id.startsWith("gpt-") || Regex("^o[1-9]").containsMatchIn(id)
            base.contains("api.anthropic.com") -> id.startsWith("claude-")
            base.contains("generativelanguage.googleapis.com") -> id.startsWith("gemini-")
            base.contains("api.longcat.chat") -> listOf("vision", "omni", "multimodal").any(id::contains)
            base.contains("dashscope.aliyuncs.com") -> id.contains("-vl") || id.contains("omni")
            base.contains("api.hunyuan.cloud.tencent.com") -> id.contains("vision")
            base.contains("open.bigmodel.cn") -> Regex("^glm-[0-9.]+v(?:-|$)").containsMatchIn(id) || id.contains("vision")
            base.contains("ark.cn-beijing.volces.com") -> id.startsWith("doubao-seed-") || id.contains("vision")
            base.contains("api.minimaxi.com") -> id.startsWith("minimax-m3")
            base.contains("api.siliconflow.cn") -> listOf("-vl", "vision", "omni", "mllm", "glm-4.5v", "ocr").any(id::contains)
            else -> true
        }
    }
}

internal object HttpResponsePolicy {
    fun rejectRedirect(statusCode: Int) {
        require(statusCode !in 300..399) { "模型服务不允许重定向，请填写最终 HTTPS 地址" }
    }
}
