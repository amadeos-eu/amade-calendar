package eu.amadeos.apps.calendar.domain;

import java.time.LocalDateTime;
import java.util.Comparator;

/**
 * One day on which an appointment takes place: the appointment itself for a one-time appointment, one
 * of its repetitions otherwise.
 */
public record Occurrence(Appointment appointment, java.time.LocalDate date) {
    /** Earlier days first, on a day appointments without a time first, then by start time. */
    public static final Comparator<Occurrence> ORDER = Comparator.comparing(Occurrence::date)
            .thenComparing(o -> o.appointment().hasTime() ? 1 : 0)
            .thenComparing(o -> o.appointment().hasTime() ? o.appointment().start() : java.time.LocalTime.MIDNIGHT)
            .thenComparing(o -> o.appointment().summary());

    public LocalDateTime start() {
        return appointment.startOn(date);
    }

    public LocalDateTime end() {
        return appointment.hasEnd() ? date.atTime(appointment.end()) : start();
    }
}
