package com.notispoti.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class SpotifyTrackResolver {

    public static class TrackInfo {
        public final String name;
        public final String artist;
        public final String uri;
        public final String id;

        public TrackInfo(String name, String artist, String uri, String id) {
            this.name = name;
            this.artist = artist;
            this.uri = uri;
            this.id = id;
        }
    }

    public interface Callback {
        void onSuccess(TrackInfo track);
        void onError(String message);
    }

    private static final String TOTP_SECRET_RAW = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N";
    private static final int TOTP_VERSION = 61;
    private static final String SEARCH_HASH = "eef7cc54888d91bdd6802623477873caa3948ae173a0c34fd86827b267e94c03";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, TrackInfo> cache = new HashMap<>();
    private String cachedToken = null;
    private long tokenExpiresAt = 0;

    public void resolve(final String query, final Callback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onError("Empty query");
            return;
        }

        final String cleanQuery = query.trim();

        synchronized (cache) {
            if (cache.containsKey(cleanQuery)) {
                callback.onSuccess(cache.get(cleanQuery));
                return;
            }
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String token = getOrRefreshToken();
                    TrackInfo track = searchTrack(token, cleanQuery);
                    if (track != null) {
                        synchronized (cache) {
                            cache.put(cleanQuery, track);
                        }
                        callback.onSuccess(track);
                    } else {
                        callback.onError("No matching Spotify track found for: " + cleanQuery);
                    }
                } catch (Exception e) {
                    callback.onError("Resolver error: " + e.getMessage());
                }
            }
        });
    }

    private synchronized String getOrRefreshToken() throws Exception {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt - 60000) {
            return cachedToken;
        }

        // 1. Fetch server time
        long serverTimeSec = System.currentTimeMillis() / 1000;
        try {
            HttpURLConnection timeConn = (HttpURLConnection) new URL("https://open.spotify.com/api/server-time").openConnection();
            timeConn.setConnectTimeout(5000);
            timeConn.setReadTimeout(5000);
            if (timeConn.getResponseCode() == 200) {
                String resp = readStream(timeConn.getInputStream());
                JSONObject json = new JSONObject(resp);
                serverTimeSec = json.optLong("serverTime", serverTimeSec);
            }
        } catch (Exception ignored) {}

        // 2. Derive Secret & Compute TOTP
        byte[] secretBytes = deriveTotpSecret(TOTP_SECRET_RAW);
        String totp = generateTotp(secretBytes, System.currentTimeMillis());
        String totpServer = generateTotp(secretBytes, serverTimeSec * 1000);

        String tokenUrl = "https://open.spotify.com/api/token?reason=transport&productType=web-player" +
                "&totp=" + totp + "&totpServer=" + totpServer + "&totpVer=" + TOTP_VERSION;

        HttpURLConnection tokenConn = (HttpURLConnection) new URL(tokenUrl).openConnection();
        tokenConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/124.0.0.0 Safari/537.36");
        tokenConn.setRequestProperty("Accept", "application/json");
        tokenConn.setRequestProperty("Referer", "https://open.spotify.com/");
        tokenConn.setConnectTimeout(6000);
        tokenConn.setReadTimeout(6000);

        if (tokenConn.getResponseCode() != 200) {
            throw new Exception("HTTP " + tokenConn.getResponseCode() + " fetching Spotify token");
        }

        String tokenBody = readStream(tokenConn.getInputStream());
        JSONObject tokenJson = new JSONObject(tokenBody);
        cachedToken = tokenJson.getString("accessToken");
        tokenExpiresAt = tokenJson.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 1800000);

        return cachedToken;
    }

    private TrackInfo searchTrack(String token, String searchTerm) throws Exception {
        URL url = new URL("https://api-partner.spotify.com/pathfinder/v1/query");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
        conn.setRequestProperty("Referer", "https://open.spotify.com/");
        conn.setRequestProperty("Origin", "https://open.spotify.com");
        conn.setRequestProperty("app-platform", "WebPlayer");
        conn.setDoOutput(true);
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);

        JSONObject variables = new JSONObject();
        variables.put("searchTerm", searchTerm);
        variables.put("offset", 0);
        variables.put("limit", 5);
        variables.put("numberOfTopResults", 3);
        variables.put("includeAudiobooks", true);

        JSONObject persistedQuery = new JSONObject();
        persistedQuery.put("version", 1);
        persistedQuery.put("sha256Hash", SEARCH_HASH);

        JSONObject extensions = new JSONObject();
        extensions.put("persistedQuery", persistedQuery);

        JSONObject payload = new JSONObject();
        payload.put("operationName", "searchDesktop");
        payload.put("variables", variables);
        payload.put("extensions", extensions);

        byte[] payloadBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        OutputStream os = conn.getOutputStream();
        os.write(payloadBytes);
        os.flush();
        os.close();

        if (conn.getResponseCode() != 200) {
            throw new Exception("Pathfinder search returned HTTP " + conn.getResponseCode());
        }

        String body = readStream(conn.getInputStream());
        JSONObject resJson = new JSONObject(body);

        JSONObject data = resJson.optJSONObject("data");
        if (data == null) return null;
        JSONObject searchV2 = data.optJSONObject("searchV2");
        if (searchV2 == null) return null;
        JSONObject tracksV2 = searchV2.optJSONObject("tracksV2");
        if (tracksV2 == null) return null;
        JSONArray items = tracksV2.optJSONArray("items");
        if (items == null || items.length() == 0) return null;

        JSONObject firstItem = items.getJSONObject(0).optJSONObject("item");
        if (firstItem == null) return null;
        JSONObject trackData = firstItem.optJSONObject("data");
        if (trackData == null) return null;

        String name = trackData.optString("name", "Unknown");
        String uri = trackData.optString("uri", "");
        String id = trackData.optString("id", "");

        String artistName = "Unknown";
        JSONObject artists = trackData.optJSONObject("artists");
        if (artists != null) {
            JSONArray artistItems = artists.optJSONArray("items");
            if (artistItems != null && artistItems.length() > 0) {
                JSONObject profile = artistItems.getJSONObject(0).optJSONObject("profile");
                if (profile != null) {
                    artistName = profile.optString("name", "Unknown");
                }
            }
        }

        return new TrackInfo(name, artistName, uri, id);
    }

    private byte[] deriveTotpSecret(String raw) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            int xor = ch ^ ((i % 33) + 9);
            sb.append(xor);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String generateTotp(byte[] secret, long timestampMs) {
        try {
            long counter = timestampMs / 1000 / 30;
            byte[] counterBytes = ByteBuffer.allocate(8).putLong(counter).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hmac = mac.doFinal(counterBytes);

            int offset = hmac[hmac.length - 1] & 0x0F;
            int code = ((hmac[offset] & 0x7F) << 24) |
                       ((hmac[offset + 1] & 0xFF) << 16) |
                       ((hmac[offset + 2] & 0xFF) << 8) |
                       (hmac[offset + 3] & 0xFF);

            int otp = code % 1000000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            return "000000";
        }
    }

    private String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        return sb.toString();
    }
}
