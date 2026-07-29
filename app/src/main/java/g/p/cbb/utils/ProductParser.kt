package g.p.cbb.utils

import java.util.Locale

object ProductParser {

    /**
     * Generates a shortcut for a product name.
     * Example: "1 Litre Milk" -> "m1", "Bread" -> "b"
     */
    fun generateShortcut(name: String): String {
        val cleanName = name.lowercase(Locale.getDefault())
        val words = cleanName.split("\\s+".toRegex()).filter { it.isNotBlank() }
        
        val firstLetter = words.find { it.any { c -> c.isLetter() } }?.find { it.isLetter() } ?: ""
        val firstDigit = words.find { it.any { c -> c.isDigit() } }?.filter { it.isDigit() } ?: ""
        
        return "$firstLetter$firstDigit".trim()
    }

    /**
     * Extracts units from a product name.
     * Example: "1 Litre Milk" -> "1 Litre"
     */
    fun extractUnits(name: String): String? {
        val unitsList = listOf("litre", "liter", "kg", "gram", "gm", "ml", "pkt", "packet", "pc", "piece")
        val words = name.split("\\s+".toRegex())
        
        for (i in words.indices) {
            val word = words[i].lowercase(Locale.getDefault())
            if (unitsList.contains(word)) {
                // Check if previous word is a number
                if (i > 0 && words[i-1].any { it.isDigit() }) {
                    return "${words[i-1]} ${words[i]}"
                }
                return words[i]
            }
        }
        return null
    }

    /**
     * Updates product name and price based on a new quantity.
     * Example: ("1 Litre Milk", 60.0, 3, "1 Litre") -> ("3 Litre Milk", 180.0)
     */
    fun applyQuantity(
        currentName: String, 
        currentPrice: Double, 
        newQuantity: Int, 
        units: String?,
        basePrice: Double // The price for 1 unit of 'units'
    ): Pair<String, Double> {
        if (units == null || newQuantity <= 0) return currentName to currentPrice
        
        val unitPattern = units.split("\\s+".toRegex()).last() // e.g., "Litre"
        val newUnits = "$newQuantity $unitPattern"
        
        val updatedName = currentName.replace(units, newUnits, ignoreCase = true)
        val finalName = if (updatedName == currentName) {
            "$newUnits $currentName"
        } else {
            updatedName
        }
        
        return finalName to (basePrice * newQuantity)
    }
}
