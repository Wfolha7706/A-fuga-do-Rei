package com.jungleclimb.afugadorei

import android.content.Context
import android.content.SharedPreferences

class SaveManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("jungle_climb_save_v1", Context.MODE_PRIVATE)

    var unlockedLevel: Int
        get() = prefs.getInt("unlocked_level", 1).coerceIn(1, 12)
        set(value) = prefs.edit().putInt("unlocked_level", value.coerceIn(1, 12)).apply()

    var highScore: Int
        get() = prefs.getInt("global_high_score", 0).coerceAtLeast(0)
        set(value) {
            if (value > highScore) {
                prefs.edit().putInt("global_high_score", value).apply()
            }
        }

    var musicEnabled: Boolean
        get() = prefs.getBoolean("music_enabled", true)
        set(value) = prefs.edit().putBoolean("music_enabled", value).apply()

    var sfxEnabled: Boolean
        get() = prefs.getBoolean("sfx_enabled", true)
        set(value) = prefs.edit().putBoolean("sfx_enabled", value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean("vibration_enabled", true)
        set(value) = prefs.edit().putBoolean("vibration_enabled", value).apply()

    // Escala dos botões: 0 = Pequeno (0.85f), 1 = Médio (1.0f), 2 = Grande (1.2f)
    var controlScaleMode: Int
        get() = prefs.getInt("control_scale", 1).coerceIn(0, 2)
        set(value) = prefs.edit().putInt("control_scale", value.coerceIn(0, 2)).apply()

    fun getControlScaleFactor(): Float = when (controlScaleMode) {
        0 -> 0.85f
        2 -> 1.20f
        else -> 1.0f
    }

    fun getLevelBestScore(level: Int): Int {
        return prefs.getInt("level_${level}_score", 0).coerceAtLeast(0)
    }

    fun getLevelMedal(level: Int): Int {
        // 0 = Nenhuma, 1 = Bronze, 2 = Prata, 3 = Ouro
        return prefs.getInt("level_${level}_medal", 0).coerceIn(0, 3)
    }

    fun recordLevelCompletion(level: Int, score: Int, medal: Int, totalCampaignScore: Int) {
        val editor = prefs.edit()
        if (score > getLevelBestScore(level)) {
            editor.putInt("level_${level}_score", score)
        }
        if (medal > getLevelMedal(level)) {
            editor.putInt("level_${level}_medal", medal)
        }
        if (level >= unlockedLevel && level < 12) {
            editor.putInt("unlocked_level", level + 1)
        }
        if (totalCampaignScore > highScore) {
            editor.putInt("global_high_score", totalCampaignScore)
        }
        editor.apply()
    }
}
