package com.dueckis.kawaiiraweditor.domain.ai

import org.json.JSONObject
import java.io.File
import kotlin.math.min

internal class ClipBpeTokenizer(
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
