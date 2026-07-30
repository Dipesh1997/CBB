package g.p.cbb.utils

import java.util.Locale

object ProductParser {

    /**
     * Generates a shortcut for a product name.
     * Example: "1 Litre Milk" -> "mi1", "1 Litre Oil" -> "oi1"
     */
    fun generateShortcut(name: String): String {
        val unitsList = listOf("litre", "liter", "ltr", "kg", "gram", "gm", "ml", "pkt", "packet", "pc", "piece", "bag")
        val cleanName = name.lowercase(Locale.getDefault())
        
        // Extract the first numeric sequence
        val firstDigit = "\\d+".toRegex().find(cleanName)?.value ?: ""
        
        // Remove units and numbers to find core product words
        var coreText = cleanName
        unitsList.forEach { unit ->
            coreText = coreText.replace("\\b$unit\\b".toRegex(), "")
        }
        coreText = coreText.replace("\\d+".toRegex(), "")
        
        val words = coreText.split("\\s+".toRegex()).filter { it.length >= 2 }
        
        val letters = if (words.size >= 2) {
            // Take first letter of first two words
            "${words[0][0]}${words[1][0]}"
        } else if (words.isNotEmpty()) {
            // Take first two letters of the single word
            words[0].take(2)
        } else {
            // Fallback to original logic if core extraction fails
            cleanName.filter { it.isLetter() }.take(2)
        }
        
        return "$letters$firstDigit".trim()
    }

    /**
     * Extracts units from a product name.
     * Example: "1 Litre Milk" -> "1 Litre"
     */
    fun extractUnits(name: String): String? {
        val unitsList = listOf("litre", "liter", "ltr", "kg", "gram", "gm", "ml", "pkt", "packet", "pc", "piece", "bag")
        val words = name.split("\\s+".toRegex())
        
        for (i in words.indices) {
            val word = words[i].lowercase(Locale.getDefault())
            // Check if word contains any unit (handles "5kg" case)
            val matchedUnit = unitsList.find { word.contains(it) }
            
            if (matchedUnit != null) {
                // If the word itself has digits (like "5kg")
                if (word.any { it.isDigit() }) {
                    return word
                }
                // Check if previous word is a number
                if (i > 0 && words[i-1].any { it.isDigit() }) {
                    return "${words[i-1]} $matchedUnit"
                }
                return matchedUnit
            }
        }
        return null
    }

    /**
     * Updates product name and price based on a new quantity multiplier.
     * Example: ("4 Litre Royal", 1000.0, 3, "4 Litre", 1000.0) -> ("12 Litre Royal", 3000.0)
     */
    fun applyQuantity(
        currentName: String, 
        currentPrice: Double, 
        newQuantity: Int, 
        units: String?,
        basePrice: Double, // The price for the base suggestion item
        baseName: String // The original name from the catalog
    ): Pair<String, Double> {
        if (newQuantity <= 0) return currentName to currentPrice
        
        // If no units, we just update the price (acting as a simple multiplier)
        if (units == null) {
            return currentName to (basePrice * newQuantity)
        }

        // Extract the unit label (e.g., "Litre") from the base units template
        val numberRegex = "\\d+".toRegex()
        val baseQuantityInTemplate = numberRegex.find(units)?.value?.toIntOrNull() ?: 1
        val unitLabel = units.replace(numberRegex, "").trim() 
        
        // Calculate total quantity relative to the base template
        val totalCalculatedQuantity = baseQuantityInTemplate * newQuantity
        val newUnitsText = "$totalCalculatedQuantity $unitLabel".trim()
        
        // Find the core product name by removing the base units from the base name
        val coreProductName = baseName.replace(units, "", ignoreCase = true).trim()
        
        // Construct the new name: "[Total Qty] [Unit] [Product Name]"
        val finalName = if (coreProductName.isNotEmpty()) {
            "$newUnitsText $coreProductName"
        } else {
            newUnitsText
        }
        
        return finalName to (basePrice * newQuantity)
    }

    /**
     * Joins unit and name if not already joined.
     * Example: ("Royal", "1 Litre") -> "1 Litre Royal"
     */
    fun getFormattedName(name: String, units: String?): String {
        if (units == null) return name
        
        // Check if name already contains the units (case-insensitive)
        if (name.contains(units, ignoreCase = true)) return name
        
        // Also check if name contains just the unit label (e.g. "Litre")
        val unitLabel = units.split("\\s+".toRegex()).last()
        if (name.contains(unitLabel, ignoreCase = true)) return name
        
        return "$units $name".trim()
    }
}
