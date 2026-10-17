# amade-calendar

**amade-calendar** is the Calendar of the [amadeOS](https://github.com/datazuul/amadeos) desktop: a
Java Swing calendar modelled on the CDE Calendar (`dtcm`), with the four views, the Appointment
Editor and the To Do Editor of the original, stored as a plain iCalendar file.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)
![Debian](https://img.shields.io/badge/Debian-package-red)

> Status: early development (0.1.0). Appointments, To Do items, reminders, repeats, lists, find and
> options work; see *Roadmap* for printing, mail and other calendars.

---

## Window layout

```
┌ Calendar - October 2026 ────────────────────────────────────────────────────┐
│ File  Edit  View                                                       Help │
├─────────────────────────────────────────────────────────────────────────────┤
│ [Appointment] [To Do]  [◀] [Today] [▶]  [Day] [Week] [Month] [Year]  October 2026 │
├─────────────────────────────────────────────────────────────────────────────┤
│  Sunday   Monday    Tuesday   Wednesday  Thursday  Friday   Saturday        │
│                                              1         2        3           │
│   4        5         6         7             8         9        10          │
│  11       12        13        14            15        16       ▌17▐         │
│                     1:00 PM Lunch                               2:30 PM Staff│
│  18       19        20        21            22        23        24          │
│           10:00 AM  9:00 AM   10:00 AM                10:00 AM  6:00 PM     │
├─────────────────────────────────────────────────────────────────────────────┤
│ Calendar loaded: 6 appointments, 2 To Do items                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

- **Day view**: the day's appointments hour by hour (the hours between the *Begin* and *End* of the
  Display Settings; earlier and later appointments in a row of their own, appointments without a time
  at the top) and the three-month mini calendar. A day in the mini calendar selects that day, a month
  name switches to Month view. Days with appointments are bold.
- **Week view**: a column per day with the day's appointments, the selected day highlighted, and the
  week grid below with the taken hours shaded. A day name switches to Day view.
- **Month view**: the month with the appointments in the day cells, the selected day highlighted. A
  click selects the day, a click on the day number opens Day view.
- **Year view**: the twelve months without appointments. A month name opens Month view, a day Day view.
- In every view a double-click on a day (or on an hour) opens the Appointment Editor for it, a
  double-click on an appointment opens it. *Previous*, *Today* and *Next* step by the view's period;
  *View → Go To Date…* jumps anywhere.
- **Appointment Editor** (*Edit → Appointment…*, the tool bar or a double-click): the day's
  appointments in a list; Date, Start, End (empty: an appointment without a time, or without an end)
  and What (the first line is shown in the views). *More* reveals the options of dtcm: reminders
  *Beep*, *Flash*, *Popup* and *Mail* with their lead times, *Occurs* (One Time, Daily, Weekly, Every
  Two Weeks, Monthly By Date, Monthly By Weekday, Yearly, Monday Thru Friday, Mon/Wed/Fri,
  Tue/Thu, Repeat Every *n* days, weeks or months) with *For* (number of times, empty for ever) and
  *Privacy* (Others See Time and Text, Time Only, Nothing). *Insert*, *Change*, *Delete*, *Clear*.
- **To Do Editor** (*Edit → To Do…*): due date, description and *Completed*.
- **Lists** (*View → Appointment List…*, *View → To Do List…*): the current view's period as a
  table; the To Do list has a check box per item, a filter for all, pending or completed items, and
  applies the checks with *OK* or *Apply*.
- **Find** (*View → Find…*): a text, searched six months back and ahead by default, *Search all* for
  the whole calendar, *Show Appointment* opens a match in Day view with the editor.
- **Reminders** fire while the Calendar runs: *Beep* sounds, *Flash* draws attention to the window,
  *Popup* shows a message. *Mail* is stored in the file but not sent yet (see *Roadmap*).
- **Options** (*File → Options…*) is one dialog with a *Category* menu, as in dtcm:
  - *Editor Defaults*: duration, start time, reminders and privacy of new appointments.
  - *Display Settings*: the hours shown in Day and Week view, 12- or 24-hour times, the default view.
  - *Date Format*: order of day, month and year, and the separator.
  - *Language*: English, Deutsch, Français, Español and Italiano. The Calendar follows the amadeOS
    desktop language; this overrides it for the Calendar alone (takes effect at the next start).

## Files

The calendar is one iCalendar file (RFC 5545), readable only by the user:

| File | Content |
|---|---|
| `~/.local/share/amadeos/calendar/calendar.ics` (or `$XDG_DATA_HOME/amadeos/calendar/`) | appointments as `VEVENT`, To Do items as `VTODO`, reminders as `VALARM`, repeats as `RRULE` |
| `~/.config/amadeos/calendar/calendar.conf` (or `$XDG_CONFIG_HOME/amadeos/calendar/`) | the options |

Times are local wall-clock times without a time zone, as in dtcm. Properties and components the
Calendar does not know (locations, attendees, time zones, alarms at an absolute time, ...) are kept
and written back unchanged, so the file can be exchanged with other calendar programs. A changed
repeating appointment changes all its occurrences, as dtcm did; exceptions (`EXDATE`,
`RECURRENCE-ID`) are not yet understood.

## Architecture

The Java 25 Swing application uses Guice for dependency injection. Theme, Motif look, language and
start-up come from [amade-commons](https://github.com/datazuul/amade-commons): the Calendar follows
the desktop theme chosen in the Style Manager immediately, including the dark *Dark Slate*.

- `domain`: `Appointment`, `ToDoItem`, `Reminder`, `Repeat` (the `RRULE` subset the *Occurs* menu
  expresses, with the occurrence expansion) and `RepeatPreset` (the menu entries), `Privacy`,
  `CalendarPreferences`, `CalendarView` (the periods of the views).
- `repository`: `IcsCodec` reads and writes iCalendar; `IcsCalendarRepository` the file, atomically;
  `PreferencesRepository` the options.
- `service`: `CalendarService` holds the calendar in memory and answers the queries of the views
  (occurrences per day and range, find, To Do items); `DateFormats` formats and parses dates and
  times in the user's format; `ReminderCheck` finds the reminders that became due.
- `ui.main`: `MainWindow`, `MainMenuBar`, `CalendarToolBar` and `MainPresenter`, which navigates,
  edits through the service and saves on the background executor. `ui.views` holds the four views
  and `MiniMonth`; `ui.editor` the two editors; `ui.lists` the list and find dialogs; `ui.options`
  the Options dialog.

Texts live in `src/main/resources/org/datazuul/amadeos/calendar/messages*.properties` (UTF-8); a
test checks that every translation is complete and the mnemonics are unique.

## Build and install

```sh
./gradlew test               # unit tests (the Swing tests skip themselves without a display)
./gradlew run                # start the Calendar
./gradlew deb                # build/distributions/amade-calendar_0.1.0_all.deb
./gradlew installToAmadeOs   # ... and drop it into ../amadeos/config/packages.chroot/
```

Gradle takes amade-commons from JitPack, or builds a checkout in `../amade-commons` instead. The
package installs `/usr/bin/amade-calendar`, the jar under `/opt/amade-calendar/` and a desktop entry
(`Office;Calendar`) the front panel drawers pick up; the panel's calendar control starts it.

## Roadmap

In the order of the CDE User's Guide chapter *Using Calendar*:

1. **Reminders and interchange**: *Mail* reminders through amade-mail; the `** Calendar Appointment **`
   text format of dtcm and iCalendar as the drag-and-drop and file format between amade-mail,
   amade-files and the Calendar; `amade-calendar file.ics` to import a file.
2. **Printing**: *Print Current View* and the Print dialog (report type, copies, range, print to
   file) through amade-print, with a *Printer Settings* category in the options.
3. **Other calendars**: the Browse menu of dtcm (*Show Other Calendar*, *Compare Calendars* with the
   overlaid busy grid, the *Group Appointment Editor*, the *Menu Editor* for the browse list) on
   CalDAV, with free/busy and access as the server offers it. *Time Zone* in the View menu.
4. **Command line**: `lookup`, `insert` and `delete` as the dtcm tools had them, and a standalone
   editor that writes one appointment to a file.

## License

EUPL-1.2, see [LICENSE](LICENSE).
