package com.gabriel.notileve;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.provider.Settings;
import android.service.quicksettings.TileService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Liga, desliga, estende o bloqueio e cuida de todos os lembretes. */
final class Block {
    static final String CH_STATUS = "status";
    static final String CH_ALERT = "alertas";

    static final int ID_ONGOING = 1;
    static final int ID_SUMMARY = 2;
    static final int ID_REMIND = 3;
    static final int ID_URGENT = 4;

    static final String A_END = "com.gabriel.notileve.END";
    static final String A_REMIND = "com.gabriel.notileve.REMIND";
    static final String A_NAG = "com.gabriel.notileve.NAG";
    static final String A_STOP = "com.gabriel.notileve.STOP";
    static final String A_EXTEND = "com.gabriel.notileve.EXTEND";
    static final String A_KEEP = "com.gabriel.notileve.KEEP";

    private Block() {}

    // ───────────────────────── ações principais ─────────────────────────

    static void start(Context c, int minutes, String label) {
        SharedPreferences sp = Prefs.p(c);
        long now = System.currentTimeMillis();
        long end = minutes > 0 ? now + minutes * 60_000L : 0;
        sp.edit()
                .putBoolean(Prefs.ACTIVE, true)
                .putLong(Prefs.START, now)
                .putLong(Prefs.END, end)
                .putString(Prefs.LABEL, label)
                .putInt(Prefs.LAST_MIN, minutes)
                .putInt(Prefs.SESS_BLOCKED, 0)
                .putBoolean(Prefs.OVERTIME, false)
                .apply();
        nm(c).cancel(ID_SUMMARY);
        applyDnd(c, true);
        scheduleAll(c);
        postOngoing(c);
        ShieldService.sweepNow(c);
        refreshTile(c);
    }

    static void extend(Context c, int minutes) {
        SharedPreferences sp = Prefs.p(c);
        if (!sp.getBoolean(Prefs.ACTIVE, false)) return;
        long now = System.currentTimeMillis();
        long end = sp.getLong(Prefs.END, 0);
        if (end == 0) {
            // modo "até eu desligar": +X vira um prazo de verdade
            end = now + minutes * 60_000L;
        } else if (sp.getBoolean(Prefs.OVERTIME, false) || end < now) {
            end = now + minutes * 60_000L;
        } else {
            end += minutes * 60_000L;
        }
        sp.edit().putLong(Prefs.END, end).putBoolean(Prefs.OVERTIME, false).apply();
        nm(c).cancel(ID_REMIND);
        applyDnd(c, true);
        scheduleAll(c);
        postOngoing(c);
        refreshTile(c);
    }

