package eu.amadeos.apps.calendar.ui.editor;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.repository.IcsCodec;
import eu.amadeos.apps.calendar.service.DateFormats;
import eu.amadeos.apps.calendar.service.DtcmText;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * What the Drag Appointment icon carries: an iCalendar file for file managers and mail attachment lists, the
 * dtcm appointment text for text fields. The file is written on demand into a temporary directory.
 */
final class AppointmentTransferable implements Transferable {
    private static final DataFlavor[] FLAVORS = {DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
    private final Appointment appointment;
    private final DateFormats formats;
    private Path file;

    AppointmentTransferable(Appointment appointment, DateFormats formats) {
        this.appointment = appointment;
        this.formats = formats;
    }

    @Override
    public DataFlavor[] getTransferDataFlavors() {
        return FLAVORS.clone();
    }

    @Override
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        for (DataFlavor f : FLAVORS) if (f.equals(flavor)) return true;
        return false;
    }

    @Override
    public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
        if (DataFlavor.stringFlavor.equals(flavor)) return DtcmText.format(appointment, formats);
        if (DataFlavor.javaFileListFlavor.equals(flavor)) return List.of(file().toFile());
        throw new UnsupportedFlavorException(flavor);
    }

    /** {@code <summary>.ics} in a temporary directory of its own, deleted when the program ends. */
    synchronized Path file() throws IOException {
        if (file == null) {
            Path directory = Files.createTempDirectory("amade-calendar-");
            directory.toFile().deleteOnExit();
            file = directory.resolve(fileName(appointment));
            Files.writeString(file, IcsCodec.write(new CalendarData(List.of(appointment), List.of(), List.of())),
                    StandardCharsets.UTF_8);
            file.toFile().deleteOnExit();
        }
        return file;
    }

    static String fileName(Appointment a) {
        String base = a.summary().replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        if (base.length() > 40) base = base.substring(0, 40).strip();
        return (base.isEmpty() ? "appointment" : base) + ".ics";
    }

    /** For tests and cleanup. */
    static File fileOf(AppointmentTransferable t) throws IOException {
        return t.file().toFile();
    }
}
