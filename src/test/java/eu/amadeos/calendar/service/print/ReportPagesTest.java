package eu.amadeos.calendar.service.print;

import eu.amadeos.calendar.domain.Appointment;
import eu.amadeos.calendar.domain.DateOrder;
import eu.amadeos.calendar.domain.PrintSettings;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.Repeat;
import eu.amadeos.calendar.domain.RepeatPreset;
import eu.amadeos.calendar.domain.ToDoItem;
import eu.amadeos.calendar.service.CalendarService;
import eu.amadeos.calendar.service.DateFormats;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class ReportPagesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 13);
    private final CalendarService calendar = new CalendarService();
    private final DateFormats formats = new DateFormats(DateOrder.MONTH_DAY_YEAR, '/', false, Locale.US);
    private final ReportPages.Labels labels = new ReportPages.Labels() {
        @Override
        public String reportTitle(ReportType type) {
            return type.name();
        }

        @Override
        public String footer() {
            return "footer";
        }

        @Override
        public String busy() {
            return "(busy)";
        }

        @Override
        public String column(String id) {
            return id;
        }
    };

    private ReportPages pages(ReportType type, LocalDate from, LocalDate to, boolean asOthersSee) {
        PrintSettings settings = new PrintSettings(PrintSettings.Destination.FILE, "", "", "x.ps", 1, 1, asOthersSee);
        return new ReportPages(new PrintRequest(type, from, to, settings), calendar, formats, labels, ReportPages.paperFor(Locale.US));
    }

    private void fill() {
        calendar.put(new Appointment("a", DAY, LocalTime.of(9, 0), LocalTime.of(10, 0), "Project meeting\nRoom 4", List.of(),
                RepeatPreset.DAILY.toRepeat(0, 1, RepeatPreset.Unit.DAYS), Privacy.TIME_AND_TEXT));
        calendar.put(new Appointment("b", DAY, LocalTime.of(14, 0), null, "Dentist", List.of(), Repeat.ONCE, Privacy.TIME_ONLY));
        calendar.put(new Appointment("c", DAY, null, null, "Secret", List.of(), Repeat.ONCE, Privacy.NOTHING));
        calendar.put(new ToDoItem("t", DAY, "Report", false));
    }

    @Test
    void onePagePerPeriodAndListsByRows() {
        fill();
        assertEquals(3, pages(ReportType.DAY, DAY, DAY.plusDays(2), false).getNumberOfPages());
        assertEquals(2, pages(ReportType.WEEK, DAY, DAY.plusDays(7), false).getNumberOfPages());
        assertEquals(1, pages(ReportType.MONTH, DAY, DAY.plusDays(10), false).getNumberOfPages());
        assertEquals(2, pages(ReportType.YEAR, DAY, DAY.plusYears(1), false).getNumberOfPages());
        assertEquals(1, pages(ReportType.APPOINTMENT_LIST, DAY, DAY.plusDays(20), false).getNumberOfPages());
        assertTrue(pages(ReportType.APPOINTMENT_LIST, DAY, DAY.plusDays(200), false).getNumberOfPages() > 3, "many rows");
        assertEquals(1, pages(ReportType.TODO_LIST, DAY.plusYears(5), DAY.plusYears(5), false).getNumberOfPages(), "an empty page");
        assertEquals(PageFormat.LANDSCAPE, pages(ReportType.WEEK, DAY, DAY, false).getPageFormat(0).getOrientation());
        assertEquals(PageFormat.PORTRAIT, pages(ReportType.DAY, DAY, DAY, false).getPageFormat(0).getOrientation());
    }

    @Test
    void privacyFilterHidesWhatOthersMayNotSee() {
        fill();
        ReportPages mine = pages(ReportType.DAY, DAY, DAY, false);
        ReportPages others = pages(ReportType.DAY, DAY, DAY, true);
        List<eu.amadeos.calendar.domain.Occurrence> today = calendar.occurrencesOn(DAY);
        assertEquals(List.of("Secret", "Project meeting", "Dentist"), today.stream().map(mine::textOf).toList());
        assertEquals(List.of("", "Project meeting", "(busy)"), today.stream().map(others::textOf).toList());
    }

    @Test
    void everyReportPaintsSomething() {
        fill();
        for (ReportType type : ReportType.values()) {
            ReportPages pages = pages(type, DAY, DAY.plusDays(3), false);
            BufferedImage image = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 600, 800);
            PageFormat format = pages.getPageFormat(0);
            assertEquals(java.awt.print.Printable.PAGE_EXISTS, pages.print(g, format, 0), type.name());
            assertEquals(java.awt.print.Printable.NO_SUCH_PAGE, pages.print(g, format, pages.getNumberOfPages()));
            int dark = 0;
            for (int y = 0; y < 800; y += 2) for (int x = 0; x < 600; x += 2) if ((image.getRGB(x, y) & 0xff) < 128) dark++;
            assertTrue(dark > 200, type + " painted " + dark + " dark pixels");
            g.dispose();
        }
    }

    @Test
    void reportTypesFollowTheViews() {
        assertEquals(ReportType.MONTH, ReportType.of(eu.amadeos.calendar.domain.CalendarView.MONTH));
        assertTrue(ReportType.WEEK.landscape());
        assertFalse(ReportType.TODO_LIST.landscape());
        assertEquals(LocalDate.of(2026, 10, 25), ReportType.WEEK.end(LocalDate.of(2026, 10, 13), 2, Locale.GERMANY));
        assertEquals(LocalDate.of(2026, 11, 30), ReportType.MONTH.end(LocalDate.of(2026, 10, 13), 2, Locale.US));
        assertEquals(LocalDate.of(2026, 10, 15), ReportType.APPOINTMENT_LIST.end(LocalDate.of(2026, 10, 13), 3, Locale.US));
    }
}
