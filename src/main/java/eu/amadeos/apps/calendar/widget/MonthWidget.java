package eu.amadeos.apps.calendar.widget;

import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.repository.IcsCalendarRepository;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import eu.amadeos.commons.widget.WidgetSettings;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// --8<-- [start:month-widget]
/**
 * The calendar's desktop widget: the current month with today in the accent colour, a dot under every day
 * with an appointment, and the next appointments below. It reads the calendar file every minute (only when
 * the file changed) and opens Calendar on a click.
 *
 * <p>The widget runs inside the widget host, not inside Calendar, so it uses its own {@link Messages} rather
 * than the application-wide {@code Texts}, and reads the calendar file itself.
 */
public final class MonthWidget implements DesktopWidget {
    static final String BUNDLE = "eu.amadeos.apps.calendar.messages";

    private static Messages texts(Locale locale) {
        return Messages.load(MonthWidget.class, BUNDLE, locale);
    }

    @Override
    public String id() {
        return "calendar-month";
    }

    @Override
    public String name() {
        return texts(Locale.getDefault()).get("widget.name");
    }

    @Override
    public String description() {
        return texts(Locale.getDefault()).get("widget.description");
    }

    @Override
    public Dimension defaultSize() {
        return new Dimension(260, 300);
    }

    @Override
    public JComponent create(WidgetContext context) {
        Path file = calendarFile();
        Month month = new Month(context, new IcsCalendarRepository(file)::load, file);
        context.every(Duration.ofMinutes(1), month::refresh);
        return month;
    }

    @Override
    public WidgetSettings settings(WidgetContext context) {
        Messages t = texts(context.locale());
        JSpinner count = new JSpinner(new SpinnerNumberModel(upcoming(context), 0, 8, 1));
        JPanel form = new JPanel(new BorderLayout(8, 0));
        form.add(new JLabel(t.get("widget.upcoming")), BorderLayout.WEST);
        form.add(count, BorderLayout.CENTER);
        return new WidgetSettings(form, () -> context.set("upcoming", String.valueOf(count.getValue())));
    }

    static Path calendarFile() {
        return XdgDirectories.system().dataHome().resolve("amadeos").resolve("calendar")
                .resolve(IcsCalendarRepository.FILE_NAME);
    }

    /** How many of the next appointments the widget lists below the month; 3 unless set. */
    static int upcoming(WidgetContext context) {
        try {
            return Math.max(0, Math.min(8, Integer.parseInt(context.get("upcoming", "3").strip())));
        } catch (NumberFormatException e) {
            return 3;
        }
    }

    /** Reads the calendar; an interface so that tests can hand in a calendar without a file. */
    interface Source {
        CalendarData load() throws IOException;
    }

    /** The days of {@code month} that have at least one appointment. */
    static Set<LocalDate> busyDays(CalendarService calendar, YearMonth month) {
        Set<LocalDate> days = new HashSet<>();
        for (Occurrence o : calendar.occurrencesBetween(month.atDay(1), month.atEndOfMonth())) days.add(o.date());
        return days;
    }

    /**
     * The next {@code max} appointments from {@code now} on, within four weeks: ones already over today are
     * left out, and an appointment over several days counts once.
     */
    static List<Occurrence> next(CalendarService calendar, LocalDateTime now, int max) {
        List<Occurrence> result = new ArrayList<>();
        if (max <= 0) return result;
        Set<String> seen = new HashSet<>();
        for (Occurrence o : calendar.occurrencesBetween(now.toLocalDate(), now.toLocalDate().plusWeeks(4))) {
            boolean over = o.date().equals(now.toLocalDate()) && o.appointment().hasTime()
                    && o.end().isBefore(now);
            if (over || o.appointment().days() > 1 && !seen.add(o.appointment().key())) continue;
            result.add(o);
            if (result.size() == max) break;
        }
        return result;
    }

    /** The first day shown in the grid: the start of the week that holds the first of the month. */
    static LocalDate gridStart(YearMonth month, Locale locale) {
        DayOfWeek first = WeekFields.of(locale).getFirstDayOfWeek();
        LocalDate d = month.atDay(1);
        while (d.getDayOfWeek() != first) d = d.minusDays(1);
        return d;
    }

    /** One line for an appointment: "Today 14:00 Dentist", "Tue 3 Nov All day Fair". */
    static String line(Occurrence o, LocalDate today, Locale locale, Messages t) {
        String day = o.date().equals(today) ? t.get("widget.today")
                : o.date().equals(today.plusDays(1)) ? t.get("widget.tomorrow")
                : o.date().format(DateTimeFormatter.ofPattern("EEE d MMM", locale));
        String time = o.appointment().hasTime()
                ? o.start().toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
                : "";
        return (day + " " + time).strip() + "  " + o.appointment().summary();
    }

    static final class Month extends JComponent {
        private final WidgetContext context;
        private final Source source;
        private final Path file;
        private final CalendarService calendar = new CalendarService();
        private FileTime readAt;
        private LocalDate shownDay;

