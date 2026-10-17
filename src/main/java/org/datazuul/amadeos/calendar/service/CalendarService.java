package org.datazuul.amadeos.calendar.service;

import org.datazuul.amadeos.calendar.domain.Appointment;
import org.datazuul.amadeos.calendar.domain.CalendarData;
import org.datazuul.amadeos.calendar.domain.Occurrence;
import org.datazuul.amadeos.calendar.domain.ToDoItem;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The calendar in memory: appointments and To Do items by UID, and the queries the views need.
 * Not thread-safe; used from the EDT only. Persistence is the presenter's job.
 */
public final class CalendarService {
    private final Map<String, Appointment> appointments = new LinkedHashMap<>();
    private final Map<String, ToDoItem> toDos = new LinkedHashMap<>();
    private List<String> extra = List.of();

    public void load(CalendarData data) {
        appointments.clear();
        toDos.clear();
        for (Appointment a : data.appointments()) appointments.put(a.uid(), a);
        for (ToDoItem t : data.toDos()) toDos.put(t.uid(), t);
        extra = data.extra();
    }

    public CalendarData data() {
        return new CalendarData(new ArrayList<>(appointments.values()), new ArrayList<>(toDos.values()), extra);
    }

    // ---- appointments ---------------------------------------------------------------------------

    public Optional<Appointment> appointment(String uid) {
        return Optional.ofNullable(appointments.get(uid));
    }

    /** Inserts or replaces (same UID). */
    public void put(Appointment appointment) {
        appointments.put(appointment.uid(), appointment);
    }

    public boolean removeAppointment(String uid) {
        return appointments.remove(uid) != null;
    }

    /** The occurrences on one day, in {@link Occurrence#ORDER}. */
    public List<Occurrence> occurrencesOn(LocalDate day) {
        return occurrencesBetween(day, day);
    }

    /** All occurrences from {@code from} to {@code to} (inclusive), in {@link Occurrence#ORDER}. */
    public List<Occurrence> occurrencesBetween(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (Appointment a : appointments.values()) {
            for (LocalDate date : a.repeat().occurrences(a.date(), from, to)) result.add(new Occurrence(a, date));
        }
        result.sort(Occurrence.ORDER);
        return result;
    }

    /** True if the day has at least one appointment (for the marks in the mini calendars). */
    public boolean hasAppointments(LocalDate day) {
        for (Appointment a : appointments.values()) if (a.repeat().occursOn(a.date(), day)) return true;
        return false;
    }

    /**
     * Occurrences whose description contains {@code text} (case-insensitive) between the dates; an
     * appointment that repeats is listed once per occurrence, as in dtcm's Find window.
     */
    public List<Occurrence> find(String text, LocalDate from, LocalDate to) {
        String needle = text.strip();
        if (needle.isEmpty()) return List.of();
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence o : occurrencesBetween(from, to)) if (o.appointment().matches(needle)) result.add(o);
        return result;
    }

    // ---- To Do items ----------------------------------------------------------------------------

    public Optional<ToDoItem> toDo(String uid) {
        return Optional.ofNullable(toDos.get(uid));
    }

    public void put(ToDoItem item) {
        toDos.put(item.uid(), item);
    }

    public boolean removeToDo(String uid) {
        return toDos.remove(uid) != null;
    }

    /** All To Do items, earliest due date first. */
    public List<ToDoItem> toDos() {
        List<ToDoItem> result = new ArrayList<>(toDos.values());
        result.sort(Comparator.comparing(ToDoItem::due).thenComparing(ToDoItem::summary));
        return result;
    }

    /** The To Do items due between the dates (inclusive), earliest first. */
    public List<ToDoItem> toDosBetween(LocalDate from, LocalDate to) {
        List<ToDoItem> result = new ArrayList<>();
        for (ToDoItem t : toDos()) if (!t.due().isBefore(from) && !t.due().isAfter(to)) result.add(t);
        return result;
    }

    public int appointmentCount() {
        return appointments.size();
    }

    public int toDoCount() {
        return toDos.size();
    }
}
