package eu.amadeos.apps.calendar.service;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.domain.ToDoItem;
import eu.amadeos.apps.calendar.repository.IcsCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Schedules appointments that arrive as text or files: iCalendar (from other programs, mail
 * attachments, the Drag Appointment icon) or the dtcm appointment text. Entries whose UID is already in the
 * calendar are added as copies, so that nothing of the user's is overwritten.
 */
public final class CalendarImporter {
    /** What an import added. */
    public record Result(List<Appointment> appointments, List<ToDoItem> toDos) {
        public boolean isEmpty() {
            return appointments.isEmpty() && toDos.isEmpty();
        }

        /** The day to show afterwards: the first appointment's, else the first To Do item's, else null. */
        public LocalDate firstDate() {
            if (!appointments.isEmpty()) return appointments.getFirst().date();
            if (!toDos.isEmpty()) return toDos.getFirst().due();
            return null;
        }
    }

    private final CalendarService calendar;

    public CalendarImporter(CalendarService calendar) {
        this.calendar = calendar;
    }

    /** True if the text is iCalendar or dtcm appointment text. */
    public static boolean looksLikeCalendar(String text) {
        return text != null && (text.contains("BEGIN:VCALENDAR") || DtcmText.contains(text));
    }

    public Result importFile(Path file, DateFormats formats) throws IOException {
        return importText(Files.readString(file, StandardCharsets.UTF_8), formats);
    }

    /** Adds everything found in the text to the calendar; an empty result means nothing was found. */
    public Result importText(String text, DateFormats formats) {
        List<Appointment> appointments = new ArrayList<>();
        List<ToDoItem> toDos = new ArrayList<>();
        if (text != null && text.contains("BEGIN:VCALENDAR")) {
            CalendarData data = IcsCodec.read(text);
            java.util.Map<String, String> renamed = new java.util.HashMap<>();
            for (Appointment a : data.appointments()) {
                boolean clash = calendar.appointment(a.uid()).isPresent() || !calendar.exceptionsOf(a.uid()).isEmpty();
                String uid = clash ? renamed.computeIfAbsent(a.uid(), k -> Appointment.newUid()) : a.uid();
                appointments.add(uid.equals(a.uid()) ? a : a.withUid(uid));
            }
            for (ToDoItem t : data.toDos()) {
                toDos.add(calendar.toDo(t.uid()).isPresent()
                        ? new ToDoItem(ToDoItem.newUid(), t.due(), t.what(), t.completed(), t.extra()) : t);
            }
        } else {
            appointments.addAll(DtcmText.parse(text, formats));
        }
        for (Appointment a : appointments) calendar.put(a);
        for (ToDoItem t : toDos) calendar.put(t);
        return new Result(appointments, toDos);
    }
}
