package eu.amadeos.apps.calendar.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A To Do item as the CDE To Do Editor edits it.
 *
 * @param uid       iCalendar UID
 * @param due       due date
 * @param what      description; the first line is shown in lists
 * @param completed done or still pending
 * @param extra     iCalendar properties the editor does not know, kept for other programs
 */
public record ToDoItem(String uid, LocalDate due, String what, boolean completed, List<String> extra) {
    public ToDoItem {
        Objects.requireNonNull(uid, "uid");
        Objects.requireNonNull(due, "due");
        what = what == null ? "" : what.strip();
        extra = extra == null ? List.of() : List.copyOf(extra);
    }

    public ToDoItem(String uid, LocalDate due, String what, boolean completed) {
        this(uid, due, what, completed, List.of());
    }

    public static String newUid() {
        return UUID.randomUUID() + "@amade-calendar";
    }

    public String summary() {
        int nl = what.indexOf('\n');
        return (nl < 0 ? what : what.substring(0, nl)).strip();
    }

    public ToDoItem withCompleted(boolean done) {
        return new ToDoItem(uid, due, what, done, extra);
    }
}
