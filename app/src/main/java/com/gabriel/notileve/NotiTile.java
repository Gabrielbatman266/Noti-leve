package com.gabriel.notileve;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botão nos atalhos rápidos: liga com o último tempo usado, ou desliga. */
@TargetApi(24)
public class NotiTile extends TileService {
    @Override
    public void onStartListening() {
        update();
    }

    @Override
    public void onClick() {
        SharedPreferences sp = Prefs.p(this);
        if (sp.getBoolean(Prefs.ACTIVE, false)) {
            Block.stop(this, false);
        } else if (Block.listenerAccess(this)) {
            int min = sp.getInt(Prefs.LAST_MIN, 25);
            Block.start(this, min, "Silêncio rápido");
        } else {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    startActivityAndCollapse(Block.openApp(this, 0));
                } else {
                    openLegacy();
                }
            } catch (Exception ignored) {}
        }
        update();
    }

    @SuppressWarnings("deprecation")
    private void openLegacy() {
        startActivityAndCollapse(new android.content.Intent(this, MainActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        SharedPreferences sp = Prefs.p(this);
        boolean on = sp.getBoolean(Prefs.ACTIVE, false);
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(on ? "Silêncio ligado" : "Silêncio");
        if (Build.VERSION.SDK_INT >= 29) {
            long end = sp.getLong(Prefs.END, 0);
            int min = sp.getInt(Prefs.LAST_MIN, 25);
            if (on) t.setSubtitle(end > 0 ? "até " + Block.clock(end) : "sem hora");
            else t.setSubtitle(min > 0 ? min + " min" : "sem hora");
        }
        t.updateTile();
    }
}
