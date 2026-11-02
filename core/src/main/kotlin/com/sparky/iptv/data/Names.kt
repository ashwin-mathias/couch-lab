package com.sparky.iptv.data

/** Reads the provider's naming conventions: "US| ABC NEWS", "##### NEWS #####", "Live | Lakers vs …". */
object Names {
    const val OTHER = "OTHER"

    private val prefix = Regex("""^\s*([A-Za-z0-9+\-]{1,6}(?:[ \-][A-Za-z0-9+]{1,6})?)\s*[|│┃]\s*(.*)$""")
    private val divider = Regex("""^\s*[#=*~•★]{2,}.*[#=*~•★]{2,}\s*$""")
    private val event = Regex("""^\s*(live|next|soon|now)\s*[|:]\s*(.+)$""", RegexOption.IGNORE_CASE)

    /** Region code from a "US| News" style name, or null when there isn't one. */
    fun region(name: String): String? =
        prefix.find(name)?.groupValues?.get(1)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }

    /** The name without its region prefix. */
    fun strip(name: String): String {
        val m = prefix.find(name) ?: return name.trim()
        if (m.groupValues[1].trim().lowercase() in eventWords) return name.trim()  // "Live | …" is an event, not a region
        return m.groupValues[2].trim().ifBlank { name.trim() }
    }

    private val eventWords = setOf("live", "next", "soon", "now")

    /** Providers insert rows like "##### NEWS NETWORK #####" as section dividers. */
    fun isDivider(name: String): Boolean = divider.matches(name)

    fun dividerTitle(name: String): String =
        strip(name).trim('#', '=', '*', '~', '•', '★', ' ').ifBlank { strip(name) }

    /** For event channels ("Live | Lakers vs Suns") returns the badge and the event title. */
    fun event(display: String): Pair<String?, String> {
        val m = event.find(display) ?: return null to display
        return m.groupValues[1].uppercase() to m.groupValues[2].trim()
    }

    private val labels = mapOf(
        "US" to "United States", "USA" to "United States", "UK" to "United Kingdom", "GB" to "United Kingdom",
        "CA" to "Canada", "IN" to "India", "IND" to "India", "PK" to "Pakistan", "BD" to "Bangladesh",
        "AR" to "Arabic", "ARA" to "Arabic", "FR" to "France", "DE" to "Germany", "ES" to "Spain",
        "IT" to "Italy", "PT" to "Portugal", "BR" to "Brazil", "MX" to "Mexico", "LATAM" to "Latin America",
        "LAT" to "Latin America", "NL" to "Netherlands", "BE" to "Belgium", "PL" to "Poland", "RU" to "Russia",
        "TR" to "Turkey", "GR" to "Greece", "IR" to "Iran", "AF" to "Afghanistan", "SO" to "Somalia",
        "EX-YU" to "Ex-Yugoslavia", "YU" to "Ex-Yugoslavia", "AL" to "Albania", "RO" to "Romania",
        "HU" to "Hungary", "CZ" to "Czechia", "SE" to "Sweden", "NO" to "Norway", "DK" to "Denmark",
        "FI" to "Finland", "AU" to "Australia", "NZ" to "New Zealand", "PH" to "Philippines",
        "CN" to "China", "JP" to "Japan", "KR" to "Korea", "VN" to "Vietnam", "TH" to "Thailand",
        "MY" to "Malaysia", "ID" to "Indonesia", "AFR" to "Africa", "NG" to "Nigeria", "ZA" to "South Africa",
        "KE" to "Kenya", "CARIB" to "Caribbean", "IL" to "Israel", "KU" to "Kurdish", "KUR" to "Kurdish",
        "SP" to "Sports", "4K" to "4K / UHD", "UHD" to "4K / UHD", "VIP" to "VIP", "PPV" to "Pay-per-view",
        "EN" to "English", "EU" to "Europe", "INT" to "International",
        OTHER to "Other channels",
    )

    fun regionLabel(code: String): String = labels[code] ?: code
}
