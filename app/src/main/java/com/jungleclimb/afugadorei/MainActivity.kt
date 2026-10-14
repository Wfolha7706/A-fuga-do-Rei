package com.jungleclimb.afugadorei

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager

class MainActivity : Activity() {

    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}

        gameView = GameView(this)
        setContentView(gameView)

        try {
            hideSystemUI()
        } catch (_: Exception) {}
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            hideSystemUI()
        } catch (_: Exception) {}
        if (::gameView.isInitialized) {
            gameView.resumeGame()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::gameView.isInitialized) {
            gameView.pauseGame()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::gameView.isInitialized) {
            gameView.pauseGame()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!::gameView.isInitialized || !gameView.handleBackPressed()) {
            super.onBackPressed()
        }
    }
}
