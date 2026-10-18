package eu.amadeos.apps.calendar.domain;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * @param extra        iCalendar properties the editor does not know, kept for other programs
 * @param days         how many days an appointment without a time spans (a holiday week); 1 for the rest
 * @param excluded     days on which a repeating appointment does not take place ({@code EXDATE})
 * @param recurrenceId for an exception of a series: the day of the occurrence it replaces; the exception
 *                     shares the UID of the series ({@code RECURRENCE-ID}); null for a series or a
 *                     one-time appointment
 */
public record Appointment(String uid, LocalDate date, LocalTime start, LocalTime end, String what,
                          List<Reminder> reminders, Repeat repeat, Privacy privacy, List<String> extra, int days,
                          Set<LocalDate> excluded, LocalDate recurrenceId) {

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
        days = start != null ? 1 : Math.max(1, days);
        excluded = excluded == null || excluded.isEmpty() ? Set.of() : Set.copyOf(excluded);
        if (recurrenceId != null) repeat = Repeat.ONCE;
    }

    public Appointment(String uid, LocalDate date, LocalTime start, LocalTime end, String what, List<Reminder> reminders,
                       Repeat repeat, Privacy privacy) {
        this(uid, date, start, end, what, reminders, repeat, privacy, List.of());
    }

    public Appointment(String uid, LocalDate date, LocalTime start, LocalTime end, String what, List<Reminder> reminders,
                       Repeat repeat, Privacy privacy, List<String> extra) {
        this(uid, date, start, end, what, reminders, repeat, privacy, extra, 1, Set.of(), null);
    }

    /** True for an exception of a series (shares the series' UID, replaces one occurrence). */
    public boolean isException() {
        return recurrenceId != null;
    }

    /** The key an appointment is stored under: exceptions share the UID of their series. */
    public String key() {
        return recurrenceId == null ? uid : uid + "#" + recurrenceId;
    }

    /** The last day of an appointment that spans several days. */
    public LocalDate lastDay() {
        return date.plusDays(days - 1L);
    }

    public Appointment withRepeat(Repeat newRepeat) {
        return new Appointment(uid, date, start, end, what, reminders, newRepeat, privacy, extra, days, excluded, recurrenceId);
    }

    public Appointment withExcluded(Set<LocalDate> newExcluded) {
        return new Appointment(uid, date, start, end, what, reminders, repeat, privacy, extra, days, newExcluded, recurrenceId);
    }

    /** The same appointment as an exception of the series {@code uid} for the occurrence on {@code day}. */
    public Appointment asExceptionOf(String seriesUid, LocalDate day) {
        return new Appointment(seriesUid, date, start, end, what, reminders, Repeat.ONCE, privacy, extra, days, Set.of(), day);
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
        return new Appointment(newUid, date, start, end, what, reminders, repeat, privacy, extra, days, excluded, recurrenceId);
    }

    public Appointment withDate(LocalDate newDate) {
        return new Appointment(uid, newDate, start, end, what, reminders, repeat, privacy, extra, days, excluded, recurrenceId);
    }
}
