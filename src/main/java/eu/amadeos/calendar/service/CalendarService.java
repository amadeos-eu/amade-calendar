package eu.amadeos.calendar.service;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.CalendarData;
import eu.amadeos.calendar.domain.EditScope;
import eu.amadeos.calendar.domain.Repeat;
import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.calendar.domain.ToDoItem;

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
        for (Appointment a : data.appointments()) appointments.put(a.key(), a);
        for (ToDoItem t : data.toDos()) toDos.put(t.uid(), t);
        extra = data.extra();
    }

    public CalendarData data() {
        return new CalendarData(new ArrayList<>(appointments.values()), new ArrayList<>(toDos.values()), extra);
    }

    // ---- appointments ---------------------------------------------------------------------------

    /** The series or one-time appointment with the UID, not its exceptions. */
    public Optional<Appointment> appointment(String uid) {
        return Optional.ofNullable(appointments.get(uid));
    }

    /** The exceptions of a series, by the day of the occurrence they replace. */
    public Map<LocalDate, Appointment> exceptionsOf(String uid) {
        Map<LocalDate, Appointment> result = new java.util.TreeMap<>();
        for (Appointment a : appointments.values()) if (a.isException() && a.uid().equals(uid)) result.put(a.recurrenceId(), a);
        return result;
    }

    /** Inserts or replaces (same key: UID, plus the recurrence day for an exception). */
    public void put(Appointment appointment) {
        appointments.put(appointment.key(), appointment);
    }

    /** Removes the appointment and, for a series, its exceptions. */
    public boolean removeAppointment(String uid) {
        boolean removed = appointments.remove(uid) != null;
        removed |= appointments.values().removeIf(a -> a.isException() && a.uid().equals(uid));
        return removed;
    }

    // ---- changing and deleting occurrences of a series ------------------------------------------

    /**
     * Applies the edited fields to the occurrence of {@code series} on {@code day} as the scope says.
     * {@code edited} carries the new date, times, text, reminders, repeat, privacy and, for
     * {@link EditScope#ALL}, the days the series leaves out; for {@link EditScope#THIS_ONE} its repeat
     * is ignored.
     */
    public void change(Appointment series, LocalDate day, Appointment edited, EditScope scope) {
        if (series.isException()) {
            appointments.put(series.key(), edited.asExceptionOf(series.uid(), series.recurrenceId()));
            return;
        }
        if (!series.repeat().repeats()) {
            Appointment changed = edited.withUid(series.uid());
            appointments.put(changed.key(), changed);
            return;
        }
        switch (scope) {
            case THIS_ONE -> appointments.put(series.uid() + "#" + day, edited.asExceptionOf(series.uid(), day));
            case FORWARD -> {
                if (day.equals(series.date())) {
                    removeAppointment(series.uid());
                    Appointment changed = edited.withUid(series.uid());
                    appointments.put(changed.key(), changed);
                } else {
                    appointments.put(series.uid(), series.withRepeat(series.repeat().withUntil(day.minusDays(1)).withCount(0)));
                    appointments.values().removeIf(a -> a.isException() && a.uid().equals(series.uid()) && !a.recurrenceId().isBefore(day));
                    Appointment rest = edited.withUid(Appointment.newUid());
                    appointments.put(rest.key(), rest);
                }
            }
            case ALL -> {
                // The series keeps its first day; the fields move with it.
                Appointment changed = edited.withUid(series.uid()).withDate(series.date().plusDays(
                        java.time.temporal.ChronoUnit.DAYS.between(day, edited.date())));
                appointments.put(changed.key(), changed);
            }
        }
    }

    /** Deletes the occurrence of {@code series} on {@code day} as the scope says. */
    public void delete(Appointment series, LocalDate day, EditScope scope) {
        if (!series.repeat().repeats() || series.isException()) {
            appointments.remove(series.key());
            if (series.isException()) {
                // Deleting an exception deletes that occurrence of its series.
                appointment(series.uid()).ifPresent(master -> {
                    java.util.Set<LocalDate> excluded = new java.util.HashSet<>(master.excluded());
                    excluded.add(series.recurrenceId());
                    appointments.put(master.uid(), master.withExcluded(excluded));
                });
            }
            return;
        }
        switch (scope) {
            case THIS_ONE -> {
                java.util.Set<LocalDate> excluded = new java.util.HashSet<>(series.excluded());
                excluded.add(day);
                appointments.put(series.uid(), series.withExcluded(excluded));
                appointments.remove(series.uid() + "#" + day);
            }
            case FORWARD -> {
                if (day.isAfter(series.date())) {
                    appointments.put(series.uid(), series.withRepeat(series.repeat().withUntil(day.minusDays(1)).withCount(0)));
                    appointments.values().removeIf(a -> a.isException() && a.uid().equals(series.uid()) && !a.recurrenceId().isBefore(day));
                } else {
                    removeAppointment(series.uid());
                }
            }
            case ALL -> removeAppointment(series.uid());
        }
    }

    /** The occurrences on one day, in {@link Occurrence#ORDER}. */
    public List<Occurrence> occurrencesOn(LocalDate day) {
        return occurrencesBetween(day, day);
    }

    /** All occurrences from {@code from} to {@code to} (inclusive), in {@link Occurrence#ORDER}. */
    public List<Occurrence> occurrencesBetween(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        Map<String, java.util.Set<LocalDate>> replaced = new java.util.HashMap<>();
        for (Appointment a : appointments.values()) {
            if (a.isException()) replaced.computeIfAbsent(a.uid(), k -> new java.util.HashSet<>()).add(a.recurrenceId());
        }
        for (Appointment a : appointments.values()) {
            java.util.Set<LocalDate> skip = a.isException() ? java.util.Set.of() : replaced.getOrDefault(a.uid(), java.util.Set.of());
            // A multi-day appointment is on every day of its span; look back far enough for its first day.
            LocalDate lookBack = from.minusDays(a.days() - 1L);
            for (LocalDate date : a.repeat().occurrences(a.date(), lookBack, to)) {
                if (a.excluded().contains(date) || skip.contains(date)) continue;
                for (int d = 0; d < a.days(); d++) {
                    LocalDate day = date.plusDays(d);
                    if (!day.isBefore(from) && !day.isAfter(to)) result.add(new Occurrence(a, day));
                }
            }
        }
        result.sort(Occurrence.ORDER);
        return result;
    }

    /** True if the day has at least one appointment (for the marks in the mini calendars). */
    public boolean hasAppointments(LocalDate day) {
        return !occurrencesOn(day).isEmpty();
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
