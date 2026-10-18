package eu.amadeos.apps.calendar.repository;

import com.google.inject.Inject;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.commons.config.XdgDirectories;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * The user's calendar as one iCalendar file, {@code ~/.local/share/amadeos/calendar/calendar.ics},
 * written atomically and readable only by the owner.
 */
public class IcsCalendarRepository implements CalendarRepository {
    public static final String FILE_NAME = "calendar.ics";

    private final Path file;

    @Inject
    public IcsCalendarRepository(XdgDirectories xdg) {
        this(xdg.dataHome().resolve("amadeos").resolve("calendar").resolve(FILE_NAME));
    }

    public IcsCalendarRepository(Path file) {
        this.file = file;
    }

    @Override
    public synchronized CalendarData load() throws IOException {
        if (!Files.exists(file)) return CalendarData.EMPTY;
        return IcsCodec.read(Files.readString(file, StandardCharsets.UTF_8));
    }

    @Override
    public synchronized void save(CalendarData data) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), FILE_NAME, ".tmp");
        try {
            try {
                Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system
            }
            Files.writeString(temp, IcsCodec.write(data), StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Override
    public String location() {
        return file.toString();
    }
}
