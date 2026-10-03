package com.anos.myscreenoff.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.anos.myscreenoff.MainActivity

/**
 * Quick Settings tile that turns [ScreenOffService] on and off, e.g. around a banking app that will
 * not run while a sideloaded accessibility service is on. Switching needs WRITE_SECURE_SETTINGS,
 * granted once over adb; until then the tile opens the app, which explains how.
 */
class ServiceToggleTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!ScreenOffService.canToggle(this)) {
            openApp()
            return
        }
        // Accessibility changes from the lock screen wait for the user to unlock first.
        if (isLocked) unlockAndRun(::toggle) else toggle()
    }

    private fun toggle() {
        ScreenOffService.setEnabled(this, !ScreenOffService.isEnabled(this))
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.state = if (ScreenOffService.isEnabled(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // The Intent overload only runs below Android 14.
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
