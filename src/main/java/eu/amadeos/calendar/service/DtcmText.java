package eu.amadeos.calendar.service;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.DateOrder;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.Repeat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The appointment text of dtcm, as mailed between CDE users and dropped from Mailer or File Manager
 * onto the Calendar:
 * <pre>
 *     ** Calendar Appointment **
 *
 *     Date:   11/22/94
 *     Start:  10:00 am
 *     End:    11:00 am
 *     What:   Staff meeting
 *             Monet conference room
 * </pre>
 * Written with the user's date and time format; read with it, with the CDE order month/day/year and
 * with ISO dates, so that text from other people is understood as well.
 */
public final class DtcmText {
    public static final String MARKER = "** Calendar Appointment **";

    private DtcmText() {
    }

    /** True if the text holds at least one appointment block. */
    public static boolean contains(String text) {
        return text != null && text.contains(MARKER);
    }

    public static String format(Appointment a, DateFormats formats) {
        StringBuilder b = new StringBuilder();
        b.append('\t').append(MARKER).append('\n').append('\n');
        b.append("\tDate:\t").append(formats.date(a.date())).append('\n');
        b.append("\tStart:\t").append(a.hasTime() ? formats.time(a.start()) : "").append('\n');
        b.append("\tEnd:\t").append(a.hasEnd() ? formats.time(a.end()) : "").append('\n');
        String[] lines = a.what().split("\n");
        b.append("\tWhat:\t").append(lines[0]).append('\n');
        for (int i = 1; i < lines.length; i++) b.append("\t\t").append(lines[i]).append('\n');
        return b.toString();
    }

    /** Every appointment block in the text; blocks without a readable date are skipped. */
    public static List<Appointment> parse(String text, DateFormats formats) {
        List<Appointment> result = new ArrayList<>();
        if (text == null) return result;
        String[] lines = text.split("\r\n|\n|\r");
        int i = 0;
        while (i < lines.length) {
            if (!lines[i].strip().equals(MARKER)) {
                i++;
                continue;
            }
            i++;
            String date = null;
            String start = null;
            String end = null;
            StringBuilder what = null;
            while (i < lines.length && !lines[i].strip().equals(MARKER)) {
                String line = lines[i++];
                String stripped = line.strip();
                if (stripped.isEmpty()) {
                    if (what != null) break;
                    continue;
                }
                String key = key(stripped);
                switch (key) {
                    case "date" -> date = value(stripped);
                    case "start" -> start = value(stripped);
                    case "end" -> end = value(stripped);
                    case "what" -> what = new StringBuilder(value(stripped));
                    default -> {
                        if (what != null) what.append('\n').append(stripped);
                    }
                }
            }
            Optional<LocalDate> day = date == null ? Optional.empty() : parseDate(date, formats);
            if (day.isEmpty()) continue;
            LocalTime startTime = start == null ? null : formats.parseTime(start).orElse(null);
            LocalTime endTime = end == null || startTime == null ? null : formats.parseTime(end).orElse(null);
            result.add(new Appointment(Appointment.newUid(), day.get(), startTime, endTime,
                    what == null ? "" : what.toString(), List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT));
        }
        return result;
    }

    private static String key(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0 || colon > 8) return "";
        return line.substring(0, colon).strip().toLowerCase(Locale.ROOT);
    }

    private static String value(String line) {
        return line.substring(line.indexOf(':') + 1).strip();
    }

    /** The user's format first, then ISO, then CDE's month/day/year, then day/month/year. */
    static Optional<LocalDate> parseDate(String text, DateFormats formats) {
        Optional<LocalDate> own = formats.parseDateLenient(text);
        if (own.isPresent()) return own;
        for (DateOrder order : new DateOrder[]{DateOrder.MONTH_DAY_YEAR, DateOrder.DAY_MONTH_YEAR, DateOrder.YEAR_MONTH_DAY}) {
            Optional<LocalDate> d = new DateFormats(order, '/', true, formats.locale()).parseDate(text);
            if (d.isPresent()) return d;
        }
        return Optional.empty();
    }

    /** A {@code mailto:} link that opens the mailer with the appointment as the message text. */
    public static String mailto(String to, String subject, Appointment a, DateFormats formats) {
        return "mailto:" + encode(to == null ? "" : to) + "?subject=" + encode(subject) + "&body=" + encode(format(a, formats));
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
