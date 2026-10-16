package com.nexbytes.bolap3.tile;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.nexbytes.bolap3.R;
import com.nexbytes.bolap3.playback.PlaybackInfo;
import com.nexbytes.bolap3.playback.RemoteCommands;

/** Quick settings tile: play/pause, showing the current song as subtitle. */
public class PlaybackTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        // Optimistic update; the service refreshes the real state right after.
        PlaybackInfo.playing = !PlaybackInfo.playing;
        updateTile();
        RemoteCommands.send(this, RemoteCommands.PLAY_PAUSE, null);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setState(PlaybackInfo.playing ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(getString(R.string.brand_name));
        tile.setSubtitle(PlaybackInfo.hasMedia && PlaybackInfo.title != null
                ? PlaybackInfo.title
                : getString(R.string.tile_label));
        tile.updateTile();
    }
}
