package com.gabriel.notileve;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** O "cofre": guarda as notificações seguradas durante o bloqueio. */
final class Vault {
    private static final String KEY = "vault";
    private static final int MAX = 150;

    private Vault() {}

    static synchronized JSONArray all(Context c) {
        try {
            return new JSONArray(Prefs.p(c).getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    /** @return true se é uma notificação nova (não só atualização de uma já guardada). */
    static synchronized boolean add(Context c, String key, String pkg, String app,
                                    String title, String text, long when) {
        JSONArray old = all(c);
        JSONArray out = new JSONArray();
        boolean isNew = true;
        try {
            JSONObject o = new JSONObject();
            o.put("k", key);
            o.put("p", pkg);
            o.put("a", app);
            o.put("t", title);
            o.put("x", text);
            o.put("w", when);
            out.put(o);
            for (int i = 0; i < old.length() && out.length() < MAX; i++) {
                JSONObject e = old.getJSONObject(i);
                if (key.equals(e.optString("k"))) {
                    isNew = false;
                    continue;
                }
                out.put(e);
            }
        } catch (Exception ignored) {}
        Prefs.p(c).edit().putString(KEY, out.toString()).apply();
        return isNew;
    }

    static synchronized void remove(Context c, int index) {
        JSONArray old = all(c);
        JSONArray out = new JSONArray();
        for (int i = 0; i < old.length(); i++) if (i != index) out.put(old.opt(i));
        Prefs.p(c).edit().putString(KEY, out.toString()).apply();
    }

    static synchronized void clear(Context c) {
        Prefs.p(c).edit().putString(KEY, "[]").apply();
    }

    /** Contagem por app desde um instante, do maior pro menor. */
    static List<Map.Entry<String, Integer>> countByApp(Context c, long since) {
        JSONArray a = all(c);
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null || e.optLong("w") < since) continue;
            String app = e.optString("a");
            Integer v = m.get(app);
            m.put(app, v == null ? 1 : v + 1);
        }
        List<Map.Entry<String, Integer>> l = new ArrayList<>(m.entrySet());
        Collections.sort(l, (x, y) -> y.getValue() - x.getValue());
        return l;
    }

    static String summary(Context c, long since, int max) {
        List<Map.Entry<String, Integer>> l = countByApp(c, since);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size() && i < max; i++) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(l.get(i).getKey()).append(' ').append(l.get(i).getValue());
        }
        if (l.size() > max) sb.append("…");
        return sb.toString();
    }
}
