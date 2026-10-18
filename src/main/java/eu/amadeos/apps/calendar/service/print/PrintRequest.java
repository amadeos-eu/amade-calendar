package eu.amadeos.apps.calendar.service.print;

import eu.amadeos.apps.calendar.domain.PrintSettings;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One print job: what to print, for which dates, and the settings (printer, copies, options, file).
 *
 * @param type     the report
 * @param from     first day
 * @param to       last day (inclusive)
 * @param settings printer, options, copies, destination and file, and the privacy filter
 */
public record PrintRequest(ReportType type, LocalDate from, LocalDate to, PrintSettings settings) {
    public PrintRequest {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(settings, "settings");
        if (to == null || to.isBefore(from)) to = from;
    }
}
