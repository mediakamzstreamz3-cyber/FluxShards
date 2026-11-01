package net.fluxmc.fluxshards;

import java.util.Locale;

public final class Fmt {

    private Fmt() {}

    /** 1234567 -> 1,234,567 */
    public static String commas(long v) {
        return String.format(Locale.US, "%,d", v);
    }

    /** 1500 -> 1.5K, 2500000 -> 2.5M */
    public static String shorten(long v) {
        if (v >= 1_000_000_000L) return trim(v / 1_000_000_000.0) + "B";
        if (v >= 1_000_000L) return trim(v / 1_000_000.0) + "M";
        if (v >= 1_000L) return trim(v / 1_000.0) + "K";
        return String.valueOf(v);
    }

    private static String trim(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Reads 500, 1k, 2.5m, 1b. Returns -1 if it isn't a positive amount. */
    public static long parse(String input) {
        if (input == null || input.isEmpty()) return -1;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            double v = Double.parseDouble(s) * mult;
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0 || v > 9e18) return -1;
            return (long) v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
