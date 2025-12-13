package com.example.projets5.util;

import com.example.projets5.model.GeoPoint;
import com.example.projets5.model.YearRange;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSVUtils {

    public static String cleanDescription(String s) {
        return s == null ? null : s.replaceAll("\\s+", " ").trim();
    }

    // "['4','5']" -> [4,5]
    public static List<Integer> parseIntListFromStringList(String s) {
        if (s == null) return List.of();
        String c = s.replaceAll("[\\[\\]'\\s]", "");
        if (c.isEmpty()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (String t : c.split(",")) {
            try { out.add(Integer.valueOf(t)); } catch (Exception ignore) {}
        }
        return out;
    }

    // "a, b, c" -> ["a","b","c"]
    public static List<String> parseCsvList(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.asList(s.split("\\s*,\\s*"));
    }

    // "530,3199" -> YearRange
    public static YearRange parseYearRange(String s) {
        if (s == null) return null;
        String[] p = s.split("\\s*,\\s*");
        try {
            Integer a = p.length > 0 ? Integer.valueOf(p[0]) : null;
            Integer b = p.length > 1 ? Integer.valueOf(p[1]) : null;
            return YearRange.builder().start(a).end(b).build();
        } catch (Exception e) {
            return null;
        }
    }

    // "30°1'55.75\"N, 31°4'31.13\"E" -> GeoPoint
    public static GeoPoint parseDmsLatLon(String s) {
        if (s == null) return null;
        String[] parts = s.split("\\s*,\\s*");
        if (parts.length != 2) return null;
        Double lat = dmsToDecimal(parts[0]);
        Double lon = dmsToDecimal(parts[1]);
        if (lat == null || lon == null) return null;
        return GeoPoint.builder().lat(lat).lon(lon).build();
    }

    private static Double dmsToDecimal(String dms) {
        // Exemples acceptés: 30°1'55.75"N  |  30\u00B01'55.75"N  |  30°1'55"N
        // 1-3 digits deg, 1-2 digits min, seconds en décimal optionnel, guillemet optionnel, espace optionnel, N/S/E/W
        final String REGEX =
                "(\\d{1,3})[°\\u00B0](\\d{1,2})'([\\d.]+)\"?\\s*([NSEWnsew])";
        Pattern p = Pattern.compile(REGEX, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(dms.trim());
        if (!m.find()) return null;

        double deg = Double.parseDouble(m.group(1));
        double min = Double.parseDouble(m.group(2));
        double sec = Double.parseDouble(m.group(3));
        char hemi = Character.toUpperCase(m.group(4).charAt(0));

        double value = deg + (min / 60.0) + (sec / 3600.0);
        if (hemi == 'S' || hemi == 'W') value = -value;
        return value;
    }

    // 21926 => null (unranked)
    public static Integer normalizeRanking(Integer raw) {
        return (raw != null && raw == 21926) ? null : raw;
    }

    public static Integer toInt(String s) {
        try { return (s == null || s.isBlank()) ? null : Integer.valueOf(s); }
        catch (Exception e) { return null; }
    }

    public static Double toDouble(String s) {
        try { return (s == null || s.isBlank()) ? null : Double.valueOf(s); }
        catch (Exception e) { return null; }
    }

    public static Boolean toBool(String s) {
        if (s == null) return null;
        return "1".equals(s) || "true".equalsIgnoreCase(s) || "yes".equalsIgnoreCase(s);
    }
}
