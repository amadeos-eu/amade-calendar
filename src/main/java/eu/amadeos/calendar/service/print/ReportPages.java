package eu.amadeos.calendar.service.print;

import eu.amadeos.calendar.domain.CalendarView;
import eu.amadeos.calendar.domain.Occurrence;
import eu.amadeos.calendar.domain.Privacy;
import eu.amadeos.calendar.domain.ToDoItem;
import eu.amadeos.calendar.service.CalendarService;
import eu.amadeos.calendar.service.DateFormats;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.print.PageFormat;
import java.awt.print.Pageable;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The pages of a print job, painted in black on white: one page per day, week, month or year of the
 * range, or the Appointment List and To Do List broken into pages of rows. The pages are built when
 * the object is created, so the page count is known before printing starts.
 */
public final class ReportPages implements Pageable, Printable {
    /** Texts the pages need, in the user's language. */
    public interface Labels {
        String reportTitle(ReportType type);

        String footer();

        String busy();

        String column(String id);
    }

    private static final double MARGIN = 36; // half an inch
    private static final double ROW = 12;

    private final PrintRequest request;
    private final CalendarService calendar;
    private final DateFormats formats;
    private final Labels labels;
    private final PageFormat format;
    private final List<Page> pages = new ArrayList<>();

    private interface Page {
        void paint(Graphics2D g, double width, double height);
    }

