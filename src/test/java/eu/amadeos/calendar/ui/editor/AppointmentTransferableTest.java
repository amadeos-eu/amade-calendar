package eu.amadeos.calendar.ui.editor;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.DateOrder;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.Repeat;
import eu.amadeos.calendar.service.DateFormats;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentTransferableTest {
    @Test
    void offersAnIcsFileAndDtcmText() throws Exception {
        Appointment a = new Appointment("u", LocalDate.of(2026, 10, 13), LocalTime.of(9, 0), LocalTime.of(10, 0),
                "Staff meeting: Q4 / budget", List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT);
        AppointmentTransferable t = new AppointmentTransferable(a, new DateFormats(DateOrder.MONTH_DAY_YEAR, '/', false, Locale.US));
        assertTrue(t.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
        assertTrue(t.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertFalse(t.isDataFlavorSupported(DataFlavor.imageFlavor));
        String text = (String) t.getTransferData(DataFlavor.stringFlavor);
        assertTrue(text.contains("** Calendar Appointment **"));
        assertTrue(text.contains("\tDate:\t10/13/2026"));
        @SuppressWarnings("unchecked")
        List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
        assertEquals("Staff meeting Q4  budget.ics", files.getFirst().getName());
        String ics = Files.readString(files.getFirst().toPath());
        assertTrue(ics.contains("BEGIN:VEVENT\r\nUID:u\r\n"));
        assertSame(files.getFirst().getAbsolutePath(), AppointmentTransferable.fileOf(t).getAbsolutePath().intern(),
                "the file is written once");
        assertEquals("appointment.ics", AppointmentTransferable.fileName(new Appointment("v", LocalDate.now(), null, null,
                "???", List.of(), Repeat.ONCE, Privacy.TIME_AND_TEXT)));
    }
}
