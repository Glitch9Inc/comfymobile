package sh.hnet.comfychair.util

/**
 * Makes a short, readable title from a prompt, without any AI:
 * drops boilerplate tags (quality/score/count tags, LoRA tags, weights) and keeps
 * the first few tags that actually describe the picture.
 *
 * "masterpiece, best quality, 1girl, solo, (silver hair:1.2), cozy knit sweater, <lora:x:0.8>"
 *   → "Silver Hair · Cozy Knit Sweater"
 */
object PromptTitle {
    private const val MAX_LEN = 32
    private const val MAX_TAGS = 2

    // Tags that appear in almost every prompt and say nothing about the subject
    private val BOILERPLATE = setOf(
        "masterpiece", "best quality", "high quality", "highest quality", "top quality", "good quality",
        "amazing quality", "very aesthetic", "aesthetic", "absurdres", "highres", "high res", "hires",
        "ultra detailed", "ultra-detailed", "extremely detailed", "highly detailed", "detailed", "intricate details",
        "8k", "4k", "uhd", "hdr", "sharp focus", "realistic", "photorealistic", "official art", "illustration",
        "newest", "recent", "year 2024", "year 2025", "year 2026", "safe", "sfw", "general", "sensitive",
        "solo", "solo focus", "looking at viewer", "simple background", "white background",
        "depth of field", "bokeh", "cinematic lighting", "soft lighting", "beautiful", "cute",
        "score_9", "score_8_up", "score_7_up", "score_6_up", "score_5_up", "score_4_up",
        "source_anime", "source_cartoon", "rating_safe", "anime", "anime style", "anime coloring"
    )

    // "1girl", "2boys", "multiple girls", ...
    private val COUNT_TAG = Regex("""^(\d+\+?\s*(girl|girls|boy|boys|other|others)|multiple (girls|boys))$""")

    fun from(prompt: String): String {
        val cleaned = prompt
            .replace(Regex("""<[^>]*>"""), " ")            // <lora:...>, <embedding:...>
            .replace(Regex(""":\s*-?\d+(\.\d+)?"""), "")   // weights like :1.2
            .replace(Regex("""[()\[\]{}]"""), " ")          // emphasis brackets
            .replace('_', ' ')

        val tags = cleaned.split(',', '\n', '|', '.')
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() }

        val meaningful = tags.filter { t ->
            val low = t.lowercase()
            low !in BOILERPLATE && !COUNT_TAG.matches(low) && low.length > 1 && !low.startsWith("score")
        }.distinctBy { it.lowercase() }

        val picked = (meaningful.ifEmpty { tags }).take(MAX_TAGS).map { titleCase(shortenSentence(it)) }
        var title = picked.joinToString(" · ")
        if (title.length > MAX_LEN) title = title.take(MAX_LEN).trimEnd(' ', '·') + "…"
        return title.ifEmpty { "Prompt" }
    }

    private val LEADING_FILLER = Regex(
        """^(?:(?:a|an|the)\s+)?(?:(?:warm|cozy|beautiful|high quality|cinematic|detailed)\s+(?:and\s+)?)*(?:(?:photo|picture|image|illustration|painting|shot|portrait)\s+)?(?:of\s+)?(?:(?:a|an|the)\s+)?""",
        RegexOption.IGNORE_CASE
    )
    private val SMALL_WORDS = setOf("a", "an", "the", "of", "and", "or", "in", "on", "at", "with", "by", "to", "for")

    // Sentence-style prompt ("A warm photo of a woman sitting in a chair") → "Woman Sitting in a Chair"
    private fun shortenSentence(tag: String): String {
        val words = tag.split(' ')
        if (words.size <= 4) return tag
        val stripped = LEADING_FILLER.replace(tag, "").ifBlank { tag }
        val cut = stripped.split(' ').take(4).toMutableList()
        while (cut.size > 1 && cut.last().lowercase() in SMALL_WORDS) cut.removeAt(cut.lastIndex)
        return cut.joinToString(" ")
    }

    // Capitalize Latin-letter words (small words stay lower case except first); other scripts unchanged
    private fun titleCase(s: String): String =
        s.split(' ').mapIndexed { i, w ->
            when {
                w.isEmpty() || w[0] !in 'a'..'z' -> w
                i > 0 && w in SMALL_WORDS -> w
                else -> w.replaceFirstChar { it.uppercase() }
            }
        }.joinToString(" ")
}