        Month(WidgetContext context, Source source, Path file) {
            this.context = context;
            this.source = source;
            this.file = file;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getButton() == MouseEvent.BUTTON1) context.open("amade-calendar");
                }
            });
        }

        /** Reads the calendar again if the file changed, and repaints when the day changed or it was read. */
        void refresh() {
            FileTime modified = null;
            try {
                modified = file == null || !Files.exists(file) ? FileTime.fromMillis(0) : Files.getLastModifiedTime(file);
            } catch (IOException e) {
                // keep what was read before
            }
            boolean changed = modified != null && !modified.equals(readAt);
            if (changed) {
                try {
                    calendar.load(source.load());
                    readAt = modified;
                } catch (IOException | RuntimeException e) {
                    // a half-written or broken file: try again in a minute
                }
            }
            if (changed || !LocalDate.now().equals(shownDay)) repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            shownDay = LocalDate.now();
            paintMonth((Graphics2D) g0, getWidth(), getHeight(), LocalDateTime.now(), calendar, context);
        }
    }

    static void paintMonth(Graphics2D g0, int w, int h, LocalDateTime now, CalendarService calendar,
                           WidgetContext context) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        WidgetColors c = context.colors();
        Locale locale = context.locale();
        Messages t = texts(locale);
        LocalDate today = now.toLocalDate();
        YearMonth month = YearMonth.from(today);

        List<Occurrence> next = next(calendar, now, upcoming(context));
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, 15);
        Font head = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
        Font dayFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        Font list = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        int listLine = 18;
        int listHeight = next.isEmpty() ? (upcoming(context) == 0 ? 0 : listLine + 8) : next.size() * listLine + 8;

        // Month name, the accent for the name and the year in the text colour
        g.setFont(title);
        FontMetrics fm = g.getFontMetrics();
        String monthName = month.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, locale);
        g.setColor(c.accent());
        g.drawString(monthName, 0, fm.getAscent());
        g.setColor(c.text());
        g.drawString(" " + month.getYear(), fm.stringWidth(monthName), fm.getAscent());
        int top = fm.getHeight() + 6;

        // The grid: a line of weekday letters and six weeks
        int gridHeight = Math.max(60, h - top - listHeight);
        int cellW = Math.max(1, w / 7);
        int cellH = Math.max(1, gridHeight / 7);
        LocalDate start = gridStart(month, locale);
        g.setFont(head);
        fm = g.getFontMetrics();
        g.setColor(c.dim());
        for (int i = 0; i < 7; i++) {
            String wd = start.plusDays(i).getDayOfWeek().getDisplayName(TextStyle.SHORT_STANDALONE, locale);
            wd = wd.substring(0, Math.min(2, wd.length()));
            g.drawString(wd, i * cellW + (cellW - fm.stringWidth(wd)) / 2, top + (cellH + fm.getAscent()) / 2 - 2);
        }
        Set<LocalDate> busy = busyDays(calendar, month);
        g.setFont(dayFont);
        fm = g.getFontMetrics();
        for (int week = 0; week < 6; week++) {
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(week * 7L + i);
                int x = i * cellW;
                int y = top + (week + 1) * cellH;
                String s = String.valueOf(d.getDayOfMonth());
                int sx = x + (cellW - fm.stringWidth(s)) / 2;
                int sy = y + (cellH + fm.getAscent()) / 2 - 2;
                if (d.equals(today)) {
                    int r = Math.min(cellW, cellH) - 2;
                    g.setColor(c.accent());
                    g.fill(new Ellipse2D.Double(x + (cellW - r) / 2.0, y + (cellH - r) / 2.0, r, r));
                    g.setColor(java.awt.Color.WHITE);
                } else {
                    g.setColor(YearMonth.from(d).equals(month) ? c.text() : c.line());
                }
                g.drawString(s, sx, sy);
                if (busy.contains(d) && !d.equals(today)) {
                    g.setColor(c.accent());
                    g.fill(new Ellipse2D.Double(x + cellW / 2.0 - 2, sy + 3, 4, 4));
                }
            }
        }

        // The next appointments
        if (listHeight > 0) {
            int y = top + 7 * cellH + 6;
            g.setColor(c.line());
            g.drawLine(0, y, w, y);
            g.setFont(list);
            fm = g.getFontMetrics();
            y += 4;
            if (next.isEmpty()) {
                g.setColor(c.dim());
                g.drawString(t.get("widget.nothing"), 0, y + fm.getAscent());
            }
            for (Occurrence o : next) {
                g.setColor(o.date().equals(today) ? c.text() : c.dim());
                g.drawString(clip(line(o, today, locale, t), fm, w), 0, y + fm.getAscent());
                y += listLine;
            }
        }
        g.dispose();
    }

    private static String clip(String s, FontMetrics fm, int w) {
        if (fm.stringWidth(s) <= w) return s;
        while (!s.isEmpty() && fm.stringWidth(s + "…") > w) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
// --8<-- [end:month-widget]
