package eu.amadeos.apps.calendar.i18n;

import eu.amadeos.apps.calendar.bootstrap.AmadeCalendarApplication;
import eu.amadeos.apps.calendar.domain.CalendarView;
import eu.amadeos.apps.calendar.domain.DateOrder;
import eu.amadeos.apps.calendar.domain.Privacy;
import eu.amadeos.apps.calendar.domain.ReminderType;
import eu.amadeos.apps.calendar.domain.RepeatPreset;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.i18n.Texts;
import eu.amadeos.commons.i18n.TranslationCheck;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TranslationTest {
    private static final List<Locale> LOCALES = List.of(Locale.ENGLISH, Locale.GERMANY, Locale.FRANCE,
            Locale.of("es", "ES"), Locale.ITALY);

    private static Messages in(Locale locale) {
        return Messages.load(AmadeCalendarApplication.class, Texts.bundle(), locale);
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(AmadeCalendarApplication.class, Texts.bundle()));
    }

    @Test
    void keysBuiltFromIdsExist() {
        Messages english = in(Locale.ENGLISH);
        List<String> keys = new ArrayList<>(List.of("options.page.Editor Defaults", "options.page.Display Settings",
                "options.page.Date Format", "options.page.Printer Settings", "options.page.Language", "list.todos.filter.all", "list.todos.filter.pending",
                "list.todos.filter.completed", "unit.days", "unit.weeks", "unit.months"));
        for (CalendarView v : CalendarView.values()) keys.add("view." + v.name().toLowerCase(Locale.ROOT));
        for (RepeatPreset p : RepeatPreset.values()) keys.add("repeat." + p.name().toLowerCase(Locale.ROOT));
        for (ReminderType t : ReminderType.values()) keys.add("reminder." + t.name().toLowerCase(Locale.ROOT));
        for (Privacy p : Privacy.values()) keys.add("privacy." + p.name().toLowerCase(Locale.ROOT));
        for (DateOrder o : DateOrder.values()) keys.add("options.date.order." + o.name().toLowerCase(Locale.ROOT));
        for (eu.amadeos.apps.calendar.service.print.ReportType t : eu.amadeos.apps.calendar.service.print.ReportType.values()) {
            keys.add("print.report." + t.name().toLowerCase(Locale.ROOT));
        }
        for (String key : keys) assertTrue(english.has(key), key);
    }

    @Test
    void countsFollowTheLanguage() {
        assertEquals("Calendar loaded: 2 appointments, 1 To Do item", in(Locale.ENGLISH).format("status.loaded", 2, 1));
        assertEquals("Kalender geladen: keine Termine, 3 Aufgaben", in(Locale.GERMANY).format("status.loaded", 0, 3));
        assertEquals("Aucun rendez-vous trouvé", in(Locale.FRANCE).format("find.matches", 0));
        assertEquals("1 appointment found", in(Locale.ENGLISH).format("find.matches", 1));
    }

    @Test
    void menuMnemonicsAreUniquePerLanguage() {
        List<String> menus = List.of("menu.file", "menu.edit", "menu.view", "menu.help");
        for (Locale locale : LOCALES) {
            Set<Character> letters = new HashSet<>();
            for (String key : menus) {
                String text = in(locale).get(key);
                int amp = text.indexOf('&');
                assertTrue(amp >= 0, locale + " " + key);
                assertTrue(letters.add(Character.toLowerCase(text.charAt(amp + 1))), locale + " " + key + " " + text);
            }
        }
    }

    @Test
    void printMnemonicsAreUniquePerLanguage() {
        List<String> labels = List.of("print.printer", "print.report", "print.copies", "print.from", "print.options", "print.file");
        for (Locale locale : LOCALES) {
            Set<Character> letters = new HashSet<>();
            for (String key : labels) {
                String text = in(locale).get(key);
                int amp = text.indexOf('&');
                assertTrue(amp >= 0, locale + " " + key);
                assertTrue(letters.add(Character.toLowerCase(text.charAt(amp + 1))), locale + " " + key + " " + text);
            }
        }
    }

    @Test
    void editorMnemonicsAreUniquePerLanguage() {
        List<String> labels = List.of("editor.date", "editor.end-date", "editor.start", "editor.end", "editor.what",
                "editor.occurs", "editor.every", "editor.mail-to", "editor.privacy");
        for (Locale locale : LOCALES) {
            Set<Character> letters = new HashSet<>();
            for (String key : labels) {
                String text = in(locale).get(key);
                int amp = text.indexOf('&');
                assertTrue(amp >= 0, locale + " " + key);
                assertTrue(letters.add(Character.toLowerCase(text.charAt(amp + 1))), locale + " " + key + " " + text);
            }
        }
    }
}
