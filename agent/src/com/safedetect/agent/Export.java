package com.safedetect.agent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** CSV dumps of saved flags and encounters for spreadsheets. */
final class Export {
    private Export() {
    }

    /**
     * One CSV field. Quotes anything with a comma, quote or line break, and defuses a leading = + - @ so a
     * spreadsheet does not run it as a formula.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String text = value;
        if (!text.isEmpty() && "=+-@".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        boolean quote = text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0;
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    static String line(Object... fields) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(escape(fields[i] == null ? "" : String.valueOf(fields[i])));
        }
        return out.append("\r\n").toString();
    }

    static String flagsCsv(List<FlagStore.Record> records) {
        StringBuilder out = new StringBuilder(line("name", "uuid", "flags", "counts", "evidence", "times", "first",
                "last"));
        for (FlagStore.Record record : records) {
            StringBuilder counts = new StringBuilder();
            for (Map.Entry<String, Integer> count : record.counts.entrySet()) {
                counts.append(counts.length() == 0 ? "" : " ").append(count.getKey()).append('=').append(count.getValue());
            }
            StringBuilder evidence = new StringBuilder();
            for (Map.Entry<String, String> detail : record.evidence.entrySet()) {
                evidence.append(evidence.length() == 0 ? "" : "; ").append(detail.getKey()).append(": ")
                        .append(detail.getValue());
            }
            out.append(line(record.name, record.uuid, join(record.flags), counts, evidence, record.times,
                    date(record.first), date(record.last)));
        }
        return out.toString();
    }

    static String encountersCsv(List<Encounters.Entry> entries) {
        StringBuilder out = new StringBuilder(line("name", "key", "seen", "first", "last"));
        for (Encounters.Entry entry : entries) {
            out.append(line(entry.name, entry.key, entry.count, date(entry.first), date(entry.last)));
        }
        return out.toString();
    }

    /** Queues both files on the background writer and returns the flags file. */
    static File write(File gameDir, FlagStore store, Encounters encounters, Date now) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(now);
        File dir = new File(gameDir, "config");
        File flags = new File(dir, "safedetect-export-" + stamp + ".csv");
        Files2.writeLater(flags, flagsCsv(store.records()));
        if (encounters != null) {
            Files2.writeLater(new File(dir, "safedetect-encounters-" + stamp + ".csv"),
                    encountersCsv(encounters.entries()));
        }
        return flags;
    }

    private static String join(Iterable<String> items) {
        StringBuilder out = new StringBuilder();
        for (String item : items) {
            out.append(out.length() == 0 ? "" : " ").append(item);
        }
        return out.toString();
    }

    private static String date(long millis) {
        return millis <= 0 ? "" : new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date(millis));
    }
}
