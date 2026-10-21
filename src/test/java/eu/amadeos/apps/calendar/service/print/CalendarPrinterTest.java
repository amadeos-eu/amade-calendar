package eu.amadeos.apps.calendar.service.print;

import eu.amadeos.apps.calendar.domain.PrintSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Printable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CalendarPrinterTest {
    private static final Pageable ONE_PAGE = new Pageable() {
        @Override
        public int getNumberOfPages() {
            return 1;
        }

        @Override
        public PageFormat getPageFormat(int pageIndex) {
            return new PageFormat();
        }

        @Override
        public Printable getPrintable(int pageIndex) {
            return (g, pf, i) -> {
                g.drawString("Hello", 100, 100);
                return Printable.PAGE_EXISTS;
            };
        }
    };

    @Test
    void printToFileWritesPostScript(@TempDir Path dir) throws Exception {
        assumeTrue(PostScriptSupport.available(), "no PostScript print service");
        Path target = dir.resolve("out").resolve("calendar.ps");
        CalendarPrinter printer = new CalendarPrinter(command -> {
            throw new AssertionError("no command for a file");
        });
        CalendarPrinter.Outcome outcome = printer.print(ONE_PAGE, new PrintSettings(PrintSettings.Destination.FILE, "", "",
                "calendar.ps", 1, 1, false), target, "Test");
        assertEquals(target, outcome.file());
        assertTrue(Files.readString(target).startsWith("%!PS"));
    }

    // --8<-- [start:lp]
    @Test
    void printerGetsLpWithOptionsAndTheTempFileIsRemoved() throws Exception {
        assumeTrue(PostScriptSupport.available(), "no PostScript print service");
        List<List<String>> commands = new ArrayList<>();
        Path[] seen = new Path[1];
        CalendarPrinter printer = new CalendarPrinter(command -> {
            commands.add(command);
            seen[0] = Path.of(command.getLast());
            assertTrue(Files.size(seen[0]) > 0, "the PostScript exists while lp runs");
            return "request id is Laser-7 (1 file(s))";
        });
        CalendarPrinter.Outcome outcome = printer.print(ONE_PAGE, new PrintSettings(PrintSettings.Destination.PRINTER,
                "Laser", "-o nobanner -o sides=two-sided-long-edge", "x.ps", 1, 2, false), Path.of("unused"), "Test job");
        assertEquals("request id is Laser-7 (1 file(s))", outcome.output());
        assertNull(outcome.file());
        assertEquals(List.of("lp", "-t", "Test job", "-d", "Laser", "-n", "2", "-o", "nobanner", "-o",
                "sides=two-sided-long-edge", seen[0].toString()), commands.getFirst());
        assertFalse(Files.exists(seen[0]));
    }
    // --8<-- [end:lp]

    @Test
    void failedCommandsReportTheirOutput() {
        assertThrows(java.io.IOException.class, () -> CalendarPrinter.runProcess(List.of("sh", "-c", "echo no such printer; exit 1")));
        assertDoesNotThrow(() -> assertEquals("ok", CalendarPrinter.runProcess(List.of("sh", "-c", "echo ok"))));
    }
}
