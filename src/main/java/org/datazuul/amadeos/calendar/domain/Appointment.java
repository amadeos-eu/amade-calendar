package org.datazuul.amadeos.calendar.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An appointment as the CDE Appointment Editor edits it. Times are local wall-clock times without a
 * zone, as in dtcm ("floating" in iCalendar).
 *
 * @param uid       iCalendar UID, unique in the calendar
 * @param date      the (first) day
 * @param start     start time, null for an appointment without a time (a day note such as "Halloween")
 * @param end       end time, null if the appointment has no duration; ignored when {@code start} is null
 * @param what      description; the first line is the summary shown in the views
 * @param reminders reminders before the start
 * @param repeat    how it repeats, {@link Repeat#ONCE} if it does not
 * @param privacy   what others may see
 * @param extra     iCalendar properties the editor does not know, kept for other programs
 */
public record Appointment(String uid, LocalDate date, LocalTime start, LocalTime end, String what,
                          List<Reminder> reminders, Repeat repeat, Privacy privacy, List<String> extra) {

    public Appointment {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(date, "date");
        what = what == null ? "" : what.strip();
        if (start == null) end = null;
        if (end != null && !end.isAfter(start)) end = null;
        reminders = reminders == null ? List.of() : List.copyOf(reminders);
        repeat = repeat == null ? Repeat.ONCE : repeat;
        privacy = privacy == null ? Privacy.TIME_AND_TEXT : privacy;
        extra = extra == null ? List.of() : List.copyOf(extra);
    }

    public Appointment(String uid, LocalDate date, LocalTime start, LocalTime end, String what, List<Reminder> reminders,
                       Repeat repeat, Privacy privacy) {
        this(uid, date, start, end, what, reminders, repeat, privacy, List.of());
    }

    public static String newUid() {
        return UUID.randomUUID() + "@amade-calendar";
    }

    /** The first line of the description. */
    public String summary() {
        int nl = what.indexOf('\n');
        return (nl < 0 ? what : what.substring(0, nl)).strip();
    }

    public boolean hasTime() {
        return start != null;
    }

    public boolean hasEnd() {
        return end != null;
    }

    /** Length of an appointment with start and end; zero otherwise. */
    public Duration duration() {
        return hasEnd() ? Duration.between(start, end) : Duration.ZERO;
    }

    /** Start of the occurrence on {@code day}; midnight for an appointment without a time. */
    public LocalDateTime startOn(LocalDate day) {
        return day.atTime(start == null ? LocalTime.MIDNIGHT : start);
    }

    public boolean matches(String text) {
        return what.toLowerCase().contains(text.toLowerCase());
    }

    public Appointment withUid(String newUid) {
        return new Appointment(newUid, date, start, end, what, reminders, repeat, privacy, extra);
    }
}
