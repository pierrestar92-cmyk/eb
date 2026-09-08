package de.pierre.ebesuchermonitor;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class EbesucherApi {
    private static final String BASE_URL = "https://www.ebesucher.de/api/";
    private static final TimeZone BERLIN = TimeZone.getTimeZone("Europe/Berlin");

    // eBesucher meldet derzeit 7 Requests/Minute. Ein kompletter Monitor-Durchlauf
    // benötigt 5 Requests. Deshalb lassen wir zwei Requests Reserve.
    private static final Object LOCAL_RATE_LOCK = new Object();
    private static final ArrayDeque<Long> LOCAL_REQUESTS = new ArrayDeque<>();
    private static final int LOCAL_MAX_REQUESTS_PER_MINUTE = 5;
    private static final long LOCAL_RATE_WINDOW_MS = 60_000L;

    private final String authorization;
    private int rateLimitRemaining = -1;
    private String rateLimit = "";

    public EbesucherApi(String username, String apiKey) {
        String credentials = username + ":" + apiKey;
        authorization = "Basic " + Base64.encodeToString(
                credentials.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    public List<SurflinkStats> getSurflinks() throws IOException, JSONException {
        Object json = new JSONTokener(get("visitor_exchange.json/surflinks")).nextValue();
        if (!(json instanceof JSONArray)) {
            throw new JSONException("Unerwartete Surflink-Antwort");
        }

        JSONArray array = (JSONArray) json;
        List<SurflinkStats> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            String lastActivity = item.optString("lastActivity", "");
            result.add(new SurflinkStats(
                    item.optLong("id", 0L),
                    item.optString("fullName", ""),
                    item.optString("url", ""),
                    lastActivity,
                    parseLastActivity(lastActivity)
            ));
        }
        return result;
    }

    public double getHourlyEarnings(String fullName, String yyyyMmDd)
            throws IOException, JSONException {
        String path = "visitor_exchange.json/surflink/"
                + Uri.encode(fullName)
                + "/earnings_hourly/"
                + Uri.encode(yyyyMmDd)
                + "?timezone=Europe%2FBerlin";
        Object json = new JSONTokener(get(path)).nextValue();
        return sumNumericValues(json);
    }

    public double getEarnings(String fullName, long fromUnix, long toUnix)
            throws IOException, JSONException {
        String path = "visitor_exchange.json/surflink/"
                + Uri.encode(fullName)
                + "/earnings/"
                + fromUnix + "-" + toUnix;
        Object json = new JSONTokener(get(path)).nextValue();
        return sumEarningsRows(json);
    }

    public int getRateLimitRemaining() {
        return rateLimitRemaining;
    }

    public String getRateLimit() {
        return rateLimit;
    }

    /**
     * Ein kompletter Refresh braucht alle 5 lokalen Request-Slots. Solange noch
     * ein Request im rollenden Minutenfenster liegt, warten wir bis auch der
     * neueste alte Request abgelaufen ist. So bricht ein Refresh nicht mitten drin ab.
     */
    public static long secondsUntilFullRefreshAvailable() {
        synchronized (LOCAL_RATE_LOCK) {
            long now = System.currentTimeMillis();
            purgeOldRequests(now);
            if (LOCAL_REQUESTS.isEmpty()) {
                return 0L;
            }
            long newest = LOCAL_REQUESTS.peekLast();
            long waitMs = Math.max(0L, LOCAL_RATE_WINDOW_MS - (now - newest));
            return waitMs <= 0L ? 0L : Math.max(1L, (waitMs + 999L) / 1000L);
        }
    }

    private String get(String relativePath) throws IOException {
        guardLocalRateLimit();

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + relativePath);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", authorization);
            connection.setRequestProperty("User-Agent", "eBesucher-Monitor-Android/0.1.2");

            int status = connection.getResponseCode();
            updateRateLimit(connection);

            String authStatus = connection.getHeaderField("X-Auth-Status");
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = readAll(stream);

            if ("false".equalsIgnoreCase(authStatus)) {
                throw new IOException("API-Anmeldung abgelehnt. Benutzername/API-Key prüfen.");
            }
            if (status == 429) {
                String retryAfter = connection.getHeaderField("Retry-After");
                if (retryAfter != null && !retryAfter.trim().isEmpty()) {
                    throw new IOException("API-Limit erreicht. Bitte etwa "
                            + retryAfter.trim() + " Sekunden warten. Die letzten Daten bleiben sichtbar.");
                }
                throw new IOException("API-Limit erreicht. Bitte etwa 60 Sekunden warten. "
                        + "Die letzten Daten bleiben sichtbar.");
            }
            if (status < 200 || status >= 300) {
                String detail = body == null ? "" : body.trim();
                if (detail.length() > 180) {
                    detail = detail.substring(0, 180) + "…";
                }
                throw new IOException("eBesucher API HTTP " + status
                        + (detail.isEmpty() ? "" : ": " + detail));
            }
            if (body == null || body.trim().isEmpty()) {
                throw new IOException("Leere Antwort von der eBesucher API.");
            }
            return body;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void guardLocalRateLimit() throws IOException {
        synchronized (LOCAL_RATE_LOCK) {
            long now = System.currentTimeMillis();
            purgeOldRequests(now);

            if (LOCAL_REQUESTS.size() >= LOCAL_MAX_REQUESTS_PER_MINUTE) {
                long newest = LOCAL_REQUESTS.peekLast();
                long waitMs = Math.max(1L, LOCAL_RATE_WINDOW_MS - (now - newest));
                long waitSeconds = Math.max(1L, (waitMs + 999L) / 1000L);
                throw new IOException("Rate-Limit-Schutz aktiv: Bitte noch " + waitSeconds
                        + " Sekunden bis zur nächsten vollständigen Prüfung warten.");
            }

            LOCAL_REQUESTS.addLast(now);
        }
    }

    private static void purgeOldRequests(long now) {
        while (!LOCAL_REQUESTS.isEmpty()
                && now - LOCAL_REQUESTS.peekFirst() >= LOCAL_RATE_WINDOW_MS) {
            LOCAL_REQUESTS.removeFirst();
        }
    }

    private void updateRateLimit(HttpURLConnection connection) {
        String remaining = connection.getHeaderField("X-RateLimit-Remaining");
        String limit = connection.getHeaderField("X-RateLimit-Limit");
        rateLimit = limit == null ? "" : limit;
        if (remaining != null) {
            try {
                rateLimitRemaining = Integer.parseInt(remaining.trim());
            } catch (NumberFormatException ignored) {
                rateLimitRemaining = -1;
            }
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }

    private static long parseLastActivity(String value) {
        if (value == null || value.trim().isEmpty()) {
            return 0L;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY);
        format.setLenient(false);
        format.setTimeZone(BERLIN);
        try {
            Date parsed = format.parse(value);
            return parsed == null ? 0L : parsed.getTime();
        } catch (ParseException ignored) {
            return 0L;
        }
    }

    private static double sumNumericValues(Object json) throws JSONException {
        double total = 0.0;
        if (json instanceof JSONObject) {
            JSONObject object = (JSONObject) json;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                total += asDouble(object.opt(keys.next()));
            }
            return total;
        }
        if (json instanceof JSONArray) {
            JSONArray array = (JSONArray) json;
            for (int i = 0; i < array.length(); i++) {
                total += asDouble(array.opt(i));
            }
            return total;
        }
        return asDouble(json);
    }

    private static double sumEarningsRows(Object json) throws JSONException {
        if (!(json instanceof JSONArray)) {
            return sumNumericValues(json);
        }
        JSONArray array = (JSONArray) json;
        double total = 0.0;
        for (int i = 0; i < array.length(); i++) {
            Object value = array.opt(i);
            if (value instanceof JSONObject) {
                total += asDouble(((JSONObject) value).opt("value"));
            } else {
                total += asDouble(value);
            }
        }
        return total;
    }

    private static double asDouble(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return 0.0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }
}