    public ReportPages(PrintRequest request, CalendarService calendar, DateFormats formats, Labels labels, Paper paper) {
        this.request = request;
        this.calendar = calendar;
        this.formats = formats;
        this.labels = labels;
        format = new PageFormat();
        format.setPaper(paper);
        format.setOrientation(request.type().landscape() ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        build();
    }

    /** A4 unless the locale prints on Letter. */
    public static Paper paperFor(Locale locale) {
        Paper paper = new Paper();
        boolean letter = List.of("US", "CA", "MX").contains(locale.getCountry());
        double w = letter ? 612 : 595.3;
        double h = letter ? 792 : 841.9;
        paper.setSize(w, h);
        paper.setImageableArea(MARGIN, MARGIN, w - 2 * MARGIN, h - 2 * MARGIN);
        return paper;
    }

    private Locale locale() {
        return formats.locale();
    }

    private void build() {
        switch (request.type()) {
            case DAY -> eachPeriod(CalendarView.DAY, d -> (g, w, h) -> dayPage(g, w, h, d));
            case WEEK -> eachPeriod(CalendarView.WEEK, d -> (g, w, h) -> weekPage(g, w, h, d));
            case MONTH -> eachPeriod(CalendarView.MONTH, d -> (g, w, h) -> monthPage(g, w, h, YearMonth.from(d)));
            case YEAR -> eachPeriod(CalendarView.YEAR, d -> (g, w, h) -> yearPage(g, w, h, d.getYear()));
            case APPOINTMENT_LIST -> appointmentList();
            case TODO_LIST -> toDoList();
        }
        if (pages.isEmpty()) pages.add((g, w, h) -> header(g, w, labels.reportTitle(request.type()), rangeTitle()));
    }

    private void eachPeriod(CalendarView view, Function<LocalDate, Page> page) {
        LocalDate date = view.periodStart(request.from(), locale());
        int guard = 0;
        while (!date.isAfter(request.to()) && guard++ < 400) {
            pages.add(page.apply(date));
            date = view.step(date, 1);
        }
    }

    private String rangeTitle() {
        return request.from().equals(request.to()) ? formats.longDate(request.from())
                : formats.date(request.from()) + " - " + formats.date(request.to());
    }

    // ---- Pageable / Printable -------------------------------------------------------------------

    @Override
    public int getNumberOfPages() {
        return pages.size();
    }

    @Override
    public PageFormat getPageFormat(int pageIndex) {
        return format;
    }

    @Override
    public Printable getPrintable(int pageIndex) {
        return this;
    }

    @Override
    public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= pages.size()) return NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
        g.setColor(Color.BLACK);
        double width = pageFormat.getImageableWidth();
        double height = pageFormat.getImageableHeight();
        pages.get(pageIndex).paint(g, width, height);
        g.setFont(PageText.SMALL);
        FontMetrics fm = g.getFontMetrics();
        String footer = labels.footer() + "   " + (pageIndex + 1) + "/" + pages.size();
        g.drawString(footer, (float) (width - fm.stringWidth(footer)), (float) height);
        g.dispose();
        return PAGE_EXISTS;
    }

    /** Paints a page for tests and previews; the index is 0-based. */
    public void paintPage(Graphics2D g, int pageIndex, double width, double height) {
        g.setColor(Color.BLACK);
        pages.get(pageIndex).paint(g, width, height);
    }

    // ---- what is shown of an appointment --------------------------------------------------------

    /** The text to print for an occurrence, empty if the privacy filter leaves it out. */
    String textOf(Occurrence o) {
        Privacy privacy = o.appointment().privacy();
        if (request.settings().asOthersSee()) {
            if (privacy == Privacy.NOTHING) return "";
            if (privacy == Privacy.TIME_ONLY) return labels.busy();
        }
        return o.appointment().summary();
    }

    private String timeAnd(Occurrence o) {
        String time = formats.timeRange(o.appointment().start(), o.appointment().end());
        String text = textOf(o);
        return time.isEmpty() ? text : time + "  " + text;
    }

    private List<Occurrence> shown(LocalDate from, LocalDate to) {
        List<Occurrence> result = new ArrayList<>();
        for (Occurrence o : calendar.occurrencesBetween(from, to)) if (!textOf(o).isEmpty()) result.add(o);
        return result;
    }

    // ---- page parts -----------------------------------------------------------------------------

    private double header(Graphics2D g, double width, String title, String subtitle) {
        g.setFont(PageText.TITLE);
        g.drawString(title, 0, PageText.TITLE.getSize());
        g.setFont(PageText.BODY);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(subtitle, (float) (width - fm.stringWidth(subtitle)), PageText.TITLE.getSize());
        double y = PageText.TITLE.getSize() + 6;
        g.draw(new Line2D.Double(0, y, width, y));
        return y + 8;
    }

    private void dayPage(Graphics2D g, double width, double height, LocalDate day) {
        double y = header(g, width, formats.longDate(day), labels.reportTitle(ReportType.DAY));
        List<Occurrence> items = shown(day, day);
        int begin = 8;
        int end = 18;
        for (Occurrence o : items) {
            if (!o.appointment().hasTime()) continue;
            begin = Math.min(begin, o.appointment().start().getHour());
            end = Math.max(end, (o.appointment().hasEnd() ? o.appointment().end() : o.appointment().start().plusHours(1)).getHour() + 1);
        }
        g.setFont(PageText.BODY);
        FontMetrics fm = g.getFontMetrics();
        double labelWidth = 48;
        // Untimed appointments first.
        for (Occurrence o : items) {
            if (o.appointment().hasTime()) continue;
            g.drawString(PageText.fit(textOf(o), fm, width - labelWidth), (float) labelWidth, (float) (y + fm.getAscent()));
            y += ROW;
        }
        if (y > PageText.TITLE.getSize() + 14) y += 4;
        double rowHeight = Math.max(ROW * 2, (height - y - 10) / Math.max(1, end - begin));
        for (int hour = begin; hour < end; hour++) {
            g.draw(new Line2D.Double(0, y, width, y));
            g.setFont(PageText.HEADING);
            g.drawString(formats.hourLabel(hour), 0, (float) (y + g.getFontMetrics().getAscent() + 2));
            g.setFont(PageText.BODY);
            double line = y + fm.getAscent() + 2;
            for (Occurrence o : items) {
                if (!o.appointment().hasTime() || o.appointment().start().getHour() != hour) continue;
                if (line > y + rowHeight - 2) break;
                g.drawString(PageText.fit(timeAnd(o), fm, width - labelWidth), (float) labelWidth, (float) line);
                line += ROW;
            }
            y += rowHeight;
        }
        g.draw(new Line2D.Double(0, y, width, y));
    }

    private void weekPage(Graphics2D g, double width, double height, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        double y = header(g, width, formats.date(weekStart) + " - " + formats.date(weekEnd), labels.reportTitle(ReportType.WEEK));
        double columnWidth = width / 7;
        double bottom = height - 10;
        g.setFont(PageText.HEADING);
        FontMetrics hfm = g.getFontMetrics();
        double headerHeight = hfm.getHeight() * 2 + 4;
        for (int d = 0; d < 7; d++) {
            LocalDate date = weekStart.plusDays(d);
            double x = d * columnWidth;
            g.draw(new Rectangle2D.Double(x, y, columnWidth, bottom - y));
            g.setFont(PageText.HEADING);
            g.drawString(PageText.fit(date.getDayOfWeek().getDisplayName(TextStyle.FULL_STANDALONE, locale()), hfm, columnWidth - 6),
                    (float) (x + 3), (float) (y + hfm.getAscent() + 2));
            g.drawString(formats.date(date), (float) (x + 3), (float) (y + hfm.getHeight() + hfm.getAscent() + 2));
            g.draw(new Line2D.Double(x, y + headerHeight, x + columnWidth, y + headerHeight));
            g.setFont(PageText.SMALL);
            FontMetrics sfm = g.getFontMetrics();
            double line = y + headerHeight + sfm.getAscent() + 2;
            for (Occurrence o : shown(date, date)) {
                if (line + sfm.getHeight() > bottom) break;
                String time = formats.timeRange(o.appointment().start(), o.appointment().end());
                if (!time.isEmpty()) {
                    g.drawString(PageText.fit(time, sfm, columnWidth - 6), (float) (x + 3), (float) line);
                    line += sfm.getHeight();
                }
                g.drawString(PageText.fit(textOf(o), sfm, columnWidth - 6), (float) (x + 3), (float) line);
                line += sfm.getHeight() + 2;
            }
        }
    }

    private void monthPage(Graphics2D g, double width, double height, YearMonth month) {
        double y = header(g, width, formats.monthAndYear(month.atDay(1)), labels.reportTitle(ReportType.MONTH));
        DayOfWeek first = WeekFields.of(locale()).getFirstDayOfWeek();
        int offset = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
        int rows = (offset + month.lengthOfMonth() + 6) / 7;
        double columnWidth = width / 7;
        g.setFont(PageText.HEADING);
        FontMetrics hfm = g.getFontMetrics();
        for (int i = 0; i < 7; i++) {
            String name = first.plus(i).getDisplayName(TextStyle.FULL_STANDALONE, locale());
            g.drawString(PageText.fit(name, hfm, columnWidth - 6), (float) (i * columnWidth + 3), (float) (y + hfm.getAscent()));
        }
        y += hfm.getHeight() + 2;
        double rowHeight = (height - y - 10) / rows;
        g.setFont(PageText.SMALL);
        FontMetrics sfm = g.getFontMetrics();
        for (int r = 0; r <= rows; r++) g.draw(new Line2D.Double(0, y + r * rowHeight, width, y + r * rowHeight));
        for (int c = 0; c <= 7; c++) g.draw(new Line2D.Double(c * columnWidth, y, c * columnWidth, y + rows * rowHeight));
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int index = offset + day - 1;
            double x = (index % 7) * columnWidth;
            double top = y + (index / 7) * rowHeight;
            g.setFont(PageText.HEADING);
            g.drawString(Integer.toString(day), (float) (x + 3), (float) (top + hfm.getAscent() + 2));
            g.setFont(PageText.SMALL);
            double line = top + hfm.getHeight() + sfm.getAscent() + 2;
            LocalDate date = month.atDay(day);
            for (Occurrence o : shown(date, date)) {
                if (line > top + rowHeight - 2) break;
                String text = o.appointment().hasTime() ? formats.time(o.appointment().start()) + " " + textOf(o) : textOf(o);
                g.drawString(PageText.fit(text, sfm, columnWidth - 6), (float) (x + 3), (float) line);
                line += sfm.getHeight();
            }
        }
    }

    private void yearPage(Graphics2D g, double width, double height, int year) {
        double y = header(g, width, Integer.toString(year), labels.reportTitle(ReportType.YEAR));
        double cellWidth = width / 3;
        double cellHeight = (height - y - 10) / 4;
        DayOfWeek first = WeekFields.of(locale()).getFirstDayOfWeek();
        g.setFont(PageText.SMALL);
        FontMetrics sfm = g.getFontMetrics();
        double column = (cellWidth - 12) / 7;
        for (int m = 0; m < 12; m++) {
            YearMonth month = YearMonth.of(year, m + 1);
            double x = (m % 3) * cellWidth + 6;
            double top = y + (m / 3) * cellHeight;
            g.setFont(PageText.HEADING);
            PageText.centered(g, formats.monthName(month.getMonth()), x + (cellWidth - 12) / 2, top + g.getFontMetrics().getAscent() + 2);
            g.setFont(PageText.SMALL);
            double line = top + PageText.HEADING.getSize() + sfm.getHeight() + 4;
            for (int d = 0; d < 7; d++) {
                PageText.centered(g, first.plus(d).getDisplayName(TextStyle.NARROW_STANDALONE, locale()), x + d * column + column / 2, line);
            }
            line += sfm.getHeight();
            int offset = (month.atDay(1).getDayOfWeek().getValue() - first.getValue() + 7) % 7;
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                int index = offset + day - 1;
                PageText.centered(g, Integer.toString(day), x + (index % 7) * column + column / 2, line + (index / 7) * sfm.getHeight());
            }
        }
    }

    private void appointmentList() {
        List<Occurrence> items = shown(request.from(), request.to());
        table(labels.reportTitle(ReportType.APPOINTMENT_LIST), items,
                new String[]{labels.column("date"), labels.column("time"), labels.column("what")},
                new double[]{0.18, 0.22, 0.60},
                o -> new String[]{formats.date(o.date()), formats.timeRange(o.appointment().start(), o.appointment().end()),
                        request.settings().asOthersSee() && o.appointment().privacy() != Privacy.TIME_AND_TEXT
                                ? textOf(o) : o.appointment().what()});
    }

    private void toDoList() {
        List<ToDoItem> items = calendar.toDosBetween(request.from(), request.to());
        table(labels.reportTitle(ReportType.TODO_LIST), items,
                new String[]{"", labels.column("due"), labels.column("what")},
                new double[]{0.06, 0.18, 0.76},
                t -> new String[]{t.completed() ? "☒" : "☐", formats.date(t.due()), t.what()});
    }

    /** A table broken into pages; multi-line texts wrap inside their column. */
    private <T> void table(String title, List<T> items, String[] columns, double[] widths, Function<T, String[]> row) {
        FontMetrics fm = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics()
                .getFontMetrics(PageText.BODY);
        Paper paper = format.getPaper();
        double width = paper.getImageableWidth();
        double height = paper.getImageableHeight();
        double headerBottom = PageText.TITLE.getSize() + 14 + ROW + 4;
        double bottom = height - 10;
        List<List<String[]>> pageRows = new ArrayList<>();
        List<List<Integer>> pageLines = new ArrayList<>();
        List<String[]> current = new ArrayList<>();
        List<Integer> currentLines = new ArrayList<>();
        double y = headerBottom;
        for (T item : items) {
            String[] cells = row.apply(item);
            int lines = 1;
            for (int c = 0; c < cells.length; c++) {
                lines = Math.max(lines, PageText.wrap(cells[c], fm, widths[c] * width - 6).size());
            }
            if (y + lines * ROW > bottom && !current.isEmpty()) {
                pageRows.add(current);
                pageLines.add(currentLines);
                current = new ArrayList<>();
                currentLines = new ArrayList<>();
                y = headerBottom;
            }
            current.add(cells);
            currentLines.add(lines);
            y += lines * ROW + 2;
        }
        if (!current.isEmpty() || pageRows.isEmpty()) {
            pageRows.add(current);
            pageLines.add(currentLines);
        }
        for (int p = 0; p < pageRows.size(); p++) {
            List<String[]> rows = pageRows.get(p);
            pages.add((g, w, h) -> {
                double top = header(g, w, title, rangeTitle());
                g.setFont(PageText.HEADING);
                double x = 0;
                for (int c = 0; c < columns.length; c++) {
                    g.drawString(columns[c], (float) (x + 3), (float) (top + g.getFontMetrics().getAscent()));
                    x += widths[c] * w;
                }
                top += ROW + 2;
                g.draw(new Line2D.Double(0, top, w, top));
                top += 2;
                g.setFont(PageText.BODY);
                FontMetrics bfm = g.getFontMetrics();
                for (String[] cells : rows) {
                    int lines = 1;
                    x = 0;
                    for (int c = 0; c < cells.length; c++) {
                        List<String> wrapped = PageText.wrap(cells[c], bfm, widths[c] * w - 6);
                        lines = Math.max(lines, wrapped.size());
                        for (int l = 0; l < wrapped.size(); l++) {
                            g.drawString(wrapped.get(l), (float) (x + 3), (float) (top + bfm.getAscent() + l * ROW));
                        }
                        x += widths[c] * w;
                    }
                    top += lines * ROW + 2;
                }
            });
        }
    }

    /** The first and last hour a day page would show; for tests. */
    static LocalTime startOf(Occurrence o) {
        return o.appointment().start();
    }
}
