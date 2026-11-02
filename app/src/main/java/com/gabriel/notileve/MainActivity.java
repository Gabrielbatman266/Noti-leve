package com.gabriel.notileve;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MainActivity extends Activity {
    static final int BG = 0xFF000000, CARD = 0xFF0C0D12, LINE = 0xFF1C1E27,
            TEXT = 0xFFECEFF7, SUB = 0xFF8A90A2, ACC = 0xFF5EEAD4, ACC2 = 0xFFA78BFA,
            WARN = 0xFFFBBF24, RED = 0xFFF87171;

    private static final Object[][] PRESETS = {
            {"🍅", "Foco", 25}, {"📚", "Estudo", 50},
            {"🎮", "Jogo", 60}, {"🎬", "Filme", 120},
            {"🧘", "Respiro", 10}, {"😴", "Sono", 480},
    };

    private float d;
    private LinearLayout body;
    private final TextView[] tabs = new TextView[3];
    private int tab = 0;
    private RingView ring;
    private int pickMin = 25;
    private TextView pickLabel;
    private boolean shownActive, shownOver;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (tab == 0) {
                SharedPreferences sp = Prefs.p(MainActivity.this);
                boolean a = sp.getBoolean(Prefs.ACTIVE, false);
                boolean o = sp.getBoolean(Prefs.OVERTIME, false);
                if (a != shownActive || o != shownOver) show(0);
                else updateRing();
            }
            h.postDelayed(this, 1000);
        }
    };

    // ───────────────────────── ciclo de vida ─────────────────────────

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;
        Block.ensureChannels(this);
        int last = Prefs.p(this).getInt(Prefs.LAST_MIN, 25);
        pickMin = last > 0 ? last : 25;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(22), dp(20), dp(6));
        TextView title = tv("Noti Leve", 26, TEXT, true);
        title.setLetterSpacing(0.02f);
        head.addView(title);
        head.addView(tv("silêncio com hora pra acabar", 13, SUB, false));
        root.addView(head);

        LinearLayout tabRow = new LinearLayout(this);
        tabRow.setPadding(dp(14), dp(8), dp(14), dp(8));
        String[] names = {"Silêncio", "Cofre", "Ajustes"};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            TextView t = tv(names[i], 14, SUB, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            t.setOnClickListener(v -> show(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabRow.addView(t, lp);
            tabs[i] = t;
        }
        root.addView(tabRow);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(6), dp(16), dp(28));
        sv.addView(body);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        tab = getIntent().getIntExtra("tab", 0);

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        show(i.getIntExtra("tab", tab));
    }

    @Override
    protected void onResume() {
        super.onResume();
        show(tab);
        h.removeCallbacks(tick);
        h.postDelayed(tick, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private void show(int t) {
        tab = t;
        for (int i = 0; i < 3; i++) {
            boolean on = i == t;
            tabs[i].setTextColor(on ? BG : SUB);
            tabs[i].setBackground(round(on ? ACC : CARD, 30, on ? 0 : LINE));
        }
        int n = Vault.all(this).length();
        tabs[1].setText(n > 0 ? "Cofre · " + n : "Cofre");
        body.removeAllViews();
        ring = null;
        if (t == 0) buildSilence();
        else if (t == 1) buildVault();
        else buildSettings();
    }

    // ───────────────────────── aba Silêncio ─────────────────────────

    private void buildSilence() {
        SharedPreferences sp = Prefs.p(this);
        boolean active = sp.getBoolean(Prefs.ACTIVE, false);
        boolean over = sp.getBoolean(Prefs.OVERTIME, false);
        shownActive = active;
        shownOver = over;

        ring = new RingView(this);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2);
        rlp.setMargins(0, dp(6), 0, dp(14));
        body.addView(ring, rlp);
        updateRing();

        if (!Block.listenerAccess(this)) {
            LinearLayout w = card();
            w.setBackground(round(0xFF1A1405, 18, 0xFF3A2C08));
            w.addView(tv("⚠ Falta uma permissão", 15, WARN, true));
            w.addView(spaced(tv("Para segurar as notificações eu preciso de \"Acesso às notificações\". Nada sai do seu celular.", 13, SUB, false), 4));
            TextView go = pill("Dar acesso", BG, WARN);
            go.setOnClickListener(v -> openListenerSettings());
            w.addView(spaced(go, 12));
            body.addView(w);
        }

        if (active) buildActiveControls(sp, over);
        else buildPicker(sp);

        long tot = sp.getLong(Prefs.ST_TIME, 0);
        int ses = sp.getInt(Prefs.ST_SESS, 0);
        if (ses > 0) {
            TextView st = tv("Você já ficou " + Block.human(tot) + " em silêncio, em " + ses
                    + (ses == 1 ? " sessão" : " sessões") + ", e segurou "
                    + sp.getInt(Prefs.ST_BLOCKED, 0) + " notificações.", 12, SUB, false);
            st.setGravity(Gravity.CENTER);
            body.addView(spaced(st, 18));
        }
    }

    private void buildActiveControls(SharedPreferences sp, boolean over) {
        if (over) {
            TextView off = pill("Desativar agora", BG, RED);
            off.setTextSize(17);
            off.setPadding(0, dp(16), 0, dp(16));
            off.setOnClickListener(v -> { Block.stop(this, false); show(0); });
            body.addView(off);
            body.addView(spaced(tv("O tempo acabou, mas você pediu só um lembrete. Desative quando estiver pronto.", 12, SUB, false), 8));
        }

        boolean free = sp.getLong(Prefs.END, 0) == 0 && !over;
        LinearLayout row = new LinearLayout(this);
        int[] adds = free ? new int[0] : over ? new int[]{15, 30, 60} : new int[]{5, 15, 30};
        for (int m : adds) {
            TextView b = pill("+" + m + " min", TEXT, CARD);
            b.setBackground(ripple(round(CARD, 30, LINE)));
            b.setOnClickListener(v -> { Block.extend(this, m); show(0); toast("+" + m + " minutos de silêncio"); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(4), 0, dp(4), 0);
            row.addView(b, lp);
        }
        if (!free) body.addView(spaced(row, over ? 14 : 0));

        if (!over) {
            TextView stop = pill("Segure para encerrar", TEXT, CARD);
            stop.setBackground(ripple(round(0xFF16080A, 30, 0xFF4A1C20)));
            stop.setTextColor(RED);
            stop.setOnClickListener(v -> toast("Segure o botão para encerrar — evita toque sem querer 😉"));
            stop.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Block.stop(this, false);
                show(0);
                return true;
            });
            body.addView(spaced(stop, 12));
        }

        LinearLayout info = card();
        int held = sp.getInt(Prefs.SESS_BLOCKED, 0);
        info.addView(tv(held == 0 ? "Nada chegou ainda. Aproveite. 🌙"
                : held + (held == 1 ? " notificação segurada" : " notificações seguradas") + " nesta sessão", 14, TEXT, true));
        String apps = Vault.summary(this, sp.getLong(Prefs.START, 0), 4);
        if (!apps.isEmpty()) info.addView(spaced(tv(apps, 13, SUB, false), 4));
        Set<String> wl = Prefs.whitelist(this);
        if (!wl.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (String p : wl) names.add(Prefs.appLabel(this, p));
            Collections.sort(names);
            info.addView(spaced(tv("Liberados: " + TextUtils.join(", ", names), 12, ACC, false), 8));
        }
        long end = sp.getLong(Prefs.END, 0);
        String rem;
        if (end == 0) rem = "Vou te lembrar a cada " + sp.getInt(Prefs.NAG, 30) + " min que o silêncio está ligado.";
        else if (sp.getBoolean(Prefs.AUTO_OFF, true)) rem = "Desliga sozinho às " + Block.clock(end) + " e te mostro o resumo.";
        else rem = "Às " + Block.clock(end) + " te aviso — e insisto a cada 5 min até você desativar.";
        info.addView(spaced(tv(rem, 12, SUB, false), 8));
        info.setOnClickListener(v -> show(1));
        body.addView(spaced(info, 14));
    }

    private void buildPicker(SharedPreferences sp) {
        body.addView(spaced(tv("ESCOLHA UM MODO", 11, SUB, true), 2));
        LinearLayout row = null;
        for (int i = 0; i < PRESETS.length; i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                body.addView(spaced(row, 8));
            }
            Object[] p = PRESETS[i];
            row.addView(presetCard((String) p[0], (String) p[1], (Integer) p[2]), weight());
        }
        LinearLayout free = presetCard("♾", "Até eu desligar", 0);
        body.addView(spaced(free, 8));

        // personalizado
        LinearLayout c = card();
        c.addView(tv("TEMPO PERSONALIZADO", 11, SUB, true));
        LinearLayout step = new LinearLayout(this);
        step.setGravity(Gravity.CENTER_VERTICAL);
        TextView minus = pill("−", TEXT, CARD);
        minus.setTextSize(22);
        minus.setBackground(ripple(round(BG, 30, LINE)));
        pickLabel = tv("", 26, TEXT, false);
        pickLabel.setGravity(Gravity.CENTER);
        TextView plus = pill("+", TEXT, CARD);
        plus.setTextSize(22);
        plus.setBackground(ripple(round(BG, 30, LINE)));
        minus.setOnClickListener(v -> setPick(pickMin - (pickMin > 60 ? 15 : 5)));
        plus.setOnClickListener(v -> setPick(pickMin + (pickMin >= 60 ? 15 : 5)));
        minus.setOnLongClickListener(v -> { setPick(pickMin - 60); return true; });
        plus.setOnLongClickListener(v -> { setPick(pickMin + 60); return true; });
        step.addView(minus, new LinearLayout.LayoutParams(dp(56), dp(52)));
        step.addView(pickLabel, new LinearLayout.LayoutParams(0, -2, 1));
        step.addView(plus, new LinearLayout.LayoutParams(dp(56), dp(52)));
        c.addView(spaced(step, 10));
        setPick(pickMin);

        TextView go = pill("Iniciar silêncio  ▶", BG, ACC);
        go.setTextSize(16);
        go.setPadding(0, dp(14), 0, dp(14));
        go.setOnClickListener(v -> begin(pickMin, "Silêncio de " + Block.human(pickMin * 60_000L)));
        c.addView(spaced(go, 12));
        body.addView(spaced(c, 12));

        // ao acabar
        boolean auto = sp.getBoolean(Prefs.AUTO_OFF, true);
        LinearLayout endRow = new LinearLayout(this);
        TextView a = pill("Desligar sozinho", auto ? BG : SUB, auto ? ACC2 : CARD);
        TextView r = pill("Só me lembrar", !auto ? BG : SUB, !auto ? ACC2 : CARD);
        if (auto) r.setBackground(round(CARD, 30, LINE));
        else a.setBackground(round(CARD, 30, LINE));
        a.setOnClickListener(v -> { sp.edit().putBoolean(Prefs.AUTO_OFF, true).apply(); show(0); });
        r.setOnClickListener(v -> { sp.edit().putBoolean(Prefs.AUTO_OFF, false).apply(); show(0); });
        LinearLayout.LayoutParams l1 = weight();
        LinearLayout.LayoutParams l2 = weight();
        endRow.addView(a, l1);
        endRow.addView(r, l2);
        body.addView(spaced(tv("QUANDO O TEMPO ACABAR", 11, SUB, true), 16));
        body.addView(spaced(endRow, 8));
        body.addView(spaced(tv(auto
                ? "As notificações voltam sozinhas e eu te mostro o que ficou no cofre."
                : "O silêncio continua e eu te cutuco a cada 5 min até você desativar.", 12, SUB, false), 6));
    }

    private LinearLayout presetCard(String emoji, String name, int min) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(ripple(round(CARD, 18, LINE)));
        TextView e = tv(emoji + "  " + name, 15, TEXT, true);
        e.setSingleLine(true);
        e.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(e);
        c.addView(spaced(tv(min == 0 ? "sem hora · com lembretes" : Block.human(min * 60_000L), 12, min == 0 ? ACC2 : ACC, false), 2));
        c.setOnClickListener(v -> begin(min, emoji + " " + name));
        return c;
    }

    private void setPick(int m) {
        pickMin = Math.max(5, Math.min(24 * 60, m));
        if (pickLabel != null) pickLabel.setText(Block.human(pickMin * 60_000L));
        updateRing();
    }

    private void begin(int min, String label) {
        if (!Block.listenerAccess(this)) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Preciso de acesso às notificações")
                    .setMessage("É assim que o Noti Leve segura as notificações e guarda no cofre. Ative \"Noti Leve · Escudo\" na próxima tela.")
                    .setPositiveButton("Abrir", (dd, w) -> openListenerSettings())
                    .setNegativeButton("Agora não", null)
                    .show();
            return;
        }
        Block.start(this, min, label);
        String msg = min > 0 ? "Silêncio até " + Block.clock(System.currentTimeMillis() + min * 60_000L)
                : "Silêncio ligado. Vou te lembrar de desligar.";
        toast(msg);
        show(0);
    }

    private void updateRing() {
        if (ring == null) return;
        SharedPreferences sp = Prefs.p(this);
        long now = System.currentTimeMillis();
        if (!sp.getBoolean(Prefs.ACTIVE, false)) {
            ring.set(RingView.MODE_IDLE, 1f, "PRONTO", clockFmt(pickMin * 60_000L), "toque num modo para silenciar");
            return;
        }
        String label = sp.getString(Prefs.LABEL, "Silêncio");
        int held = sp.getInt(Prefs.SESS_BLOCKED, 0);
        String top = label + (held > 0 ? "  ·  " + held + " no cofre" : "");
        long start = sp.getLong(Prefs.START, now);
        long end = sp.getLong(Prefs.END, 0);
        if (sp.getBoolean(Prefs.OVERTIME, false)) {
            ring.set(RingView.MODE_OVER, 1f, top, "00:00", "tempo esgotado · +" + Block.human(now - end));
        } else if (end == 0) {
            ring.set(RingView.MODE_FREE, 1f, top, clockFmt(now - start), "em silêncio · sem hora");
        } else {
            float p = (end - start) > 0 ? (float) (end - now) / (end - start) : 0f;
            ring.set(RingView.MODE_TIMED, p, top, clockFmt(end - now), "termina às " + Block.clock(end));
        }
    }

    // ───────────────────────── aba Cofre ─────────────────────────

    private void buildVault() {
        JSONArray a = Vault.all(this);
        LinearLayout head = card();
        head.addView(tv(a.length() == 0 ? "Cofre vazio" : a.length() + (a.length() == 1 ? " notificação guardada" : " notificações guardadas"), 17, TEXT, true));
        if (a.length() == 0) {
            head.addView(spaced(tv("Enquanto o silêncio estiver ligado, as notificações vêm parar aqui em vez de te interromper. Nada se perde.", 13, SUB, false), 6));
        } else {
            String s = Vault.summary(this, 0, 6);
            head.addView(spaced(tv(s, 13, ACC, false), 6));
            head.addView(spaced(tv("Toque para abrir o app · segure para apagar", 11, SUB, false), 6));
        }
        body.addView(head);

        long now = System.currentTimeMillis();
        for (int i = 0; i < a.length(); i++) {
            JSONObject e = a.optJSONObject(i);
            if (e == null) continue;
            final int idx = i;
            final String pkg = e.optString("p");
            LinearLayout c = card();
            c.setBackground(ripple(round(CARD, 18, LINE)));
            LinearLayout top = new LinearLayout(this);
            TextView app = tv(e.optString("a"), 12, ACC, true);
            top.addView(app, new LinearLayout.LayoutParams(0, -2, 1));
            top.addView(tv(ago(now - e.optLong("w")), 11, SUB, false));
            c.addView(top);
            String t = e.optString("t");
            if (!t.isEmpty()) c.addView(spaced(tv(t, 15, TEXT, true), 4));
            String x = e.optString("x");
            if (!x.isEmpty()) {
                TextView xv = tv(x, 13, SUB, false);
                xv.setMaxLines(3);
                xv.setEllipsize(TextUtils.TruncateAt.END);
                c.addView(spaced(xv, 2));
            }
            c.setOnClickListener(v -> {
                Intent li = getPackageManager().getLaunchIntentForPackage(pkg);
                if (li != null) startActivity(li);
                else toast("Não consegui abrir esse app");
            });
            c.setOnLongClickListener(v -> {
                v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                Vault.remove(this, idx);
                show(1);
                return true;
            });
            body.addView(spaced(c, 8));
        }

        if (a.length() > 0) {
            TextView clear = pill("Esvaziar cofre", RED, CARD);
            clear.setBackground(ripple(round(0xFF16080A, 30, 0xFF4A1C20)));
            clear.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("Esvaziar o cofre?")
                    .setMessage("Apaga a lista de notificações guardadas. Os apps não são afetados.")
                    .setPositiveButton("Esvaziar", (dd, w) -> { Vault.clear(this); show(1); })
                    .setNegativeButton("Cancelar", null)
                    .show());
            body.addView(spaced(clear, 16));
        }
    }

    // ───────────────────────── aba Ajustes ─────────────────────────

    private void buildSettings() {
        SharedPreferences sp = Prefs.p(this);

        section("PERMISSÕES");
        LinearLayout perm = card();
        perm.addView(permRow("Acesso às notificações", "Essencial — é o escudo que segura tudo",
                Block.listenerAccess(this), v -> openListenerSettings()));
        perm.addView(divider());
        perm.addView(permRow("Não Perturbe do sistema", "Opcional — corta até o som de chegada",
                Block.dndAccess(this), v -> safeStart(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))));
        if (Build.VERSION.SDK_INT >= 31) {
            perm.addView(divider());
            perm.addView(permRow("Alarmes no minuto exato", "Para o fim e os lembretes não atrasarem",
                    Block.exactAlarmsOk(this), v -> safeStart(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())))));
        }
        if (Build.VERSION.SDK_INT >= 33) {
            boolean ok = checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
            perm.addView(divider());
            perm.addView(permRow("Mostrar meus lembretes", "Sem isso eu não consigo te avisar",
                    ok, v -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1)));
        }
        body.addView(perm);

        section("COMO BLOQUEAR");
        LinearLayout how = card();
        how.addView(switchRow("Silenciar o sistema junto", "Liga o Não Perturbe durante o bloqueio e devolve como estava depois",
                Prefs.USE_DND, false, () -> {
                    if (!Block.dndAccess(this)) {
                        toast("Dê a permissão de Não Perturbe primeiro");
                        safeStart(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    }
                }));
        how.addView(divider());
        how.addView(switchRow("Chamadas e alarmes passam", "Ligações e despertador nunca são bloqueados", Prefs.CALLS, true, null));
        how.addView(divider());
        how.addView(switchRow("⚡ Pulso de urgência", "Se o mesmo app insistir 3 vezes em 3 min, eu deixo passar e te aviso", Prefs.URGENCY, true, null));
        how.addView(divider());
        how.addView(switchRow("Limpar a barra ao iniciar", "Guarda no cofre o que já estava lá quando o silêncio começa", Prefs.SWEEP, true, null));
        how.addView(divider());
        int wl = Prefs.whitelist(this).size();
        how.addView(actionRow("Apps liberados", wl == 0 ? "Nenhum — tudo é segurado" : wl + (wl == 1 ? " app passa direto" : " apps passam direto"),
                v -> pickWhitelist()));
        body.addView(how);

        section("LEMBRETES PARA DESATIVAR");
        LinearLayout rem = card();
        rem.addView(switchRow("Desligar sozinho no fim", "Desligado: o silêncio continua e eu insisto a cada 5 min até você desativar",
                Prefs.AUTO_OFF, true, null));
        rem.addView(divider());
        rem.addView(cycleRow("Avisar antes do fim", Prefs.BEFORE, new int[]{0, 1, 5, 10, 15}, 5,
                m -> m == 0 ? "não avisar" : m + " min antes"));
        rem.addView(divider());
        rem.addView(cycleRow("Modo sem hora: lembrar a cada", Prefs.NAG, new int[]{15, 30, 60, 120}, 30,
                m -> Block.human(m * 60_000L)));
        body.addView(rem);

        section("ATALHO RÁPIDO");
        LinearLayout tile = card();
        tile.addView(tv("Puxe a barra de notificações, toque no lápis e arraste o bloco \"Silêncio\". Um toque liga com o último tempo usado, outro toque desliga.", 13, SUB, false));
        body.addView(tile);

        section("SOBRE");
        LinearLayout about = card();
        about.addView(tv("Noti Leve 1.0 · leve, sem anúncios, sem internet", 13, TEXT, false));
        about.addView(spaced(tv("Silêncio total: " + Block.human(sp.getLong(Prefs.ST_TIME, 0))
                + " · " + sp.getInt(Prefs.ST_BLOCKED, 0) + " notificações seguradas", 12, SUB, false), 4));
        TextView up = pill("Procurar atualização", ACC, CARD);
        up.setBackground(ripple(round(BG, 30, LINE)));
        up.setOnClickListener(v -> safeStart(new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://github.com/Gabrielbatman266/Noti-leve/releases/latest"))));
        about.addView(spaced(up, 12));
        body.addView(about);
    }

    private void pickWhitelist() {
        PackageManager pm = getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        TreeMap<String, String> apps = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getPackageName())) continue;
            apps.put(ri.loadLabel(pm).toString() + "\u0000" + p, p);
        }
        final List<String> pkgs = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (Map.Entry<String, String> e : apps.entrySet()) {
            pkgs.add(e.getValue());
            labels.add(e.getKey().substring(0, e.getKey().indexOf('\u0000')));
        }
        final Set<String> wl = Prefs.whitelist(this);
        boolean[] checked = new boolean[pkgs.size()];
        for (int k = 0; k < pkgs.size(); k++) checked[k] = wl.contains(pkgs.get(k));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Quem pode furar o silêncio?")
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dd, w, on) -> {
                    if (on) wl.add(pkgs.get(w));
                    else wl.remove(pkgs.get(w));
                })
                .setPositiveButton("Salvar", (dd, w) -> { Prefs.setWhitelist(this, wl); show(2); })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ───────────────────────── peças de UI ─────────────────────────

    interface Fmt { String f(int v); }

    private View switchRow(String title, String desc, String key, boolean def, Runnable onEnable) {
        SharedPreferences sp = Prefs.p(this);
        LinearLayout r = row();
        LinearLayout col = textCol(title, desc);
        Switch sw = new Switch(this);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, on) -> {
            sp.edit().putBoolean(key, on).apply();
            if (on && onEnable != null) onEnable.run();
        });
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(sw);
        r.setOnClickListener(v -> sw.toggle());
        return r;
    }

    private View cycleRow(String title, String key, int[] vals, int def, Fmt fmt) {
        SharedPreferences sp = Prefs.p(this);
        LinearLayout r = row();
        r.addView(textCol(title, null), new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = tv(fmt.f(sp.getInt(key, def)), 13, BG, true);
        val.setPadding(dp(12), dp(6), dp(12), dp(6));
        val.setBackground(round(ACC2, 30, 0));
        r.addView(val);
        r.setOnClickListener(v -> {
            int cur = sp.getInt(key, def), next = vals[0];
            for (int k = 0; k < vals.length; k++) if (vals[k] == cur) next = vals[(k + 1) % vals.length];
            sp.edit().putInt(key, next).apply();
            val.setText(fmt.f(next));
        });
        return r;
    }

    private View permRow(String title, String desc, boolean ok, View.OnClickListener go) {
        LinearLayout r = row();
        r.addView(textCol(title, desc), new LinearLayout.LayoutParams(0, -2, 1));
        TextView s = tv(ok ? "✓ ok" : "ativar", 13, ok ? ACC : BG, true);
        s.setPadding(dp(12), dp(6), dp(12), dp(6));
        s.setBackground(round(ok ? 0xFF0B1E1B : WARN, 30, 0));
        r.addView(s);
        if (!ok) r.setOnClickListener(go);
        return r;
    }

    private View actionRow(String title, String desc, View.OnClickListener go) {
        LinearLayout r = row();
        r.addView(textCol(title, desc), new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(tv("›", 22, SUB, false));
        r.setOnClickListener(go);
        return r;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        return r;
    }

    private LinearLayout textCol(String title, String desc) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, 0, dp(12), 0);
        col.addView(tv(title, 15, TEXT, false));
        if (desc != null) col.addView(spaced(tv(desc, 12, SUB, false), 2));
        return col;
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2)));
        return v;
    }

    private void section(String s) {
        body.addView(spaced(tv(s, 11, SUB, true), 18));
        View gap = new View(this);
        body.addView(gap, new LinearLayout.LayoutParams(1, dp(8)));
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(round(CARD, 18, LINE));
        return c;
    }

    private TextView tv(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private TextView pill(String s, int fg, int bg) {
        TextView t = tv(s, 14, fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(12), dp(14), dp(12));
        t.setBackground(ripple(round(bg, 30, 0)));
        return t;
    }

    private GradientDrawable round(int color, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(Math.max(1, dp(1)), stroke);
        return g;
    }

    private RippleDrawable ripple(GradientDrawable g) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), g, null);
    }

    private <V extends View> V spaced(V v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        v.setLayoutParams(lp);
        return v;
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMargins(dp(4), 0, dp(4), 0);
        return lp;
    }

    private int dp(float v) { return Math.round(v * d); }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private void safeStart(Intent i) {
        try {
            startActivity(i);
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); } catch (Exception ignored) {}
        }
    }

    private void openListenerSettings() {
        safeStart(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
    }

    private static String clockFmt(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%02d:%02d", m, sec);
    }

    private static String ago(long ms) {
        long m = ms / 60_000L;
        if (m < 1) return "agora";
        if (m < 60) return "há " + m + " min";
        long h = m / 60;
        if (h < 24) return "há " + h + "h";
        return "há " + (h / 24) + "d";
    }
}
