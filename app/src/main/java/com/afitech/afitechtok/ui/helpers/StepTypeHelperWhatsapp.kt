package com.afitech.afitechtok.ui.helpers

enum class StepType {
    ROOT,                // root storage (Android 10-)
    ANDROID_DIR,         // Android
    MEDIA_DIR_LOWER,     // media
    WHATSAPP_PACKAGE,    // com.whatsapp / com.whatsapp.w4b
    WHATSAPP_DIR,        // WhatsApp
    MEDIA_DIR_CAPITAL,   // Media
    STATUSES_DIR,        // .Statuses
    UNKNOWN
}
