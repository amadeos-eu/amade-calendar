package eu.amadeos.apps.calendar.ui.views;

import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.apps.calendar.service.DateFormats;

import java.time.DayOfWeek;
import java.time.temporal.WeekFields;
import java.util.Locale;

/** What every view needs: the calendar to show, the formats and the display settings. */
public final class ViewContext {
    private final CalendarService calendar;
    private DateFormats formats;
    private CalendarPreferences prefs;

    public ViewContext(CalendarService calendar, DateFormats formats, CalendarPreferences prefs) {
        this.calendar = calendar;
        this.formats = formats;
        this.prefs = prefs;
    }

    public CalendarService calendar() {
        return calendar;
    }

    public DateFormats formats() {
        return formats;
    }

    public CalendarPreferences prefs() {
        return prefs;
    }

    public Locale locale() {
        return formats.locale();
    }

    public DayOfWeek firstDayOfWeek() {
        return WeekFields.of(formats.locale()).getFirstDayOfWeek();
    }

    public void update(DateFormats newFormats, CalendarPreferences newPrefs) {
        formats = newFormats;
        prefs = newPrefs;
    }
}
