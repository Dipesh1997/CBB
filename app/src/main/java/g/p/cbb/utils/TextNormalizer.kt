package g.p.cbb.utils

object TextNormalizer {
    private val numberMap = mapOf(
        "zero" to "0",
        "one" to "1",
        "two" to "2",
        "three" to "3",
        "four" to "4",
        "five" to "5",
        "six" to "6",
        "seven" to "7",
        "eight" to "8",
        "nine" to "9",
        "ten" to "10"
    )

    fun normalize(text: String): String {
        var normalized = text.lowercase().trim()
        
        // Replace number words with digits
        numberMap.forEach { (word, digit) ->
            // Use regex for whole word replacement only to avoid partial matches like "stones" -> "st1s"
            val regex = "\\b$word\\b".toRegex()
            normalized = normalized.replace(regex, digit)
        }
        
        return normalized.replace("\\s+".toRegex(), " ").trim()
    }
}
