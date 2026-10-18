package org.datazuul.amadeos.calendar.i18n;

import org.datazuul.amadeos.calendar.domain.CalendarView;
import org.datazuul.amadeos.calendar.domain.DateOrder;
import org.datazuul.amadeos.calendar.domain.Privacy;
import org.datazuul.amadeos.calendar.domain.ReminderType;
import org.datazuul.amadeos.calendar.domain.RepeatPreset;
import org.datazuul.amadeos.commons.i18n.Messages;
import org.datazuul.amadeos.commons.i18n.TranslationCheck;
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
        return Messages.load(Texts.class, Texts.BUNDLE, locale);
    }

    @Test
    void everyLanguageHasEveryText() {
        assertEquals(Map.of(), TranslationCheck.problems(Texts.class, Texts.BUNDLE));
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
        for (org.datazuul.amadeos.calendar.service.print.ReportType t : org.datazuul.amadeos.calendar.service.print.ReportType.values()) {
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
