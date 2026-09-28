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

        val picked = (meaningful.ifEmpty { tags }).take(MAX_TAGS).map { titleCase(it) }
        var title = picked.joinToString(" · ")
        if (title.length > MAX_LEN) title = title.take(MAX_LEN).trimEnd(' ', '·') + "…"
        return title.ifEmpty { "Prompt" }
    }

    // Capitalize words written in Latin letters; leave other scripts as they are
    private fun titleCase(s: String): String =
        s.split(' ').joinToString(" ") { w ->
            if (w.isNotEmpty() && w[0].isLowerCase() && w[0] in 'a'..'z') w.replaceFirstChar { it.uppercase() } else w
        }
}
