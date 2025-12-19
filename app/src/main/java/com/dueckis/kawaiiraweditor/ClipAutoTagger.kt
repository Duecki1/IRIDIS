package com.dueckis.kawaiiraweditor

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ClipAutoTagger(appContext: Context) {
    private val context = appContext.applicationContext

    private val lock = Any()
    @Volatile private var env: OrtEnvironment? = null
    @Volatile private var session: OrtSession? = null
    @Volatile private var cachedCandidates: List<String>? = null
    @Volatile private var cachedTokenIds: LongArray? = null
    @Volatile private var cachedAttentionMask: LongArray? = null
    private val modelMutex = Mutex()
    private val tokenizerMutex = Mutex()

    suspend fun generateTags(
        previewBitmap: Bitmap,
        onProgress: ((Float) -> Unit)? = null
    ): List<String> = withContext(Dispatchers.Default) {
        var lastProgress = 0f
        fun setProgress(p: Float) {
            val clamped = p.coerceIn(0f, 1f)
            if (clamped <= lastProgress) return
            lastProgress = clamped
            onProgress?.invoke(clamped)
        }

        setProgress(0.02f)
        val candidates = ensureCandidates()
        setProgress(0.06f)

        val ortSession = ensureSession(
            onProgress = { downloadProgress -> setProgress(0.06f + 0.42f * downloadProgress.coerceIn(0f, 1f)) }
        )
        setProgress(0.50f)

        val (tokenIds, attentionMask) = ensureTokenizedCandidates(
            candidates,
            onProgress = { p -> setProgress(0.50f + 0.22f * p.coerceIn(0f, 1f)) }
        )
        setProgress(0.74f)

        val imageInput = preprocessClipImage(previewBitmap)
        setProgress(0.80f)
        val seqLen = CLIP_MAX_TOKENS

        val inputNames = ortSession.inputNames.toList()
        val idsName =
            inputNames.firstOrNull { it.contains("input", ignoreCase = true) && it.contains("id", ignoreCase = true) }
                ?: inputNames.firstOrNull { it.contains("id", ignoreCase = true) }
                ?: inputNames.first()
        val maskName =
            inputNames.firstOrNull { it.contains("mask", ignoreCase = true) || it.contains("attention", ignoreCase = true) }
                ?: inputNames.firstOrNull { it.contains("attn", ignoreCase = true) }
                ?: inputNames.last()
        val imageName =
            inputNames.firstOrNull { it.contains("pixel", ignoreCase = true) || it.contains("image", ignoreCase = true) }
                ?: inputNames.firstOrNull { it != idsName && it != maskName }
                ?: inputNames.first()

        val idsTensor = OnnxTensor.createTensor(env(), LongBuffer.wrap(tokenIds), longArrayOf(candidates.size.toLong(), seqLen.toLong()))
        val maskTensor = OnnxTensor.createTensor(env(), LongBuffer.wrap(attentionMask), longArrayOf(candidates.size.toLong(), seqLen.toLong()))
        val imageTensor = OnnxTensor.createTensor(env(), FloatBuffer.wrap(imageInput), longArrayOf(1L, 3L, 224L, 224L))

        coroutineScope {
            val fakeAdvanceJob = launch {
                val start = SystemClock.elapsedRealtime()
                val from = lastProgress.coerceAtLeast(0.80f)
                val to = 0.975f
                while (isActive) {
                    val tSec = (SystemClock.elapsedRealtime() - start).toFloat() / 1000f
                    val frac = 1f - exp(-tSec / 2.2f)
                    setProgress(from + (to - from) * frac)
                    delay(120)
                }
            }

            try {
                idsTensor.use { ids ->
                    maskTensor.use { mask ->
                        imageTensor.use { image ->
                            val inputs = mapOf(
                                idsName to ids,
                                imageName to image,
                                maskName to mask
                            )
                            ortSession.run(inputs).use { results ->
                                val output = results[0] as OnnxTensor
                                val fb = output.floatBuffer
                                val logits = FloatArray(fb.remaining()).also { fb.get(it) }
                                val probs = softmax(logits)
                                setProgress(0.985f)

                                val scored = mutableListOf<Pair<String, Float>>()
                                for (i in candidates.indices) {
                                    val p = probs.getOrNull(i) ?: continue
                                    if (p > 0.005f) scored += candidates[i] to p
                                }
                                scored.sortByDescending { it.second }

                                val initial = scored.take(10).map { it.first }
                                val out = LinkedHashSet<String>()
                                initial.forEach(out::add)
                                extractColorTags(previewBitmap).forEach(out::add)
                                initial.forEach { tag ->
                                    tagHierarchy()[tag]?.forEach(out::add)
                                }
                                setProgress(1f)
                                out.toList()
                            }
                        }
                    }
                }
            } finally {
                fakeAdvanceJob.cancel()
            }
        }
    }

    private fun env(): OrtEnvironment = env ?: synchronized(lock) {
        env ?: OrtEnvironment.getEnvironment().also { env = it }
    }

    private suspend fun ensureSession(onProgress: ((Float) -> Unit)? = null): OrtSession = withContext(Dispatchers.IO) {
        val existing = synchronized(lock) { session }
        if (existing != null) return@withContext existing

        val modelFile = ensureModelOnDisk(onProgress = onProgress)
        val created = env().createSession(modelFile.absolutePath, OrtSession.SessionOptions())
        synchronized(lock) {
            val race = session
            if (race != null) {
                created.close()
                return@withContext race
            }
            session = created
            created
        }
    }

    private suspend fun ensureCandidates(): List<String> = withContext(Dispatchers.IO) {
        cachedCandidates ?: run {
            val list = context.assets.open(CANDIDATES_ASSET_PATH).bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotBlank() }.toList()
            }
            cachedCandidates = list
            list
        }
    }

    private suspend fun ensureTokenizedCandidates(
        candidates: List<String>,
        onProgress: ((Float) -> Unit)? = null
    ): Pair<LongArray, LongArray> =
        withContext(Dispatchers.IO) {
            val idsExisting = cachedTokenIds
            val maskExisting = cachedAttentionMask
            if (idsExisting != null && maskExisting != null) return@withContext idsExisting to maskExisting

            val tokenizer = ensureTokenizer(onProgress = onProgress)
            val count = candidates.size
            val ids = LongArray(count * CLIP_MAX_TOKENS)
            val mask = LongArray(count * CLIP_MAX_TOKENS)
            for ((idx, text) in candidates.withIndex()) {
                val encoding = tokenizer.encode(text, CLIP_MAX_TOKENS)
                val base = idx * CLIP_MAX_TOKENS
                for (i in 0 until CLIP_MAX_TOKENS) {
                    ids[base + i] = encoding.ids[i].toLong()
                    mask[base + i] = encoding.attentionMask[i].toLong()
                }
                if (idx % 20 == 0) {
                    onProgress?.invoke(idx.toFloat() / count.toFloat())
                }
            }
            cachedTokenIds = ids
            cachedAttentionMask = mask
            onProgress?.invoke(1f)
            ids to mask
        }

    private suspend fun ensureTokenizer(onProgress: ((Float) -> Unit)? = null): ClipBpeTokenizer = tokenizerMutex.withLock {
        withContext(Dispatchers.IO) {
            val modelDir = File(context.filesDir, "models").apply { mkdirs() }
            val tokenizerFile = File(modelDir, CLIP_TOKENIZER_FILENAME)
            if (!tokenizerFile.exists()) {
                downloadFile(
                    url = CLIP_TOKENIZER_URL,
                    finalDest = tokenizerFile,
                    expectedSha256 = null,
                    notificationTitle = "Downloading AI tokenizer",
                    onProgress = onProgress
                )
            }
            ClipBpeTokenizer.fromFile(tokenizerFile)
        }
    }

    private suspend fun ensureModelOnDisk(onProgress: ((Float) -> Unit)? = null): File = modelMutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "models").apply { mkdirs() }
            val dest = File(dir, CLIP_MODEL_FILENAME)
            if (dest.exists() && sha256Hex(dest).equals(CLIP_MODEL_SHA256, ignoreCase = true)) return@withContext dest
            if (dest.exists()) dest.delete()

            downloadFile(
                url = CLIP_MODEL_URL,
                finalDest = dest,
                expectedSha256 = CLIP_MODEL_SHA256,
                notificationTitle = "Downloading AI model",
                onProgress = onProgress
            )
            dest
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return granted
    }

    private fun ensureDownloadChannel(): String {
        val channelId = "model_downloads"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return channelId
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return channelId
        val existing = nm.getNotificationChannel(channelId)
        if (existing != null) return channelId
        nm.createNotificationChannel(
            NotificationChannel(
                channelId,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress for AI models"
            }
        )
        return channelId
    }

    private fun notifyDownloadProgress(
        notificationId: Int,
        title: String,
        bytesRead: Long,
        totalBytes: Long,
        indeterminate: Boolean
    ) {
        if (!notificationsAllowed()) return
        val channelId = ensureDownloadChannel()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (indeterminate || totalBytes <= 0L) {
            builder.setProgress(0, 0, true)
            builder.setContentText("Downloading\u2026")
        } else {
            val progress = ((bytesRead * 100f) / totalBytes.toFloat()).roundToInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
            builder.setContentText("$progress%")
        }

        nm.notify(notificationId, builder.build())
    }

    private fun cancelDownloadNotification(notificationId: Int) {
        if (!notificationsAllowed()) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(notificationId)
    }

    private fun downloadFile(
        url: String,
        finalDest: File,
        expectedSha256: String?,
        notificationTitle: String,
        onProgress: ((Float) -> Unit)? = null
    ) {
        val tmp = File(finalDest.parentFile, "${finalDest.name}.part")
        tmp.delete()
        finalDest.parentFile?.mkdirs()

        val notificationId = 0xC1107 // stable per tagger
        var lastUiUpdate = 0L
        var lastProgress = -1

        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            connection.connect()

            val total = connection.contentLengthLong
            val indeterminate = total <= 0L
            val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }

            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)

                        readTotal += read.toLong()
                        val now = SystemClock.elapsedRealtime()
                        val progress =
                            if (indeterminate) -1 else ((readTotal * 100f) / total.toFloat()).roundToInt().coerceIn(0, 100)
                        val shouldUpdate =
                            indeterminate ||
                                progress != lastProgress ||
                                now - lastUiUpdate >= 350L
                        if (shouldUpdate) {
                            lastUiUpdate = now
                            lastProgress = progress
                            notifyDownloadProgress(
                                notificationId = notificationId,
                                title = notificationTitle,
                                bytesRead = readTotal,
                                totalBytes = total,
                                indeterminate = indeterminate
                            )
                            if (!indeterminate && total > 0L) {
                                onProgress?.invoke((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            if (expectedSha256 != null) {
                val actual = digest?.digest()?.joinToString("") { "%02x".format(it) }
                check(actual != null && actual.equals(expectedSha256, ignoreCase = true)) {
                    "Downloaded file hash mismatch"
                }
            }
            onProgress?.invoke(1f)

            if (finalDest.exists()) finalDest.delete()
            check(tmp.renameTo(finalDest)) { "Failed to move downloaded file into place" }
        } finally {
            tmp.delete()
            cancelDownloadNotification(notificationId)
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                digest.update(buf, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun preprocessClipImage(bitmap: Bitmap): FloatArray {
        val inputSize = 224
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)
        val out = FloatArray(1 * 3 * inputSize * inputSize)

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val p = pixels[y * inputSize + x]
                val r = ((p shr 16) and 0xFF) / 255f
                val g = ((p shr 8) and 0xFF) / 255f
                val b = (p and 0xFF) / 255f
                val idx = y * inputSize + x
                out[0 * inputSize * inputSize + idx] = (r - mean[0]) / std[0]
                out[1 * inputSize * inputSize + idx] = (g - mean[1]) / std[1]
                out[2 * inputSize * inputSize + idx] = (b - mean[2]) / std[2]
            }
        }
        return out
    }

    private fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return logits
        val maxVal = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0.0
        for (i in logits.indices) {
            val v = exp((logits[i] - maxVal).toDouble()).toFloat()
            exps[i] = v
            sum += v.toDouble()
        }
        val inv = if (sum <= 0.0) 0f else (1.0 / sum).toFloat()
        for (i in exps.indices) exps[i] = exps[i] * inv
        return exps
    }

    private fun extractColorTags(bitmap: Bitmap): List<String> {
        val w = 100
        val h = 100
        val resized = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        val counts = HashMap<String, Int>()
        for (p in pixels) {
            val r = Color.red(p)
            val g = Color.green(p)
            val b = Color.blue(p)
            val hsv = FloatArray(3)
            Color.RGBToHSV(r, g, b, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]

            val colorName = when {
                value < 0.2f -> "black"
                sat < 0.1f -> if (value > 0.8f) "white" else "gray"
                hue >= 340f || hue < 20f -> "red"
                hue >= 20f && hue < 45f -> "orange"
                hue >= 45f && hue < 70f -> "yellow"
                hue >= 70f && hue < 160f -> "green"
                hue >= 160f && hue < 260f -> "blue"
                hue >= 260f && hue < 340f -> "purple"
                else -> "unknown"
            }

            val finalName =
                if ((colorName == "orange" || colorName == "red") && value < 0.6f && sat < 0.7f) "brown" else colorName
            counts[finalName] = (counts[finalName] ?: 0) + 1
        }

        val colorful = counts.entries
            .filter { (name, _) -> name != "black" && name != "white" && name != "gray" }
            .sortedByDescending { it.value }
            .map { it.key }

        return if (colorful.isNotEmpty()) colorful.take(2) else listOfNotNull(counts.maxByOrNull { it.value }?.key).take(1)
    }

    private fun tagHierarchy(): Map<String, List<String>> = TAG_HIERARCHY

    companion object {
        private const val CANDIDATES_ASSET_PATH = "tagging/rapidraw_clip_candidates.txt"

        private const val CLIP_MODEL_URL =
            "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/clip_model.onnx?download=true"
        private const val CLIP_TOKENIZER_URL =
            "https://huggingface.co/CyberTimon/RapidRAW-Models/resolve/main/clip_tokenizer.json?download=true"

        private const val CLIP_MODEL_FILENAME = "clip_model.onnx"
        private const val CLIP_TOKENIZER_FILENAME = "clip_tokenizer.json"
        private const val CLIP_MODEL_SHA256 = "57879bb1c23cdeb350d23569dd251ed4b740a96d747c529e94a2bb8040ac5d00"

        private const val CLIP_MAX_TOKENS = 77

        private val TAG_HIERARCHY: Map<String, List<String>> = run {
            val m = HashMap<String, List<String>>()

            val peopleChildren = listOf(
                "man",
                "woman",
                "child",
                "baby",
                "boy",
                "girl",
                "teenager",
                "adult",
                "senior",
                "crowd",
                "family",
                "couple",
                "portrait",
                "self-portrait",
                "face",
                "hands",
                "feet",
                "candid"
            )
            for (child in peopleChildren) m[child] = listOf("person", "people")
            m["boy"] = listOf("person", "people", "child")
            m["girl"] = listOf("person", "people", "child")
            m["teenager"] = listOf("person", "people", "child")

            val animalChildren = listOf(
                "dog", "cat", "bird", "horse", "cow", "sheep", "pig", "goat", "chicken", "duck", "lion",
                "tiger", "bear", "wolf", "fox", "deer", "elephant", "giraffe", "zebra", "monkey", "panda",
                "snake", "lizard", "turtle", "frog", "fish", "shark", "whale", "dolphin", "insect"
            )
            for (child in animalChildren) m[child] = listOf("animal")
            m["dog"] = listOf("animal", "pet")
            m["cat"] = listOf("animal", "pet")
            m["puppy"] = listOf("animal", "pet", "dog")
            m["kitten"] = listOf("animal", "pet", "cat")
            m["lion"] = listOf("animal", "wildlife", "cat")
            m["tiger"] = listOf("animal", "wildlife", "cat")
            m["butterfly"] = listOf("animal", "insect")
            m["bee"] = listOf("animal", "insect")
            m["spider"] = listOf("animal", "insect")

            val natureChildren = listOf(
                "mountain",
                "hill",
                "valley",
                "canyon",
                "desert",
                "forest",
                "jungle",
                "tree",
                "flower",
                "field",
                "meadow",
                "grass",
                "farm",
                "garden",
                "park",
                "beach",
                "coast",
                "ocean",
                "sea",
                "river",
                "lake",
                "waterfall",
                "island",
                "cave",
                "rock",
                "volcano",
                "glacier",
                "snow"
            )
            for (child in natureChildren) m[child] = listOf("nature", "landscape")
            m["rose"] = listOf("nature", "landscape", "flower")
            m["tulip"] = listOf("nature", "landscape", "flower")
            m["sunflower"] = listOf("nature", "landscape", "flower")
            m["pine tree"] = listOf("nature", "landscape", "tree")
            m["palm tree"] = listOf("nature", "landscape", "tree")

            m["sunrise"] = listOf("sky", "sun")
            m["sunset"] = listOf("sky", "sun")
            m["aurora"] = listOf("sky", "night sky")
            m["milky way"] = listOf("sky", "night sky", "galaxy")

            val architectureChildren = listOf(
                "skyscraper",
                "bridge",
                "tunnel",
                "house",
                "home",
                "apartment",
                "cabin",
                "castle",
                "church",
                "cathedral",
                "tower",
                "lighthouse",
                "ruins",
                "monument",
                "statue",
                "fountain",
                "door",
                "window",
                "interior",
                "room"
            )
            for (child in architectureChildren) m[child] = listOf("architecture", "building")
            m["cityscape"] = listOf("city", "urban", "architecture")
            m["skyline"] = listOf("city", "urban", "architecture")
            m["street"] = listOf("city", "urban")

            val vehicleChildren = listOf(
                "car",
                "bicycle",
                "motorcycle",
                "bus",
                "train",
                "airplane",
                "boat",
                "ship",
                "truck",
                "van",
                "scooter"
            )
            for (child in vehicleChildren) m[child] = listOf("vehicle")

            val foodChildren = listOf(
                "fruit",
                "apple",
                "banana",
                "orange",
                "vegetable",
                "carrot",
                "broccoli",
                "tomato",
                "bread",
                "cake",
                "pizza",
                "pasta",
                "sushi",
                "burger",
                "sandwich",
                "salad",
                "soup"
            )
            for (child in foodChildren) m[child] = listOf("food")
            m["apple"] = listOf("food", "fruit")
            m["banana"] = listOf("food", "fruit")
            m["orange"] = listOf("food", "fruit")
            m["carrot"] = listOf("food", "vegetable")
            m["broccoli"] = listOf("food", "vegetable")
            m["tomato"] = listOf("food", "vegetable", "fruit")
            m["coffee"] = listOf("drink")
            m["tea"] = listOf("drink")
            m["juice"] = listOf("drink")
            m["wine"] = listOf("drink")
            m["beer"] = listOf("drink")

            m["macro"] = listOf("close-up")
            m["sepia"] = listOf("monochrome")
            m["black and white"] = listOf("monochrome")
            m["golden hour"] = listOf("lighting", "sunrise", "sunset")
            m["blue hour"] = listOf("lighting", "sunrise", "sunset")
            m["backlighting"] = listOf("lighting", "silhouette")
            m["drone shot"] = listOf("aerial view")

            m
        }
    }
}

