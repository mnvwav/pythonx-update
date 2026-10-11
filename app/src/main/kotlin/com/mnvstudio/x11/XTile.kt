package com.mnvstudio.x11

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: start or stop the X server from the notification shade. */
class XTile : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val turnOn = XHolder.server == null
        if (!turnOn) XService.stop(this)
        else runCatching { XHolder.ensureStarted(this) }.onFailure { AppLog.e("Couldn't start from the tile", it) }
        update(on = turnOn)
    }

    private fun update(on: Boolean = XHolder.server != null) {
        val tile = qsTile ?: return
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "X11"
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = if (on) "Display :${XHolder.display}" else "Off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }
}
