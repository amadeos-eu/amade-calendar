package org.datazuul.amadeos.calendar.service;

import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.Reminder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the reminders that became due since the last check. Each reminder of each occurrence fires
 * once; reminders that were due before the program started are not caught up on, as in dtcm.
 */
public final class ReminderCheck {
    /** A reminder that is due now. */
    public record Due(Occurrence occurrence, Reminder reminder) {
    }

    private final CalendarService calendar;
    private LocalDateTime lastCheck;
    private final Set<String> fired = new HashSet<>();

    public ReminderCheck(CalendarService calendar, LocalDateTime start) {
        this.calendar = calendar;
        this.lastCheck = start;
    }

    /** The reminders due between the previous call and {@code now}, oldest first. */
    public List<Due> check(LocalDateTime now) {
        List<Due> due = new ArrayList<>();
        if (!now.isAfter(lastCheck)) return due;
        LocalDateTime from = lastCheck;
        // An occurrence can be reminded at most one day ahead of its start (the editor's limit), so
        // yesterday to tomorrow covers every reminder that can be due now.
        for (Occurrence o : calendar.occurrencesBetween(from.toLocalDate().minusDays(1), now.toLocalDate().plusDays(2))) {
            for (Reminder r : o.appointment().reminders()) {
                LocalDateTime at = o.start().minus(r.before());
                if (at.isAfter(from) && !at.isAfter(now)) {
                    String key = o.appointment().uid() + "|" + o.date() + "|" + r.type() + "|" + r.before();
                    if (fired.add(key)) due.add(new Due(o, r));
                }
            }
        }
        lastCheck = now;
        due.sort((a, b) -> a.occurrence().start().compareTo(b.occurrence().start()));
        return due;
    }
}
