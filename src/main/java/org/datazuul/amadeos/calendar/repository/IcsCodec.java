package org.datazuul.amadeos.calendar.repository;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.Reminder;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.Repeat;
import org.datazuul.amadeos.calendar.domain.ToDoItem;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads and writes the calendar as iCalendar (RFC 5545): {@code VEVENT} for appointments, {@code VTODO}
 * for To Do items, {@code VALARM} for reminders and {@code RRULE} for repeats. Times are floating
 * local times, as in dtcm. Properties and components the program does not understand are kept and
 * written back unchanged, so the file can be shared with other calendar programs.
 */
public final class IcsCodec {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final Set<String> EVENT_KNOWN = Set.of("UID", "DTSTAMP", "DTSTART", "DTEND", "DURATION", "SUMMARY",
            "DESCRIPTION", "CLASS", "RRULE", "EXDATE", "RECURRENCE-ID", "BEGIN", "END");
    private static final Set<String> TODO_KNOWN = Set.of("UID", "DTSTAMP", "DUE", "DTSTART", "SUMMARY", "DESCRIPTION",
            "STATUS", "COMPLETED", "PERCENT-COMPLETE", "BEGIN", "END");
    static final String PRODID = "-//datazuul//amade-calendar//EN";

    private IcsCodec() {
    }

    // ---- generic content lines ------------------------------------------------------------------

    /** One content line: {@code NAME;PARAM=value:value}. */
    record Property(String name, Map<String, String> params, String value) {
        String param(String key) {
            return params.get(key.toUpperCase(Locale.ROOT));
        }

        /** The line as it is written, folded by the writer. */
        String toLine() {
            StringBuilder b = new StringBuilder(name);
            params.forEach((k, v) -> b.append(';').append(k).append('=').append(v));
            return b.append(':').append(value).toString();
        }
    }

    /** A component with its properties and sub-components in file order. */
    static final class Component {
        final String name;
        final List<Property> properties = new ArrayList<>();
        final List<Component> children = new ArrayList<>();

        Component(String name) {
            this.name = name;
        }

        Property first(String propertyName) {
            for (Property p : properties) if (p.name.equals(propertyName)) return p;
            return null;
        }

        String value(String propertyName) {
            Property p = first(propertyName);
            return p == null ? null : p.value;
        }

        void add(String propertyName, String value) {
            properties.add(new Property(propertyName, Map.of(), value));
        }

        void add(String propertyName, Map<String, String> params, String value) {
            properties.add(new Property(propertyName, params, value));
        }

        /** The component as content lines, {@code BEGIN} to {@code END}. */
        List<String> toLines() {
            List<String> lines = new ArrayList<>();
            lines.add("BEGIN:" + name);
            for (Property p : properties) lines.add(p.toLine());
            for (Component c : children) lines.addAll(c.toLines());
            lines.add("END:" + name);
            return lines;
        }
    }

    static List<String> unfold(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\r\n|\n|\r")) {
            if (!raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t') && !lines.isEmpty()) {
                lines.set(lines.size() - 1, lines.getLast() + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        return lines;
    }

    static Property parseLine(String line) {
        int i = 0;
        StringBuilder name = new StringBuilder();
        while (i < line.length() && line.charAt(i) != ';' && line.charAt(i) != ':') name.append(line.charAt(i++));
        Map<String, String> params = new LinkedHashMap<>();
        while (i < line.length() && line.charAt(i) == ';') {
            i++;
            StringBuilder key = new StringBuilder();
            while (i < line.length() && line.charAt(i) != '=' && line.charAt(i) != ':' && line.charAt(i) != ';') {
                key.append(line.charAt(i++));
            }
            StringBuilder value = new StringBuilder();
            if (i < line.length() && line.charAt(i) == '=') {
                i++;
                boolean quoted = false;
                while (i < line.length()) {
                    char c = line.charAt(i);
                    if (c == '"') quoted = !quoted;
                    else if (!quoted && (c == ';' || c == ':')) break;
                    value.append(c);
                    i++;
                }
            }
            params.put(key.toString().toUpperCase(Locale.ROOT), value.toString());
        }
        String value = i < line.length() ? line.substring(i + 1) : "";
        return new Property(name.toString().toUpperCase(Locale.ROOT), params, value);
    }

    static Component parse(String text) {
        Component root = new Component("ROOT");
        List<Component> stack = new ArrayList<>();
        stack.add(root);
        for (String line : unfold(text)) {
            if (line.isBlank()) continue;
            Property p = parseLine(line);
            if (p.name.equals("BEGIN")) {
                Component c = new Component(p.value.toUpperCase(Locale.ROOT));
                stack.getLast().children.add(c);
                stack.add(c);
            } else if (p.name.equals("END")) {
                if (stack.size() > 1) stack.removeLast();
            } else {
                stack.getLast().properties.add(p);
            }
        }
        return root;
    }

    /** Folds at 75 octets and joins with CRLF, as the specification asks. */
    static String fold(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
            if (bytes.length <= 75) {
                out.append(line).append("\r\n");
                continue;
            }
            int width = 0;
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < line.length(); ) {
                int cp = line.codePointAt(i);
                String ch = new String(Character.toChars(cp));
                int len = ch.getBytes(StandardCharsets.UTF_8).length;
                if (width + len > 75) {
                    out.append(current).append("\r\n ");
                    current.setLength(0);
                    width = 1;
                }
                current.append(ch);
                width += len;
                i += Character.charCount(cp);
            }
            out.append(current).append("\r\n");
        }
        return out.toString();
    }

