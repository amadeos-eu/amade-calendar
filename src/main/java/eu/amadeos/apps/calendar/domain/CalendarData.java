package eu.amadeos.apps.calendar.domain;

import java.util.List;

/**
 * Everything in one calendar file.
 *
 * @param appointments the appointments
 * @param toDos        the To Do items
 * @param extra        iCalendar components the program does not know (time zones, journals), as raw lines
 */
public record CalendarData(List<Appointment> appointments, List<ToDoItem> toDos, List<String> extra) {
    public static final CalendarData EMPTY = new CalendarData(List.of(), List.of(), List.of());

    public CalendarData {
        appointments = List.copyOf(appointments);
        toDos = List.copyOf(toDos);
        extra = extra == null ? List.of() : List.copyOf(extra);
    }
}
