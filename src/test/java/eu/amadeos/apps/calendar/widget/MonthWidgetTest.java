package eu.amadeos.apps.calendar.widget;

import eu.amadeos.apps.calendar.domain.Appointment;
import eu.amadeos.apps.calendar.domain.CalendarData;
import eu.amadeos.apps.calendar.domain.Occurrence;
import eu.amadeos.apps.calendar.domain.Repeat;
import eu.amadeos.apps.calendar.service.CalendarService;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.widget.DesktopWidget;
import eu.amadeos.commons.widget.WidgetColors;
import eu.amadeos.commons.widget.WidgetContext;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MonthWidgetTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 12, 0);

    private static Appointment at(String uid, LocalDate day, LocalTime start, String what) {
        return new Appointment(uid, day, start, start == null ? null : start.plusHours(1), what, List.of(),
                Repeat.ONCE, null);
    }

    private static CalendarService calendar(Appointment... appointments) {
        CalendarService c = new CalendarService();
        c.load(new CalendarData(List.of(appointments), List.of(), List.of()));
        return c;
    }

    private static final class Context implements WidgetContext {
        final Map<String, String> settings = new HashMap<>();
        final List<String> opened = new java.util.ArrayList<>();

        public WidgetColors colors() {
            return new WidgetColors(Color.DARK_GRAY, Color.WHITE, Color.LIGHT_GRAY, Color.GRAY, Color.BLUE);
        }

        public Locale locale() {
            return Locale.UK;
        }

        public String get(String key, String fallback) {
            return settings.getOrDefault(key, fallback);
        }

        public void set(String key, String value) {
            settings.put(key, value);
        }

        public void every(Duration period, Runnable task) {
            task.run();
        }

        public void onThemeChange(Runnable task) {
        }

        public void open(String... command) {
            opened.add(String.join(" ", command));
        }
    }

    @Test
    void theWidgetIsFoundLikeTheHostFindsIt() {
        assertTrue(ServiceLoader.load(DesktopWidget.class).stream()
                .anyMatch(p -> p.type() == MonthWidget.class));
        assertEquals("calendar-month", new MonthWidget().id());
    }

    @Test
    void theNextAppointmentsLeaveOutWhatIsOverAndCountLongOnesOnce() {
        LocalDate today = NOW.toLocalDate();
        Appointment fair = new Appointment("f", today.minusDays(1), null, null, "Fair", List.of(), Repeat.ONCE, null,
                List.of(), 4, Set.of(), null);
        CalendarService c = calendar(
                at("a", today, LocalTime.of(9, 0), "Breakfast"),
                at("b", today, LocalTime.of(15, 0), "Dentist"),
                fair,
                at("c", today.plusDays(2), LocalTime.of(10, 0), "Train"),
                at("d", today.plusWeeks(6), LocalTime.of(10, 0), "Too far"));
        List<String> next = MonthWidget.next(c, NOW, 8).stream().map(o -> o.appointment().summary()).toList();
        assertEquals(List.of("Fair", "Dentist", "Train"), next);
        assertEquals(1, MonthWidget.next(c, NOW, 1).size());
        assertTrue(MonthWidget.next(c, NOW, 0).isEmpty());
        assertEquals(Set.of(today.minusDays(1), today, today.plusDays(1), today.plusDays(2)),
                MonthWidget.busyDays(c, YearMonth.from(today)));
    }

    @Test
    void linesSayTodayAndTomorrow() {
        Messages t = Messages.load(MonthWidget.class, MonthWidget.BUNDLE, Locale.ENGLISH);
        LocalDate today = NOW.toLocalDate();
        assertEquals("Today 15:00  Dentist", MonthWidget.line(new Occurrence(at("b", today, LocalTime.of(15, 0),
                "Dentist"), today), today, Locale.UK, t));
        assertEquals("Tomorrow  Fair", MonthWidget.line(new Occurrence(at("f", today.plusDays(1), null, "Fair"),
                today.plusDays(1)), today, Locale.UK, t));
        assertTrue(MonthWidget.line(new Occurrence(at("c", today.plusDays(2), null, "Train"), today.plusDays(2)),
                today, Locale.UK, t).startsWith("Thu 5 Nov"));
    }

    @Test
    void theGridStartsOnTheFirstDayOfTheWeekOfTheLanguage() {
        YearMonth nov = YearMonth.of(2026, 11);
        assertEquals(LocalDate.of(2026, 10, 26), MonthWidget.gridStart(nov, Locale.UK), "Monday");
        assertEquals(LocalDate.of(2026, 11, 1), MonthWidget.gridStart(nov, Locale.US), "Sunday");
    }

    @Test
    void theMonthPaintsTodayInTheAccentColour() {
        Context context = new Context();
        context.set("upcoming", "x");
        assertEquals(3, MonthWidget.upcoming(context));
        BufferedImage image = new BufferedImage(260, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        MonthWidget.paintMonth(g, 236, 276, NOW, calendar(at("b", NOW.toLocalDate(), LocalTime.of(15, 0),
                "Dentist")), context);
        g.dispose();
        boolean accent = false;
        for (int y = 40; y < 276 && !accent; y++) {
            for (int x = 0; x < 236; x++) if (image.getRGB(x, y) == Color.BLUE.getRGB()) accent = true;
        }
        assertTrue(accent, "the circle around today");
    }
}