private data class ClipEncoding(
    val ids: IntArray,
    val attentionMask: IntArray
)

private class ClipBpeTokenizer(
    private val vocab: Map<String, Int>,
    private val mergesRank: Map<String, Int>,
    private val byteEncoder: Map<Int, String>,
    private val bosId: Int,
    private val eosId: Int
) {
    private val pat = Regex(
        "('s|'t|'re|'ve|'m|'ll|'d| ?\\p{L}+| ?\\p{N}+| ?[^\\s\\p{L}\\p{N}]+|\\s+(?!\\S)|\\s+)",
        setOf(RegexOption.MULTILINE)
    )

    private val bpeCache = HashMap<String, List<String>>(2048)

    fun encode(text: String, maxLen: Int): ClipEncoding {
        val pieces = pat.findAll(text).map { it.value }.toList()
        val tokens = mutableListOf<Int>()
        tokens += bosId

        for (piece in pieces) {
            val encoded = byteEncode(piece)
            val bpeTokens = bpe(encoded)
            for (t in bpeTokens) {
                val id = vocab[t] ?: continue
                tokens += id
                if (tokens.size >= maxLen - 1) break
            }
            if (tokens.size >= maxLen - 1) break
        }
        tokens += eosId

        val ids = IntArray(maxLen)
        val mask = IntArray(maxLen)
        val n = min(tokens.size, maxLen)
        for (i in 0 until n) {
            ids[i] = tokens[i]
            mask[i] = 1
        }
        return ClipEncoding(ids = ids, attentionMask = mask)
    }

    private fun byteEncode(text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size)
        for (b in bytes) {
            val key = b.toInt() and 0xFF
            sb.append(byteEncoder[key] ?: "")
        }
        return sb.toString()
    }

    private fun bpe(token: String): List<String> {
        bpeCache[token]?.let { return it }
        if (token.isEmpty()) return emptyList()

        var word = token.map { it.toString() }.toMutableList()
        while (true) {
            var bestPairIdx: Int? = null
            var bestRank = Int.MAX_VALUE

            for (i in 0 until word.size - 1) {
                val key = "${word[i]} ${word[i + 1]}"
                val rank = mergesRank[key] ?: continue
                if (rank < bestRank) {
                    bestRank = rank
                    bestPairIdx = i
                }
            }

            val i = bestPairIdx ?: break
            val merged = word[i] + word[i + 1]
            val out = ArrayList<String>(word.size - 1)
            var idx = 0
            while (idx < word.size) {
                if (idx == i) {
                    out.add(merged)
                    idx += 2
                } else {
                    out.add(word[idx])
                    idx += 1
                }
            }
            word = out
            if (word.size <= 1) break
        }
        bpeCache[token] = word
        return word
    }

    companion object {
        fun fromFile(file: File): ClipBpeTokenizer {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            val model = json.getJSONObject("model")
            val vocabObj = model.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            vocabObj.keys().forEach { k -> vocab[k] = vocabObj.getInt(k) }
            val mergesArr = model.getJSONArray("merges")
            val mergesRank = HashMap<String, Int>(mergesArr.length())
            for (i in 0 until mergesArr.length()) {
                mergesRank[mergesArr.getString(i)] = i
            }

            val byteEncoder = bytesToUnicode()

            val bosId = vocab["<|startoftext|>"] ?: 49406
            val eosId = vocab["<|endoftext|>"] ?: 49407
            return ClipBpeTokenizer(
                vocab = vocab,
                mergesRank = mergesRank,
                byteEncoder = byteEncoder,
                bosId = bosId,
                eosId = eosId
            )
        }

        private fun bytesToUnicode(): Map<Int, String> {
            val bs = mutableListOf<Int>()
            for (i in 33..126) bs.add(i)
            for (i in 161..172) bs.add(i)
            for (i in 174..255) bs.add(i)

            val cs = bs.toMutableList()
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val m = HashMap<Int, String>(256)
            for (i in bs.indices) {
                m[bs[i]] = String(Character.toChars(cs[i]))
            }
            return m
        }
    }
}