    static String escapeText(String s) {
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\n").replace("\n", "\\n");
    }

    static String unescapeText(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                b.append(n == 'n' || n == 'N' ? '\n' : n);
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    // ---- calendar <-> components ----------------------------------------------------------------

    public static CalendarData read(String text) {
        Component root = parse(text);
        List<Appointment> appointments = new ArrayList<>();
        List<ToDoItem> toDos = new ArrayList<>();
        List<String> extra = new ArrayList<>();
        for (Component calendar : root.children) {
            if (!calendar.name.equals("VCALENDAR")) {
                extra.addAll(calendar.toLines());
                continue;
            }
            for (Component c : calendar.children) {
                switch (c.name) {
                    case "VEVENT" -> {
                        Appointment a = toAppointment(c);
                        if (a != null) appointments.add(a);
                    }
                    case "VTODO" -> {
                        ToDoItem t = toToDo(c);
                        if (t != null) toDos.add(t);
                    }
                    default -> extra.addAll(c.toLines());
                }
            }
        }
        return new CalendarData(appointments, toDos, extra);
    }

    public static String write(CalendarData data) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:" + PRODID);
        // An exception names the occurrence it replaces by the series' start time.
        Map<String, LocalTime> seriesStart = new java.util.HashMap<>();
        for (Appointment a : data.appointments()) if (!a.isException() && a.hasTime()) seriesStart.put(a.uid(), a.start());
        for (Appointment a : data.appointments()) lines.addAll(fromAppointment(a, seriesStart.get(a.uid())).toLines());
        for (ToDoItem t : data.toDos()) lines.addAll(fromToDo(t).toLines());
        lines.addAll(data.extra());
        lines.add("END:VCALENDAR");
        return fold(lines);
    }

