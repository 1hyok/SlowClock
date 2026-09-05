package com.example.slowclock.data.model

/** 화면 밝기 테마. 기본은 기기 설정을 따른다. */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode = entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
