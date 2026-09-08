package de.pierre.ebesuchermonitor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String PREFS = "ebesucher_monitor";
    private static final String PREF_USER = "username";
    private static final String PREF_KEY = "api_key";
    private static final String PREF_AUTO = "auto_refresh";
    private static final String PREF_LAST_SUCCESS = "last_success";

    private static final long REFRESH_INTERVAL_MS = 120_000L;
    private static final double BTP_EPSILON = 0.005;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TextView[] surfName = new TextView[2];
    private final TextView[] surfStatus = new TextView[2];
    private final TextView[] surfToday = new TextView[2];
    private final TextView[] surfTenMinutes = new TextView[2];
    private final TextView[] surfLast = new TextView[2];

    private EditText usernameInput;
    private EditText apiKeyInput;
    private CheckBox autoRefresh;
    private Button refreshButton;
    private TextView connectionStatus;
    private TextView totalToday;
    private TextView rateLimit;
    private TextView updatedAt;

    private volatile boolean refreshing;
    private boolean resumed;
    private int lastServerRemaining = -1;
    private String lastServerLimit = "";

    private final Runnable autoRefreshTask = new Runnable() {
        @Override
        public void run() {
            if (!resumed) {
                return;
            }
            if (autoRefresh != null && autoRefresh.isChecked()) {
                refreshData(false);
            }
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final Runnable rateCountdownTask = new Runnable() {
        @Override
        public void run() {
            if (!resumed) {
                return;
            }
            updateRefreshAvailability();
            handler.postDelayed(this, 1_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        loadSettings();

        if (!usernameInput.getText().toString().trim().isEmpty()
                && !apiKeyInput.getText().toString().trim().isEmpty()) {
            refreshData(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        handler.removeCallbacks(autoRefreshTask);
        handler.removeCallbacks(rateCountdownTask);
        handler.postDelayed(autoRefreshTask, REFRESH_INTERVAL_MS);
        handler.post(rateCountdownTask);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(autoRefreshTask);
        handler.removeCallbacks(rateCountdownTask);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(243, 244, 246));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("eBesucher Monitor", 28, true, Color.rgb(17, 24, 39));
        root.addView(title);

        TextView subtitle = text("Version 0.1.3 · 10-Minuten-BTP-Monitor", 14, false,
                Color.rgb(75, 85, 99));
        subtitle.setPadding(0, dp(3), 0, dp(14));
        root.addView(subtitle);

        LinearLayout credentials = card();
        root.addView(credentials, spacedParams());
        credentials.addView(text("API-Verbindung", 19, true, Color.rgb(17, 24, 39)));
        credentials.addView(text(
                "Benutzername + API-Key. Dein eBesucher-Passwort wird nicht benötigt.",
                13, false, Color.rgb(75, 85, 99)), topMargin(4));

        usernameInput = new EditText(this);
        usernameInput.setHint("eBesucher Benutzername");
        usernameInput.setSingleLine(true);
        usernameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        credentials.addView(usernameInput, topMargin(10));

        apiKeyInput = new EditText(this);
        apiKeyInput.setHint("API-Key");
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        credentials.addView(apiKeyInput, topMargin(4));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        credentials.addView(controls, topMargin(8));

        Button saveButton = new Button(this);
        saveButton.setText("Speichern");
        saveButton.setOnClickListener(v -> {
            saveSettings();
            Toast.makeText(this, "Einstellungen gespeichert", Toast.LENGTH_SHORT).show();
        });
        controls.addView(saveButton, new LinearLayout.LayoutParams(0, dp(48), 1f));

        refreshButton = new Button(this);
        refreshButton.setText("Jetzt prüfen");
        refreshButton.setOnClickListener(v -> refreshData(true));
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        refreshParams.setMargins(dp(8), 0, 0, 0);
        controls.addView(refreshButton, refreshParams);

        autoRefresh = new CheckBox(this);
        autoRefresh.setText("Automatisch alle 2 Minuten aktualisieren");
        autoRefresh.setOnCheckedChangeListener((buttonView, isChecked) -> saveSettings());
        credentials.addView(autoRefresh, topMargin(6));

        connectionStatus = text("Noch nicht geprüft", 14, true, Color.rgb(75, 85, 99));
        credentials.addView(connectionStatus, topMargin(6));

        LinearLayout summary = card();
        root.addView(summary, spacedParams());
        summary.addView(text("Übersicht", 19, true, Color.rgb(17, 24, 39)));
        totalToday = text("Heute gesamt: –", 22, true, Color.rgb(37, 99, 235));
        summary.addView(totalToday, topMargin(8));
        updatedAt = text("Letzte erfolgreiche Aktualisierung: –", 13, false,
                Color.rgb(75, 85, 99));
        summary.addView(updatedAt, topMargin(5));
        rateLimit = text("API-Limit: –", 12, false, Color.rgb(107, 114, 128));
        summary.addView(rateLimit, topMargin(3));

        root.addView(buildSurfbarCard(0), spacedParams());
        root.addView(buildSurfbarCard(1), spacedParams());

        TextView note = text(
                "Statuslogik v0.1.3: Grün bedeutet, dass eBesucher in den letzten 10 Minuten "
                        + "BTP für diesen Surflink gemeldet hat. Orange bedeutet nur: derzeit keine "
                        + "BTP-Gutschrift. Daraus folgt nicht automatisch, dass der Browser gestoppt ist. "
                        + "lastActivity bleibt reine Zusatzinformation.",
                12, false, Color.rgb(75, 85, 99));
        note.setPadding(dp(4), dp(2), dp(4), 0);
        root.addView(note);

        return scroll;
    }

    private LinearLayout buildSurfbarCard(int index) {
        LinearLayout card = card();
        card.addView(text("Surfbar " + (index + 1), 13, true, Color.rgb(107, 114, 128)));

        surfName[index] = text("–", 20, true, Color.rgb(17, 24, 39));
        card.addView(surfName[index], topMargin(4));

        surfStatus[index] = text("● Noch keine Daten", 15, true,
                Color.rgb(107, 114, 128));
        card.addView(surfStatus[index], topMargin(8));

        surfToday[index] = text("Heute: –", 18, true, Color.rgb(17, 24, 39));
        card.addView(surfToday[index], topMargin(10));

        surfTenMinutes[index] = text("Letzte 10 Minuten: –", 14, true,
                Color.rgb(75, 85, 99));
        card.addView(surfTenMinutes[index], topMargin(4));

        surfLast[index] = text("API lastActivity: –", 12, false, Color.rgb(107, 114, 128));
        card.addView(surfLast[index], topMargin(5));
        return card;
    }

    private void refreshData(boolean showToast) {
        if (refreshing) {
            return;
        }

        long localWait = EbesucherApi.secondsUntilFullRefreshAvailable();
        if (localWait > 0L) {
            updateRefreshAvailability();
            if (showToast) {
                Toast.makeText(this,
                        "Nächste vollständige Prüfung in " + localWait + " Sekunden.",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        final String username = usernameInput.getText().toString().trim();
        final String apiKey = apiKeyInput.getText().toString().trim();
        if (username.isEmpty() || apiKey.isEmpty()) {
            connectionStatus.setText("Benutzername und API-Key fehlen.");
            connectionStatus.setTextColor(Color.rgb(185, 28, 28));
            if (showToast) {
                Toast.makeText(this, "Bitte Benutzername und API-Key eintragen.",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        saveSettings();
        refreshing = true;
        refreshButton.setEnabled(false);
        connectionStatus.setText("Prüfe eBesucher API …");
        connectionStatus.setTextColor(Color.rgb(37, 99, 235));

        executor.execute(() -> {
            try {
                EbesucherApi api = new EbesucherApi(username, apiKey);
                List<SurflinkStats> links = new ArrayList<>(api.getSurflinks());
                Collections.sort(links, new Comparator<SurflinkStats>() {
                    @Override
                    public int compare(SurflinkStats a, SurflinkStats b) {
                        return Long.compare(b.lastActivityMillis, a.lastActivityMillis);
                    }
                });

                int count = Math.min(2, links.size());
                String today = berlinDate();
                long nowUnix = System.currentTimeMillis() / 1000L;
                long tenMinutesAgoUnix = nowUnix - 600L;
                double total = 0.0;

                for (int i = 0; i < count; i++) {
                    SurflinkStats item = links.get(i);
                    item.todayBtp = api.getHourlyEarnings(item.fullName, today);
                    item.last10MinutesBtp = api.getEarnings(
                            item.fullName, tenMinutesAgoUnix, nowUnix);
                    total += item.todayBtp;
                }

                final int displayed = count;
                final List<SurflinkStats> result = links;
                final double totalResult = total;
                final int remaining = api.getRateLimitRemaining();
                final String limit = api.getRateLimit();

                runOnUiThread(() -> {
                    renderSuccess(result, displayed, totalResult, remaining, limit);
                    if (showToast) {
                        Toast.makeText(this, "Aktualisiert", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) {
                    message = e.getClass().getSimpleName();
                }
                final String finalMessage = message;
                runOnUiThread(() -> renderError(finalMessage));
            } finally {
                refreshing = false;
                runOnUiThread(this::updateRefreshAvailability);
            }
        });
    }

    private void renderSuccess(List<SurflinkStats> links, int count, double total,
                               int remaining, String limit) {
        connectionStatus.setText(count >= 2
                ? "✓ API verbunden · 2 Surflinks gefunden"
                : "✓ API verbunden · nur " + count + " Surflink(s) gefunden");
        connectionStatus.setTextColor(Color.rgb(21, 128, 61));
        totalToday.setText("Heute gesamt: " + btp(total));

        long now = System.currentTimeMillis();
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putLong(PREF_LAST_SUCCESS, now)
                .apply();
        updatedAt.setText("Letzte erfolgreiche Aktualisierung: " + formatTime(now));

        lastServerRemaining = remaining;
        lastServerLimit = limit == null ? "" : limit;

        for (int i = 0; i < 2; i++) {
            if (i < count) {
                renderSurfbar(i, links.get(i));
            } else {
                clearSurfbar(i);
            }
        }
        updateRefreshAvailability();
    }

    private void renderSurfbar(int index, SurflinkStats item) {
        surfName[index].setText(item.fullName.isEmpty() ? "Unbenannter Surflink" : item.fullName);
        surfToday[index].setText("Heute: " + btp(item.todayBtp));
        surfTenMinutes[index].setText("Letzte 10 Minuten: " + btp(item.last10MinutesBtp));

        if (item.last10MinutesBtp > BTP_EPSILON) {
            surfStatus[index].setText("● BTP-GUTSCHRIFT AKTIV");
            surfStatus[index].setTextColor(Color.rgb(21, 128, 61));
            surfTenMinutes[index].setTextColor(Color.rgb(21, 128, 61));
        } else {
            surfStatus[index].setText("● DERZEIT KEINE BTP-GUTSCHRIFT");
            surfStatus[index].setTextColor(Color.rgb(180, 83, 9));
            surfTenMinutes[index].setText(
                    "Letzte 10 Minuten: 0 BTP · Browser kann trotzdem Seiten laden");
            surfTenMinutes[index].setTextColor(Color.rgb(180, 83, 9));
        }

        surfLast[index].setText("API lastActivity: " + emptyDash(item.lastActivity)
                + " · nur Zusatzinfo");
    }

    private void clearSurfbar(int index) {
        surfName[index].setText("Kein weiterer Surflink");
        surfStatus[index].setText("● Keine Daten");
        surfStatus[index].setTextColor(Color.rgb(107, 114, 128));
        surfToday[index].setText("Heute: –");
        surfTenMinutes[index].setText("Letzte 10 Minuten: –");
        surfTenMinutes[index].setTextColor(Color.rgb(75, 85, 99));
        surfLast[index].setText("API lastActivity: –");
    }

    private void renderError(String message) {
        connectionStatus.setText("✕ " + message);
        connectionStatus.setTextColor(Color.rgb(185, 28, 28));
        updateRefreshAvailability();
    }

    private void updateRefreshAvailability() {
        if (refreshButton == null || rateLimit == null) {
            return;
        }

        long wait = EbesucherApi.secondsUntilFullRefreshAvailable();
        refreshButton.setEnabled(!refreshing && wait <= 0L);

        String serverText;
        if (lastServerRemaining >= 0 && lastServerLimit != null && !lastServerLimit.isEmpty()) {
            serverText = "API-Limit: " + lastServerRemaining + " von " + lastServerLimit
                    + " Anfragen verfügbar";
        } else if (lastServerRemaining >= 0) {
            serverText = "API-Limit: " + lastServerRemaining + " Anfragen verfügbar";
        } else {
            serverText = "API-Limit: –";
        }

        if (wait > 0L) {
            rateLimit.setText(serverText + " · nächste vollständige Prüfung in " + wait + " Sek.");
        } else {
            rateLimit.setText(serverText + " · bereit");
        }
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        usernameInput.setText(prefs.getString(PREF_USER, ""));
        apiKeyInput.setText(prefs.getString(PREF_KEY, ""));
        autoRefresh.setChecked(prefs.getBoolean(PREF_AUTO, true));

        long lastSuccess = prefs.getLong(PREF_LAST_SUCCESS, 0L);
        if (lastSuccess > 0L) {
            updatedAt.setText("Letzte erfolgreiche Aktualisierung: " + formatTime(lastSuccess));
        }
    }

    private void saveSettings() {
        if (usernameInput == null || apiKeyInput == null || autoRefresh == null) {
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(PREF_USER, usernameInput.getText().toString().trim())
                .putString(PREF_KEY, apiKeyInput.getText().toString().trim())
                .putBoolean(PREF_AUTO, autoRefresh.isChecked())
                .apply();
    }

    private String berlinDate() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
        format.setTimeZone(TimeZone.getTimeZone("Europe/Berlin"));
        return format.format(new Date());
    }

    private String btp(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.GERMANY);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(2);
        return format.format(value) + " BTP";
    }

    private String formatTime(long millis) {
        return DateFormat.getTimeInstance(DateFormat.MEDIUM, Locale.GERMANY)
                .format(new Date(millis));
    }

    private static String emptyDash(String value) {
        return value == null || value.trim().isEmpty() ? "–" : value;
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(229, 231, 235));
        layout.setBackground(background);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            layout.setElevation(dp(2));
        }
        return layout;
    }

    private TextView text(String value, int sizeSp, boolean bold, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private LinearLayout.LayoutParams spacedParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(topDp), 0, 0);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
