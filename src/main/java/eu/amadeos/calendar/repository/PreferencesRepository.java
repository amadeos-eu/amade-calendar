package eu.amadeos.calendar.repository;

import com.google.inject.Inject;
import eu.amadeos.calendar.domain.CalendarPreferences;
import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.calendar.domain.DateOrder;
import eu.amadeos.calendar.domain.PrintSettings;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.ReminderType;
import eu.amadeos.commons.config.AppDirectories;
import eu.amadeos.commons.config.PropertiesFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Stores {@link CalendarPreferences} in {@code calendar.conf}. Values that are missing take the
 * defaults of the locale.
 */
public class PreferencesRepository {
    static final String FILE_NAME = "calendar.conf";

    private final Path file;
    private final Locale locale;

    @Inject
    public PreferencesRepository(AppDirectories directories, Locale locale) {
        this(directories.configFile(FILE_NAME), locale);
    }

    PreferencesRepository(Path file, Locale locale) {
        this.file = file;
        this.locale = locale;
    }

    public synchronized CalendarPreferences load() {
        Properties p = PropertiesFile.load(file);
        CalendarPreferences d = CalendarPreferences.defaultsFor(locale);
        Map<ReminderType, Duration> reminders = new EnumMap<>(ReminderType.class);
        for (ReminderType type : ReminderType.values()) {
            String minutes = p.getProperty("editor.reminder." + type.name().toLowerCase(Locale.ROOT));
            if (minutes != null && !minutes.isBlank()) {
                reminders.put(type, Duration.ofMinutes(parseInt(minutes, (int) DEFAULT_REMINDER_MINUTES)));
            }
        }
        String separator = p.getProperty("date.separator", String.valueOf(d.dateSeparator()));
        return new CalendarPreferences(
                parseInt(p.getProperty("editor.durationMinutes"), d.defaultDurationMinutes()),
                parseTime(p.getProperty("editor.start"), d.defaultStart()),
                p.containsKey("editor.reminders") ? reminders : d.defaultReminders(),
                p.getProperty("editor.mailTo", d.defaultMailTo()),
                parseEnum(Privacy.class, p.getProperty("editor.privacy"), d.defaultPrivacy()),
                parseInt(p.getProperty("display.dayBegin"), d.dayBegin()),
                parseInt(p.getProperty("display.dayEnd"), d.dayEnd()),
                Boolean.parseBoolean(p.getProperty("display.24hour", Boolean.toString(d.twentyFourHour()))),
                parseEnum(CalendarView.class, p.getProperty("display.defaultView"), d.defaultView()),
                parseEnum(DateOrder.class, p.getProperty("date.order"), d.dateOrder()),
                separator.isEmpty() ? d.dateSeparator() : separator.charAt(0),
                new PrintSettings(
                        parseEnum(PrintSettings.Destination.class, p.getProperty("print.destination"), d.print().destination()),
                        p.getProperty("print.printer", d.print().printerName()),
                        p.getProperty("print.options", d.print().options()),
                        p.getProperty("print.file", d.print().fileName()),
                        parseInt(p.getProperty("print.units"), d.print().units()),
                        parseInt(p.getProperty("print.copies"), d.print().copies()),
                        Boolean.parseBoolean(p.getProperty("print.asOthersSee", Boolean.toString(d.print().asOthersSee())))));
    }

    public synchronized void save(CalendarPreferences prefs) {
        Properties p = new Properties();
        p.setProperty("editor.durationMinutes", Integer.toString(prefs.defaultDurationMinutes()));
        p.setProperty("editor.start", prefs.defaultStart().toString());
        p.setProperty("editor.reminders", "set");
        prefs.defaultReminders().forEach((type, lead) ->
                p.setProperty("editor.reminder." + type.name().toLowerCase(Locale.ROOT), Long.toString(lead.toMinutes())));
        p.setProperty("editor.mailTo", prefs.defaultMailTo());
        p.setProperty("editor.privacy", prefs.defaultPrivacy().name());
        p.setProperty("display.dayBegin", Integer.toString(prefs.dayBegin()));
        p.setProperty("display.dayEnd", Integer.toString(prefs.dayEnd()));
        p.setProperty("display.24hour", Boolean.toString(prefs.twentyFourHour()));
        p.setProperty("display.defaultView", prefs.defaultView().name());
        p.setProperty("date.order", prefs.dateOrder().name());
        p.setProperty("date.separator", String.valueOf(prefs.dateSeparator()));
        p.setProperty("print.destination", prefs.print().destination().name());
        p.setProperty("print.printer", prefs.print().printerName());
        p.setProperty("print.options", prefs.print().options());
        p.setProperty("print.file", prefs.print().fileName());
        p.setProperty("print.units", Integer.toString(prefs.print().units()));
        p.setProperty("print.copies", Integer.toString(prefs.print().copies()));
        p.setProperty("print.asOthersSee", Boolean.toString(prefs.print().asOthersSee()));
        PropertiesFile.store(file, p, "amade-calendar preferences");
    }

    private static final long DEFAULT_REMINDER_MINUTES = 5;

    private static int parseInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static LocalTime parseTime(String value, LocalTime fallback) {
        if (value == null) return fallback;
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException e) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback) {
        if (value == null) return fallback;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
