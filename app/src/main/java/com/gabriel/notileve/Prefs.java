package com.gabriel.notileve;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/** Todas as configurações e o estado do bloqueio ficam aqui. */
final class Prefs {
    // Estado
    static final String ACTIVE = "active";
    static final String START = "start";
    static final String END = "end";            // 0 = sem hora pra acabar
    static final String LABEL = "label";
    static final String LAST_MIN = "lastMin";
    static final String SESS_BLOCKED = "sessBlocked";
    static final String OVERTIME = "overtime";  // tempo acabou mas usuário pediu só lembrete
    static final String DND_SET = "dndSet";
    static final String DND_PREV = "dndPrev";

    // Ajustes
    static final String USE_DND = "useDnd";
    static final String CALLS = "calls";
    static final String URGENCY = "urgency";
    static final String SWEEP = "sweep";
    static final String AUTO_OFF = "autoOff";
    static final String BEFORE = "before";
    static final String NAG = "nag";
    static final String WHITELIST = "whitelist";

    // Estatísticas
    static final String ST_TIME = "stTime";
    static final String ST_SESS = "stSess";
    static final String ST_BLOCKED = "stBlocked";

    private Prefs() {}

    static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("noti_leve", Context.MODE_PRIVATE);
    }

    static boolean active(Context c) { return p(c).getBoolean(ACTIVE, false); }

    static Set<String> whitelist(Context c) {
        return new HashSet<>(p(c).getStringSet(WHITELIST, new HashSet<>()));
    }

    static void setWhitelist(Context c, Set<String> s) {
        p(c).edit().putStringSet(WHITELIST, new HashSet<>(s)).apply();
    }

    private static final HashMap<String, String> labels = new HashMap<>();

    static String appLabel(Context c, String pkg) {
        String l = labels.get(pkg);
        if (l != null) return l;
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            l = pm.getApplicationLabel(ai).toString();
        } catch (Exception e) {
            int dot = pkg.lastIndexOf('.');
            l = dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
        labels.put(pkg, l);
        return l;
    }
}
