package com.hangulmate.brand

/**
 * The single source of truth for the app's brand name.
 * Every screen, string and dialog must read the name from here —
 * never hard-code "HangulMate" (or any developer branding) elsewhere.
 */
object BrandConfig {
    const val appName: String = "HangulMate"
    const val taglineNe: String = "कोरियाली सिक्नुहोस्, नेपालीमा"
    const val taglineEn: String = "Learn Korean, in Nepali"
}
