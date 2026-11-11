package com.hangulmate.domain.model

/**
 * The 14 vocabulary topics from the seed data. [key] is the value stored
 * in the database and in the seed JSON; [nameNe] is the Nepali display
 * name shown in the UI; [nameEn] is the English display name; [iconName]
 * is the Material icon name used by the UI layer.
 */
enum class Topic(
    val key: String,
    val nameNe: String,
    val nameEn: String,
    val iconName: String
) {
    DAILY("daily", "दैनिक जीवन", "Daily Life", "home"),
    WORKPLACE("workplace", "कार्यस्थल", "Workplace", "work"),
    AGRICULTURE("agriculture", "कृषि", "Agriculture", "agriculture"),
    MANUFACTURING("manufacturing", "उत्पादन", "Manufacturing", "factory"),
    CONSTRUCTION("construction", "निर्माण", "Construction", "construction"),
    FOOD("food", "खाना", "Food", "restaurant"),
    TRANSPORT("transport", "यातायात", "Transport", "directions_bus"),
    HEALTH("health", "स्वास्थ्य", "Health", "health_and_safety"),
    CONVERSATION("conversation", "बातचीत", "Conversation", "chat"),
    NUMBERS("numbers", "संख्या", "Numbers", "pin"),
    DIRECTIONS("directions", "दिशा", "Directions", "explore"),
    TOOLS("tools", "औजार", "Tools", "build"),
    SAFETY("safety", "सुरक्षा", "Safety", "shield"),
    GENERAL("general", "सामान्य", "General", "category");

    companion object {
        fun fromKey(key: String): Topic =
            entries.firstOrNull { it.key == key } ?: GENERAL
    }
}
