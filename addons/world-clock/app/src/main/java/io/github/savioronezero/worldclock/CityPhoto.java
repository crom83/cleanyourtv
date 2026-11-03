package io.github.savioronezero.worldclock;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Locale;

/**
 * A photo of a clock's city: the lead image of its English Wikipedia article, taken from Wikimedia Commons at
 * 1920 px with its author and licence (only Commons files, so only freely licensed ones). Kept in the app's cache
 * for {@link #KEEP_MS}; blocking, call it off the main thread. Null when there is none or the network is down.
 */
final class CityPhoto {
    static final long KEEP_MS = 30L * 24 * 60 * 60 * 1000;
    private static final String AGENT = "CleanYourTV-WorldClock/1.0 (Android TV add-on; github.com/Gathin23/cleanyourtv)";

    final File image;
    /** "Author · licence", for the credit line. */
    final String credit;

    private CityPhoto(File image, String credit) {
        this.image = image;
        this.credit = credit;
    }

    static CityPhoto load(Context c, String id) {
        String title = title(id);
        File dir = new File(c.getCacheDir(), "city");
        String key = title.replaceAll("[^A-Za-z0-9]+", "_");
        File image = new File(dir, key + ".jpg"), credit = new File(dir, key + ".txt");
        try {
            if (image.isFile() && credit.isFile() && System.currentTimeMillis() - image.lastModified() < KEEP_MS) {
                return new CityPhoto(image, new String(Files.readAllBytes(credit.toPath()), StandardCharsets.UTF_8));
            }
            JSONObject summary = null;
            // "New York" is a disambiguation page: then "New York City", then "New York (city)"
            for (String candidate : new String[] {title, title + " City", title + " (city)"}) {
                try {
                    JSONObject s = new JSONObject(get("https://en.wikipedia.org/api/rest_v1/page/summary/"
                            + URLEncoder.encode(candidate.replace(' ', '_'), "UTF-8").replace("+", "%20") + "?redirect=true"));
                    if (!"disambiguation".equals(s.optString("type"))) {
                        summary = s;
                        break;
                    }
                } catch (IOException notThere) {
                    // no such article: the next candidate
                }
            }
            if (summary == null) return null;
            JSONObject original = summary.optJSONObject("originalimage");
            String file = original == null ? null : fileName(original.optString("source"));
            if (file == null || file.toLowerCase(Locale.ROOT).endsWith(".svg")) return null;
            JSONObject pages = new JSONObject(get("https://commons.wikimedia.org/w/api.php?action=query&format=json"
                    + "&prop=imageinfo&iiprop=url%7Cextmetadata&iiurlwidth=1920&titles="
                    + URLEncoder.encode("File:" + file, "UTF-8"))).getJSONObject("query").getJSONObject("pages");
            Iterator<String> keys = pages.keys();
            if (!keys.hasNext()) return null;
            JSONObject page = pages.getJSONObject(keys.next());
            if (page.has("missing") || page.optJSONArray("imageinfo") == null) return null;   // not on Commons
            JSONObject info = page.getJSONArray("imageinfo").getJSONObject(0);
            String url = info.optString("thumburl", info.optString("url"));
            JSONObject meta = info.optJSONObject("extmetadata");
            String artist = meta == null ? "" : text(meta.optJSONObject("Artist"));
            String licence = meta == null ? "" : text(meta.optJSONObject("LicenseShortName"));
            byte[] jpeg = bytes(url);
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            try (FileOutputStream out = new FileOutputStream(image)) {
                out.write(jpeg);
            }
            String line = artist.isEmpty() ? licence : licence.isEmpty() ? artist : artist + " · " + licence;
            Files.write(credit.toPath(), line.getBytes(StandardCharsets.UTF_8));
            return new CityPhoto(image, line);
        } catch (Exception e) {
            return image.isFile() ? new CityPhoto(image, read(credit)) : null;   // an old photo beats none
        }
    }

    /** The English article for the clock's city; a few names need the article's full title. */
    static String title(String id) {
        String city = ClockModel.LOCAL.equals(id) ? ClockModel.city(ZoneId.systemDefault().getId()) : ClockModel.city(id);
        switch (city) {
            case "Washington": return "Washington, D.C.";
            case "St Johns": return "St. John's, Newfoundland and Labrador";
            case "Kiev": return "Kyiv";
            default: return city;
        }
    }

    /** "Big Ben.jpg" from an upload. or thumb.wikimedia.org URL, thumbnail or original; null when it isn't one. */
    static String fileName(String url) {
        if (url == null || !(url.contains("://upload.wikimedia.org/") || url.contains("://thumb.wikimedia.org/"))) return null;
        int query = url.indexOf('?');   // Wikipedia adds ?utm_source=... to its image links
        String[] parts = (query < 0 ? url : url.substring(0, query)).split("/");
        int thumb = -1;
        for (int i = 0; i < parts.length; i++) if (parts[i].equals("thumb")) thumb = i;
        String name = thumb >= 0 && parts.length > thumb + 3 ? parts[thumb + 3] : parts[parts.length - 1];
        try {
            return URLDecoder.decode(name, "UTF-8").replace('_', ' ');
        } catch (IOException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Plain text from an extmetadata value, which is HTML: tags gone, spaces collapsed, at most 60 characters. */
    static String text(JSONObject field) {
        if (field == null) return "";
        String s = field.optString("value").replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&nbsp;", " ")
                .replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 59) + "…" : s;
    }

    private static String get(String url) throws IOException {
        return new String(bytes(url), StandardCharsets.UTF_8);
    }

    private static byte[] bytes(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", AGENT);
        try {
            if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > 6 * 1024 * 1024) throw new IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static String read(File f) {
        try {
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
