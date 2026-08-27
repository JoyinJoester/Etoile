package takagi.ru.monica.data

/**
 * Settings data classes
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

enum class ColorScheme {
    DEFAULT,
    OCEAN_BLUE,      // 海洋蓝
    SUNSET_ORANGE,   // 日落橙
    FOREST_GREEN,    // 森林绿
    TECH_PURPLE,     // 科技紫
    BLACK_MAMBA,     // 黑曼巴
    GREY_STYLE,      // 小黑紫
    WATER_LILIES,    // 睡莲
    IMPRESSION_SUNRISE, // 印象·日出
    JAPANESE_BRIDGE, // 日本桥
    HAYSTACKS,       // 干草堆
    ROUEN_CATHEDRAL, // 鲁昂大教堂
    PARLIAMENT_FOG,  // 国会大厦
    CATPPUCCIN_LATTE,     // Catppuccin · Latte
    CATPPUCCIN_FRAPPE,    // Catppuccin · Frappé
    CATPPUCCIN_MACCHIATO, // Catppuccin · Macchiato
    CATPPUCCIN_MOCHA,     // Catppuccin · Mocha
    CUSTOM           // 自定义
}

enum class Language {
    SYSTEM, ENGLISH, CHINESE, VIETNAMESE, JAPANESE, RUSSIAN
}

enum class NoteCodeBlockCollapseMode {
    COMPACT,
    BALANCED,
    EXPANDED
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val oledPureBlackEnabled: Boolean = false,
    val colorScheme: ColorScheme = ColorScheme.DEFAULT,
    val customPrimaryColor: Long = 0xFF6650a4,
    val customSecondaryColor: Long = 0xFF625b71,
    val customTertiaryColor: Long = 0xFF7D5260,
    val customNeutralColor: Long = 0xFF605D66,
    val customNeutralVariantColor: Long = 0xFF625B71,
    val language: Language = Language.SYSTEM,
    val screenshotProtectionEnabled: Boolean = false
)
