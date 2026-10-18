package eu.amadeos.apps.calendar.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * A reminder that fires {@code before} the start of an appointment.
 *
 * @param type   how the reminder shows itself
 * @param before lead time, never negative
 * @param mailTo address for {@link ReminderType#MAIL}, otherwise empty
 */
public record Reminder(ReminderType type, Duration before, String mailTo) {
    public static final Duration DEFAULT_LEAD = Duration.ofMinutes(5);

    public Reminder {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(before, "before");
        if (before.isNegative()) throw new IllegalArgumentException("Lead time must not be negative: " + before);
        mailTo = mailTo == null ? "" : mailTo.strip();
    }

    public Reminder(ReminderType type, Duration before) {
        this(type, before, "");
    }

    public long minutes() {
        return before.toMinutes();
    }
}
