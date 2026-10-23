package eu.amadeos.apps.calendar.ui.views;

import eu.amadeos.apps.calendar.domain.CalendarPreferences;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.apps.calendar.service.DateFormats;
import eu.amadeos.commons.contacts.ContactDirectory;

import java.time.DayOfWeek;
import java.time.temporal.WeekFields;
import java.util.Locale;

/**
 * What every view needs: the calendar to show, the formats and the display settings; and the address books, for
 * the address completion of the editor.
 */
public final class ViewContext {
    private final CalendarService calendar;
    private DateFormats formats;
    private CalendarPreferences prefs;
    private final ContactDirectory contacts;

    public ViewContext(CalendarService calendar, DateFormats formats, CalendarPreferences prefs) {
        this(calendar, formats, prefs, null);
    }

    /** @param contacts the address books, null for none */
    public ViewContext(CalendarService calendar, DateFormats formats, CalendarPreferences prefs, ContactDirectory contacts) {
        this.calendar = calendar;
        this.formats = formats;
        this.prefs = prefs;
        this.contacts = contacts;
    }

    /** The address books, or null. */
    public ContactDirectory contacts() {
        return contacts;
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