    static Appointment toAppointment(Component c) {
        Property dtstart = c.first("DTSTART");
        if (dtstart == null) return null;
        LocalDateTime start = parseDateTime(dtstart);
        if (start == null) return null;
        boolean allDay = isDate(dtstart);
        LocalTime startTime = allDay ? null : start.toLocalTime();
        LocalTime endTime = null;
        int days = 1;
        Property dtend = c.first("DTEND");
        if (allDay && dtend != null) {
            LocalDateTime end = parseDateTime(dtend);
            if (end != null) days = (int) Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate()));
        } else if (allDay && c.value("DURATION") != null) {
            try {
                days = (int) Math.max(1, java.time.Duration.parse(c.value("DURATION")).toDays());
            } catch (DateTimeParseException ignored) {
                // no usable duration
            }
        }
        if (!allDay && dtend != null) {
            LocalDateTime end = parseDateTime(dtend);
            if (end != null && end.toLocalDate().equals(start.toLocalDate())) endTime = end.toLocalTime();
            else if (end != null && end.isAfter(start)) endTime = LocalTime.of(23, 59);
        } else if (!allDay && c.value("DURATION") != null) {
            try {
                Duration d = Duration.parse(c.value("DURATION"));
                LocalDateTime end = start.plus(d);
                endTime = end.toLocalDate().equals(start.toLocalDate()) ? end.toLocalTime() : LocalTime.of(23, 59);
            } catch (DateTimeParseException ignored) {
                // no usable duration
            }
        }
        String summary = c.value("SUMMARY") == null ? "" : unescapeText(c.value("SUMMARY"));
        String description = c.value("DESCRIPTION") == null ? "" : unescapeText(c.value("DESCRIPTION"));
        String what = description.isBlank() ? summary
                : description.startsWith(summary) ? description : summary + "\n" + description;
        List<Reminder> reminders = new ArrayList<>();
        // Unknown properties first, then the sub-components the editor cannot show, as raw lines.
        List<String> extra = new ArrayList<>();
        for (Property p : c.properties) if (!EVENT_KNOWN.contains(p.name)) extra.add(p.toLine());
        for (Component child : c.children) {
            if (child.name.equals("VALARM")) {
                Reminder r = toReminder(child);
                if (r != null) reminders.add(r);
                else extra.addAll(child.toLines());
            } else {
                extra.addAll(child.toLines());
            }
        }
        String uid = c.value("UID") == null || c.value("UID").isBlank() ? Appointment.newUid() : c.value("UID");
        Set<LocalDate> excluded = new java.util.HashSet<>();
        for (Property p : c.properties) {
            if (!p.name.equals("EXDATE")) continue;
            for (String value : p.value.split(",")) {
                LocalDateTime d = parseDateTime(new Property("EXDATE", p.params, value.strip()));
                if (d != null) excluded.add(d.toLocalDate());
            }
        }
        LocalDate recurrenceId = null;
        Property rid = c.first("RECURRENCE-ID");
        if (rid != null) {
            LocalDateTime r = parseDateTime(rid);
            if (r != null) recurrenceId = r.toLocalDate();
        }
        return new Appointment(uid, start.toLocalDate(), startTime, endTime, what, reminders,
                parseRule(c.value("RRULE")), Privacy.fromIcsClass(c.value("CLASS") == null ? "PUBLIC" : c.value("CLASS")),
                extra, days, excluded, recurrenceId);
    }

    static Component fromAppointment(Appointment a) {
        return fromAppointment(a, a.start());
    }

    /** @param seriesStart the start time of the series an exception belongs to, null for a date-only series */
    static Component fromAppointment(Appointment a, LocalTime seriesStart) {
        Component c = new Component("VEVENT");
        c.add("UID", a.uid());
        c.add("DTSTAMP", UTC.format(ZonedDateTime.now(ZoneOffset.UTC)));
        if (a.hasTime()) {
            c.add("DTSTART", DATE_TIME.format(a.date().atTime(a.start())));
            if (a.hasEnd()) c.add("DTEND", DATE_TIME.format(a.date().atTime(a.end())));
        } else {
            c.add("DTSTART", Map.of("VALUE", "DATE"), DATE.format(a.date()));
            if (a.days() > 1) c.add("DTEND", Map.of("VALUE", "DATE"), DATE.format(a.date().plusDays(a.days())));
        }
        if (a.isException()) {
            if (seriesStart != null) c.add("RECURRENCE-ID", DATE_TIME.format(a.recurrenceId().atTime(seriesStart)));
            else c.add("RECURRENCE-ID", Map.of("VALUE", "DATE"), DATE.format(a.recurrenceId()));
        }
        c.add("SUMMARY", escapeText(a.summary()));
        if (a.what().contains("\n")) c.add("DESCRIPTION", escapeText(a.what()));
        c.add("CLASS", a.privacy().icsClass());
        if (a.repeat().repeats()) c.add("RRULE", formatRule(a.repeat(), a.date()));
        if (!a.excluded().isEmpty()) {
            List<String> dates = new ArrayList<>();
            for (LocalDate d : new java.util.TreeSet<>(a.excluded())) {
                dates.add(a.hasTime() ? DATE_TIME.format(d.atTime(a.start())) : DATE.format(d));
            }
            if (a.hasTime()) c.add("EXDATE", String.join(",", dates));
            else c.add("EXDATE", Map.of("VALUE", "DATE"), String.join(",", dates));
        }
        List<String> block = extraProperties(c, a.extra());
        for (Reminder r : a.reminders()) c.children.add(fromReminder(r, a.summary()));
        if (!block.isEmpty()) c.children.addAll(parse(String.join("\r\n", block)).children);
        return c;
    }

    /**
     * Adds the kept properties (the lines before the first {@code BEGIN:}) to the component and returns
     * the remaining lines, the kept sub-components.
     */
    private static List<String> extraProperties(Component c, List<String> extra) {
        List<String> block = new ArrayList<>();
        for (String line : extra) {
            if (line.startsWith("BEGIN:") || !block.isEmpty()) block.add(line);
            else c.properties.add(parseLine(line));
        }
        return block;
    }

    static Reminder toReminder(Component alarm) {
        String action = alarm.value("ACTION");
        Property trigger = alarm.first("TRIGGER");
        if (action == null || trigger == null) return null;
        Duration before;
        try {
            Duration d = Duration.parse(trigger.value);
            before = d.isNegative() ? d.negated() : Duration.ZERO;
        } catch (DateTimeParseException e) {
            return null;
        }
        if ("DATE-TIME".equalsIgnoreCase(trigger.param("VALUE"))) return null;
        ReminderType type = switch (action.toUpperCase(Locale.ROOT)) {
            case "AUDIO" -> ReminderType.BEEP;
            case "EMAIL" -> ReminderType.MAIL;
            case "DISPLAY" -> "TRUE".equalsIgnoreCase(alarm.value("X-AMADE-FLASH")) ? ReminderType.FLASH
                    : ReminderType.POPUP;
            default -> null;
        };
        if (type == null) return null;
        String mailTo = "";
        Property attendee = alarm.first("ATTENDEE");
        if (attendee != null) mailTo = attendee.value.replaceFirst("(?i)^mailto:", "");
        return new Reminder(type, before, mailTo);
    }

    static Component fromReminder(Reminder r, String summary) {
        Component alarm = new Component("VALARM");
        String action = switch (r.type()) {
            case BEEP -> "AUDIO";
            case MAIL -> "EMAIL";
            default -> "DISPLAY";
        };
        alarm.add("ACTION", action);
        alarm.add("TRIGGER", "-" + r.before());
        if (r.type() != ReminderType.BEEP) {
            alarm.add("DESCRIPTION", escapeText(summary));
        }
        if (r.type() == ReminderType.FLASH) alarm.add("X-AMADE-FLASH", "TRUE");
        if (r.type() == ReminderType.MAIL) {
            alarm.add("SUMMARY", escapeText(summary));
            if (!r.mailTo().isEmpty()) alarm.add("ATTENDEE", "mailto:" + r.mailTo());
        }
        return alarm;
    }

    static ToDoItem toToDo(Component c) {
        Property due = c.first("DUE");
        if (due == null) due = c.first("DTSTART");
        if (due == null) return null;
        LocalDateTime dueAt = parseDateTime(due);
        if (dueAt == null) return null;
        String summary = c.value("SUMMARY") == null ? "" : unescapeText(c.value("SUMMARY"));
        String description = c.value("DESCRIPTION") == null ? "" : unescapeText(c.value("DESCRIPTION"));
        String what = description.isBlank() ? summary
                : description.startsWith(summary) ? description : summary + "\n" + description;
        boolean completed = "COMPLETED".equalsIgnoreCase(c.value("STATUS"));
        List<String> extra = new ArrayList<>();
        for (Property p : c.properties) if (!TODO_KNOWN.contains(p.name)) extra.add(p.toLine());
        for (Component child : c.children) extra.addAll(child.toLines());
        String uid = c.value("UID") == null || c.value("UID").isBlank() ? ToDoItem.newUid() : c.value("UID");
        return new ToDoItem(uid, dueAt.toLocalDate(), what, completed, extra);
    }

    static Component fromToDo(ToDoItem t) {
        Component c = new Component("VTODO");
        c.add("UID", t.uid());
        c.add("DTSTAMP", UTC.format(ZonedDateTime.now(ZoneOffset.UTC)));
        c.add("DUE", Map.of("VALUE", "DATE"), DATE.format(t.due()));
        c.add("SUMMARY", escapeText(t.summary()));
        if (t.what().contains("\n")) c.add("DESCRIPTION", escapeText(t.what()));
        c.add("STATUS", t.completed() ? "COMPLETED" : "NEEDS-ACTION");
        if (t.completed()) c.add("PERCENT-COMPLETE", "100");
        List<String> block = extraProperties(c, t.extra());
        if (!block.isEmpty()) c.children.addAll(parse(String.join("\r\n", block)).children);
        return c;
    }

    // ---- values ---------------------------------------------------------------------------------

    static boolean isDate(Property p) {
        return "DATE".equalsIgnoreCase(p.param("VALUE")) || p.value.length() == 8;
    }

    static LocalDateTime parseDateTime(Property p) {
        String v = p.value.strip();
        try {
            if (isDate(p)) return LocalDate.parse(v, DATE).atStartOfDay();
            boolean utc = v.endsWith("Z");
            LocalDateTime local = LocalDateTime.parse(utc ? v.substring(0, v.length() - 1) : v, DATE_TIME);
            if (utc) {
                return local.atZone(ZoneOffset.UTC).withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }
            String tzid = p.param("TZID");
            if (tzid != null) {
                try {
                    return local.atZone(java.time.ZoneId.of(tzid)).withZoneSameInstant(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (java.time.DateTimeException ignored) {
                    // unknown zone: take the wall-clock time as it is
                }
            }
            return local;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Repeat parseRule(String rule) {
        if (rule == null || rule.isBlank()) return Repeat.ONCE;
        Repeat.Frequency frequency = null;
        int interval = 1;
        int count = 0;
        LocalDate until = null;
        Set<DayOfWeek> byDay = EnumSet.noneOf(DayOfWeek.class);
        boolean byWeekdayOfMonth = false;
        for (String part : rule.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            String key = part.substring(0, eq).toUpperCase(Locale.ROOT);
            String value = part.substring(eq + 1).strip();
            try {
                switch (key) {
                    case "FREQ" -> frequency = switch (value.toUpperCase(Locale.ROOT)) {
                        case "DAILY" -> Repeat.Frequency.DAILY;
                        case "WEEKLY" -> Repeat.Frequency.WEEKLY;
                        case "MONTHLY" -> Repeat.Frequency.MONTHLY;
                        case "YEARLY" -> Repeat.Frequency.YEARLY;
                        default -> null;
                    };
                    case "INTERVAL" -> interval = Integer.parseInt(value);
                    case "COUNT" -> count = Integer.parseInt(value);
                    // UNTIL is a date or a UTC date-time; the day it names is the last possible day.
                    case "UNTIL" -> until = LocalDate.parse(value.substring(0, 8), DATE);
                    case "BYDAY" -> {
                        for (String day : value.split(",")) {
                            String d = day.strip().toUpperCase(Locale.ROOT);
                            if (d.length() > 2) byWeekdayOfMonth = true;
                            DayOfWeek dow = dayOfWeek(d.substring(d.length() - 2));
                            if (dow != null) byDay.add(dow);
                        }
                    }
                    default -> {
                        // BYMONTHDAY and the rest are implied by the first date
                    }
                }
            } catch (RuntimeException e) {
                // a malformed part is ignored, the rest of the rule still applies
            }
        }
        if (frequency == null) return Repeat.ONCE;
        if (frequency != Repeat.Frequency.WEEKLY) byDay = EnumSet.noneOf(DayOfWeek.class);
        return new Repeat(frequency, interval, byDay, frequency == Repeat.Frequency.MONTHLY && byWeekdayOfMonth,
                count, until);
    }

    static String formatRule(Repeat r) {
        StringBuilder b = new StringBuilder("FREQ=").append(r.frequency().name());
        if (r.interval() > 1) b.append(";INTERVAL=").append(r.interval());
        if (r.count() > 0) b.append(";COUNT=").append(r.count());
        if (r.until() != null) b.append(";UNTIL=").append(DATE.format(r.until()));
        if (r.frequency() == Repeat.Frequency.WEEKLY && !r.byDay().isEmpty()) {
            b.append(";BYDAY=");
            List<String> days = new ArrayList<>();
            for (DayOfWeek d : DayOfWeek.values()) if (r.byDay().contains(d)) days.add(d.name().substring(0, 2));
            b.append(String.join(",", days));
        }
        return b.toString();
    }

    /** Monthly by weekday needs the first date; the writer adds BYDAY=nXX from it. */
    static String formatRule(Repeat r, LocalDate first) {
        String rule = formatRule(r);
        if (r.frequency() == Repeat.Frequency.MONTHLY && r.byWeekdayOfMonth()) {
            int ordinal = (first.getDayOfMonth() - 1) / 7 + 1;
            rule += ";BYDAY=" + ordinal + first.getDayOfWeek().name().substring(0, 2);
        }
        return rule;
    }

    private static DayOfWeek dayOfWeek(String two) {
        for (DayOfWeek d : DayOfWeek.values()) if (d.name().startsWith(two)) return d;
        return null;
    }
}
