package com.example.apaudio

import java.util.prefs.Preferences

internal class WindowsRaopVolumeStore {
    private val preferences = Preferences.userRoot().node("com/example/apaudio/windows")

    fun load(): Float = preferences.getFloat(KEY_VOLUME_DB, DEFAULT_VOLUME_DB)

    fun saveSenderVolume(volumeDb: Float) {
        if (volumeDb.isFinite()) {
            preferences.putFloat(KEY_VOLUME_DB, volumeDb)
        }
    }

    private companion object {
        const val KEY_VOLUME_DB = "last_sender_volume_db"
        const val DEFAULT_VOLUME_DB = -20f
    }
}
