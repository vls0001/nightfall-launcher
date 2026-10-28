package dev.nightfall.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Current weather for the TV's rough location (from its internet address).
 * Uses ipapi.co / ipwho.is for location and open-meteo.com for weather; no accounts or keys.
 */
final class Weather {
    static final class Now {
        String city;
        int temp;
        String unit;
        String icon;
        String summary;
    }

    private static final long LOCATION_TTL = 12 * 3600 * 1000L;
    private static final long WEATHER_TTL = 30 * 60 * 1000L;

    private Weather() {
    }

    /** Cached reading if fresh, else null. */
    static Now cached(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - p.getLong("at", 0) > WEATHER_TTL) return null;
        return fromPrefs(p);
    }

    /** Last reading even if stale, else null. */
    static Now last(Context ctx) {
        return fromPrefs(ctx.getSharedPreferences("weather", Context.MODE_PRIVATE));
    }

    private static Now fromPrefs(SharedPreferences p) {
        if (!p.contains("temp")) return null;
        Now n = new Now();
        n.city = p.getString("city", "");
        n.temp = p.getInt("temp", 0);
        n.unit = p.getString("unit", "C");
        n.icon = p.getString("icon", "");
        n.summary = p.getString("summary", "");
        return n;
    }

    /** Network fetch. Call off the main thread. */
    static Now fetch(Context ctx) throws Exception {
        SharedPreferences p = ctx.getSharedPreferences("weather", Context.MODE_PRIVATE);
        double lat, lon;
        String city, country;
        if (System.currentTimeMillis() - p.getLong("loc_at", 0) < LOCATION_TTL && p.contains("lat")) {
            lat = Double.longBitsToDouble(p.getLong("lat", 0));
            lon = Double.longBitsToDouble(p.getLong("lon", 0));
            city = p.getString("city", "");
            country = p.getString("country", "");
        } else {
            JSONObject loc;
            try {
                loc = new JSONObject(Net.get("https://ipapi.co/json/"));
                if (!loc.has("latitude")) throw new Exception("no location");
            } catch (Exception e) {
                loc = new JSONObject(Net.get("https://ipwho.is/"));
            }
            lat = loc.getDouble("latitude");
            lon = loc.getDouble("longitude");
            city = loc.optString("city", "");
            country = loc.optString("country_code", "");
            p.edit().putLong("lat", Double.doubleToLongBits(lat)).putLong("lon", Double.doubleToLongBits(lon))
                    .putString("city", city).putString("country", country)
                    .putLong("loc_at", System.currentTimeMillis()).apply();
        }
        boolean fahrenheit = country.equals("US") || country.equals("LR") || country.equals("MM");
        String url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                        + "&current=temperature_2m,weather_code,is_day&temperature_unit=%s",
                lat, lon, fahrenheit ? "fahrenheit" : "celsius");
        JSONObject cur = new JSONObject(Net.get(url)).getJSONObject("current");
        Now n = new Now();
        n.city = city;
        n.temp = (int) Math.round(cur.getDouble("temperature_2m"));
        n.unit = fahrenheit ? "F" : "C";
        int code = cur.getInt("weather_code");
        boolean day = cur.optInt("is_day", 1) == 1;
        describe(n, code, day);
        p.edit().putInt("temp", n.temp).putString("unit", n.unit).putString("icon", n.icon)
                .putString("summary", n.summary).putLong("at", System.currentTimeMillis()).apply();
        return n;
    }

    private static void describe(Now n, int code, boolean day) {
        if (code == 0) { n.icon = day ? "☀️" : "🌙"; n.summary = "Clear"; }
        else if (code <= 2) { n.icon = day ? "⛅" : "☁️"; n.summary = "Partly cloudy"; }
        else if (code == 3) { n.icon = "☁️"; n.summary = "Cloudy"; }
        else if (code == 45 || code == 48) { n.icon = "🌫️"; n.summary = "Foggy"; }
        else if (code >= 51 && code <= 57) { n.icon = "🌦️"; n.summary = "Drizzle"; }
        else if (code >= 61 && code <= 67) { n.icon = "🌧️"; n.summary = "Rain"; }
        else if (code >= 71 && code <= 77) { n.icon = "🌨️"; n.summary = "Snow"; }
        else if (code >= 80 && code <= 82) { n.icon = "🌦️"; n.summary = "Showers"; }
        else if (code == 85 || code == 86) { n.icon = "🌨️"; n.summary = "Snow showers"; }
        else if (code >= 95) { n.icon = "⛈️"; n.summary = "Thunderstorms"; }
        else { n.icon = "☁️"; n.summary = "Cloudy"; }
    }
}