    static void stop(Context c, boolean natural) {
        SharedPreferences sp = Prefs.p(c);
        if (!sp.getBoolean(Prefs.ACTIVE, false)) return;
        long now = System.currentTimeMillis();
        long start = sp.getLong(Prefs.START, now);
        long dur = Math.max(0, now - start);
        int blocked = sp.getInt(Prefs.SESS_BLOCKED, 0);
        sp.edit()
                .putBoolean(Prefs.ACTIVE, false)
                .putBoolean(Prefs.OVERTIME, false)
                .putLong(Prefs.ST_TIME, sp.getLong(Prefs.ST_TIME, 0) + dur)
                .putInt(Prefs.ST_SESS, sp.getInt(Prefs.ST_SESS, 0) + 1)
                .putInt(Prefs.ST_BLOCKED, sp.getInt(Prefs.ST_BLOCKED, 0) + blocked)
                .apply();
        applyDnd(c, false);
        cancelAlarms(c);
        NotificationManager nm = nm(c);
        nm.cancel(ID_ONGOING);
        nm.cancel(ID_REMIND);

        String title = natural ? "✓ Bloqueio concluído" : "Bloqueio encerrado";
        String text;
        if (blocked == 0) {
            text = "Durou " + human(dur) + ". Nenhuma notificação chegou — paz total.";
        } else {
            String apps = Vault.summary(c, start, 3);
            text = "Durou " + human(dur) + " · " + blocked + (blocked == 1 ? " notificação guardada" : " notificações guardadas")
                    + " no cofre" + (apps.isEmpty() ? "" : " (" + apps + ")") + ". Toque para ver.";
        }
        Notification.Builder b = builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openApp(c, 1))
                .setAutoCancel(true)
                .setColor(0xFF5EEAD4);
        notify(c, ID_SUMMARY, b.build());
        refreshTile(c);
    }

    /** Chamado pelo AlarmReceiver (alarmes e botões das notificações). */
    static void onAction(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        SharedPreferences sp = Prefs.p(c);
        boolean active = sp.getBoolean(Prefs.ACTIVE, false);
        switch (a) {
            case A_STOP:
                stop(c, false);
                break;
            case A_EXTEND:
                extend(c, i.getIntExtra("min", 15));
                break;
            case A_KEEP:
                nm(c).cancel(ID_REMIND);
                break;
            case A_END:
                if (!active) return;
                if (sp.getBoolean(Prefs.AUTO_OFF, true)) {
                    stop(c, true);
                } else {
                    sp.edit().putBoolean(Prefs.OVERTIME, true).apply();
                    postOngoing(c);
                    alert(c, "⏰ O tempo acabou!",
                            "Seu bloqueio ainda está ligado. Quer desativar agora?", true);
                    setAlarm(c, A_NAG, System.currentTimeMillis() + 5 * 60_000L);
                    refreshTile(c);
                }
                break;
            case A_REMIND:
                if (!active) return;
                long end = sp.getLong(Prefs.END, 0);
                long left = Math.max(60_000L, end - System.currentTimeMillis());
                alert(c, "Faltam " + human(left),
                        "O silêncio termina às " + clock(end) + ". Precisa de mais tempo?", false);
                break;
            case A_NAG:
                if (!active) return;
                long start = sp.getLong(Prefs.START, System.currentTimeMillis());
                boolean over = sp.getBoolean(Prefs.OVERTIME, false);
                String t = over ? "⏰ Ainda bloqueando (tempo esgotado)"
                        : "🔕 Silêncio há " + human(System.currentTimeMillis() - start);
                int held = sp.getInt(Prefs.SESS_BLOCKED, 0);
                String x = (held > 0 ? held + " no cofre te esperando. " : "") + "Desativar o bloqueio?";
                alert(c, t, x, true);
                int mins = over ? 5 : Math.max(5, sp.getInt(Prefs.NAG, 30));
                setAlarm(c, A_NAG, System.currentTimeMillis() + mins * 60_000L);
                break;
        }
    }

    /** Depois de reiniciar o celular. */
    static void restore(Context c) {
        SharedPreferences sp = Prefs.p(c);
        if (!sp.getBoolean(Prefs.ACTIVE, false)) return;
        long end = sp.getLong(Prefs.END, 0);
        if (end > 0 && end <= System.currentTimeMillis() && !sp.getBoolean(Prefs.OVERTIME, false)) {
            onAction(c, new Intent(A_END));
            return;
        }
        applyDnd(c, true);
        scheduleAll(c);
        postOngoing(c);
    }

    // ───────────────────────── notificações ─────────────────────────

    static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = nm(c);
        NotificationChannel s = new NotificationChannel(CH_STATUS, "Status do bloqueio",
                NotificationManager.IMPORTANCE_LOW);
        s.setDescription("Contador fixo enquanto o silêncio está ligado");
        s.setShowBadge(false);
        nm.createNotificationChannel(s);
        NotificationChannel a = new NotificationChannel(CH_ALERT, "Lembretes",
                NotificationManager.IMPORTANCE_HIGH);
        a.setDescription("Avisos de fim, lembretes para desativar e urgências");
        a.enableVibration(true);
        a.setVibrationPattern(new long[]{0, 120, 80, 120});
        nm.createNotificationChannel(a);
    }

    static void postOngoing(Context c) {
        SharedPreferences sp = Prefs.p(c);
        if (!sp.getBoolean(Prefs.ACTIVE, false)) return;
        long end = sp.getLong(Prefs.END, 0);
        boolean over = sp.getBoolean(Prefs.OVERTIME, false);
        int held = sp.getInt(Prefs.SESS_BLOCKED, 0);
        String label = sp.getString(Prefs.LABEL, "Silêncio");

        String text;
        if (over) text = "Tempo esgotado — desative quando quiser";
        else if (end > 0) text = "Termina às " + clock(end);
        else text = "Sem hora pra acabar · vou te lembrar";
        if (held > 0) text += " · " + held + " no cofre";

        Notification.Builder b = builder(c, CH_STATUS)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("🔕 " + label)
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp(c, 0))
                .setColor(0xFFA78BFA)
                .setCategory(Notification.CATEGORY_STATUS);
        if (end > 0 && !over && Build.VERSION.SDK_INT >= 24) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setWhen(end).setShowWhen(true);
        } else {
            b.setShowWhen(false);
        }
        if (!over && end > 0) b.addAction(action(c, "+15 min", extendIntent(c, 15)));
        b.addAction(action(c, "Encerrar", broadcast(c, A_STOP, 0)));
        notify(c, ID_ONGOING, b.build());
    }

    private static void alert(Context c, String title, String text, boolean offerStop) {
        Notification.Builder b = builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(openApp(c, 0))
                .setAutoCancel(true)
                .setColor(0xFFFBBF24)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL);
        if (offerStop) {
            b.addAction(action(c, "Desativar", broadcast(c, A_STOP, 0)));
            b.addAction(action(c, "Mais 30 min", extendIntent(c, 30)));
            b.addAction(action(c, "Deixar", broadcast(c, A_KEEP, 0)));
        } else {
            b.addAction(action(c, "+15 min", extendIntent(c, 15)));
            b.addAction(action(c, "Encerrar já", broadcast(c, A_STOP, 0)));
        }
        notify(c, ID_REMIND, b.build());
    }

    static void postUrgent(Context c, String app) {
        String text = app + " mandou várias notificações seguidas. Liberei essa — pode ser importante.";
        Notification.Builder b = builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("⚡ Pulso de urgência: " + app)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setColor(0xFFF87171)
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .addAction(action(c, "Desativar bloqueio", broadcast(c, A_STOP, 0)));
        notify(c, ID_URGENT, b.build());
    }

    @SuppressWarnings("deprecation")
    private static Notification.Builder builder(Context c, String ch) {
        ensureChannels(c);
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, ch) : new Notification.Builder(c);
    }

    private static Notification.Action action(Context c, String title, PendingIntent pi) {
        return new Notification.Action.Builder(
                Icon.createWithResource(c, R.drawable.ic_shield), title, pi).build();
    }

    private static void notify(Context c, int id, Notification n) {
        try {
            nm(c).notify(id, n);
        } catch (SecurityException ignored) {
            // sem permissão de notificação (Android 13+)
        }
    }

    // ───────────────────────── alarmes ─────────────────────────

    private static void scheduleAll(Context c) {
        cancelAlarms(c);
        SharedPreferences sp = Prefs.p(c);
        long now = System.currentTimeMillis();
        long end = sp.getLong(Prefs.END, 0);
        if (end > 0) {
            setAlarm(c, A_END, end);
            int before = sp.getInt(Prefs.BEFORE, 5);
            long at = end - before * 60_000L;
            if (before > 0 && at > now + 30_000L) setAlarm(c, A_REMIND, at);
        } else {
            int nag = Math.max(5, sp.getInt(Prefs.NAG, 30));
            setAlarm(c, A_NAG, now + nag * 60_000L);
        }
    }

    private static void cancelAlarms(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(broadcast(c, A_END, 0));
        am.cancel(broadcast(c, A_REMIND, 0));
        am.cancel(broadcast(c, A_NAG, 0));
    }

    private static void setAlarm(Context c, String action, long at) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = broadcast(c, action, 0);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    static boolean exactAlarmsOk(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    private static int req(String action) {
        switch (action) {
            case A_END: return 10;
            case A_REMIND: return 11;
            case A_NAG: return 12;
            case A_STOP: return 13;
            case A_KEEP: return 14;
            default: return 20;
        }
    }

    private static PendingIntent broadcast(Context c, String action, int min) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action).putExtra("min", min);
        return PendingIntent.getBroadcast(c, req(action), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent extendIntent(Context c, int min) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(A_EXTEND).putExtra("min", min);
        return PendingIntent.getBroadcast(c, 100 + min, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent openApp(Context c, int tab) {
        Intent i = new Intent(c, MainActivity.class).putExtra("tab", tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 50 + tab, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ───────────────────────── Não Perturbe ─────────────────────────

    static boolean dndAccess(Context c) {
        return nm(c).isNotificationPolicyAccessGranted();
    }

    private static void applyDnd(Context c, boolean on) {
        SharedPreferences sp = Prefs.p(c);
        NotificationManager nm = nm(c);
        if (!nm.isNotificationPolicyAccessGranted()) return;
        try {
            if (on) {
                if (!sp.getBoolean(Prefs.USE_DND, false) || sp.getBoolean(Prefs.DND_SET, false)) return;
                sp.edit().putInt(Prefs.DND_PREV, nm.getCurrentInterruptionFilter())
                        .putBoolean(Prefs.DND_SET, true).apply();
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            } else if (sp.getBoolean(Prefs.DND_SET, false)) {
                int prev = sp.getInt(Prefs.DND_PREV, NotificationManager.INTERRUPTION_FILTER_ALL);
                if (prev == NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                    prev = NotificationManager.INTERRUPTION_FILTER_ALL;
                nm.setInterruptionFilter(prev);
                sp.edit().putBoolean(Prefs.DND_SET, false).apply();
            }
        } catch (Exception ignored) {}
    }

    // ───────────────────────── utilidades ─────────────────────────

    static boolean listenerAccess(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(new ComponentName(c, ShieldService.class).flattenToString());
    }

    static void refreshTile(Context c) {
        if (Build.VERSION.SDK_INT >= 24) {
            try {
                TileService.requestListeningState(c, new ComponentName(c, NotiTile.class));
            } catch (Exception ignored) {}
        }
    }

    static NotificationManager nm(Context c) {
        return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    static String clock(long t) {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(t));
    }

    static String human(long ms) {
        long m = Math.max(0, (ms + 30_000L) / 60_000L);
        if (m < 1) return "menos de 1 min";
        if (m < 60) return m + " min";
        long h = m / 60, r = m % 60;
        return r == 0 ? h + "h" : h + "h " + r + "min";
    }
}
