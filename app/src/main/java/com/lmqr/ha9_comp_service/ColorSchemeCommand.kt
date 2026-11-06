package com.lmqr.ha9_comp_service

object ColorSchemeCommand {
    const val SYSTEM_THEME = "system"

    fun of(type: String?, colorHex: String): String? =
        if (type == null || type == SYSTEM_THEME) null else "theme $type $colorHex"
}
