package com.gabriel.notileve;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Set;

/**
 * O "Escudo": enquanto o bloqueio está ligado, recolhe cada notificação que chega
 * e guarda no cofre. Apps liberados, chamadas e o "pulso de urgência" passam.
 */
public class ShieldService extends NotificationListenerService {
    private static ShieldService inst;

    private static final long PULSE_WINDOW = 3 * 60_000L;
    private static final int PULSE_COUNT = 3;
    private final HashMap<String, ArrayDeque<Long>> pulses = new HashMap<>();

    @Override
    public void onListenerConnected() {
        inst = this;
        if (Prefs.active(this)) sweep();
    }

    @Override
    public void onListenerDisconnected() {
        if (inst == this) inst = null;
    }

    static void sweepNow(Context c) {
        ShieldService s = inst;
        if (s != null && Prefs.p(c).getBoolean(Prefs.SWEEP, true)) s.sweep();
    }

    private void sweep() {
        try {
            StatusBarNotification[] arr = getActiveNotifications();
            if (arr == null) return;
            for (StatusBarNotification sbn : arr) handle(sbn, true);
        } catch (Exception ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            handle(sbn, false);
        } catch (Exception ignored) {}
    }

    private void handle(StatusBarNotification sbn, boolean sweeping) {
        SharedPreferences sp = Prefs.p(this);
        if (!sp.getBoolean(Prefs.ACTIVE, false)) return;

        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName())) return;
        if (!sbn.isClearable()) return; // música tocando, downloads, serviços fixos

        Notification n = sbn.getNotification();
        if (sp.getBoolean(Prefs.CALLS, true)
                && (Notification.CATEGORY_CALL.equals(n.category)
                || Notification.CATEGORY_ALARM.equals(n.category))) return;

        Set<String> wl = Prefs.whitelist(this);
        if (wl.contains(pkg)) return;

        boolean groupSummary = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;

        if (!sweeping && !groupSummary && sp.getBoolean(Prefs.URGENCY, true) && pulse(pkg, sbn.getPostTime())) {
            Block.postUrgent(this, Prefs.appLabel(this, pkg));
            return; // deixa essa passar
        }

        cancelNotification(sbn.getKey());
        if (groupSummary) return;

        Bundle ex = n.extras;
        CharSequence t = ex.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (x == null) x = ex.getCharSequence(Notification.EXTRA_TEXT);
        if (t == null && x == null) return;

        boolean isNew = Vault.add(this, sbn.getKey(), pkg, Prefs.appLabel(this, pkg),
                t == null ? "" : t.toString(), x == null ? "" : x.toString(), sbn.getPostTime());
        if (isNew) {
            sp.edit().putInt(Prefs.SESS_BLOCKED, sp.getInt(Prefs.SESS_BLOCKED, 0) + 1).apply();
            Block.postOngoing(this);
        }
    }

    /** true quando o mesmo app insistiu PULSE_COUNT vezes dentro da janela. */
    private boolean pulse(String pkg, long when) {
        ArrayDeque<Long> q = pulses.get(pkg);
        if (q == null) {
            q = new ArrayDeque<>();
            pulses.put(pkg, q);
        }
        Long last = q.peekLast();
        if (last != null && when - last < 2_000L) return false; // rajada da mesma mensagem
        q.addLast(when);
        while (!q.isEmpty() && when - q.peekFirst() > PULSE_WINDOW) q.pollFirst();
        if (q.size() >= PULSE_COUNT) {
            q.clear();
            return true;
        }
        return false;
    }
}
