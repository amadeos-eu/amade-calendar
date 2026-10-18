package eu.amadeos.calendar.repository;

import eu.amadeos.calendar.domain.CalendarData;

import java.io.IOException;

/** Where the user's calendar is kept. */
public interface CalendarRepository {
    CalendarData load() throws IOException;

    void save(CalendarData data) throws IOException;

    /** Where the calendar lives, for messages. */
    String location();
}
