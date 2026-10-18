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
  Changing or deleting an occurrence of a repeating appointment asks, as dtcm did, whether it applies
  to *This One* (the occurrence becomes an exception of the series), *Forward* (the series ends
  before it and a new one starts there) or *All*. The days a series leaves out are listed under
  *More*, where *Restore* puts one back. An appointment without a time can span several days: the
  *to* field next to the date names its last day.
- **To Do Editor** (*Edit → To Do…*): due date, description and *Completed*.
- **Lists** (*View → Appointment List…*, *View → To Do List…*): the current view's period as a
  table; the To Do list has a check box per item, a filter for all, pending or completed items, and
  applies the checks with *OK* or *Apply*.
- **Find** (*View → Find…*): a text, searched six months back and ahead by default, *Search all* for
  the whole calendar, *Show Appointment* opens a match in Day view with the editor.
- **Reminders** fire while the Calendar runs: *Beep* sounds, *Flash* draws attention to the window,
  *Popup* shows a message, *Mail* opens the mailer (amade-mail, or whatever handles `mailto:`) with a
  message to the *Mail To* address that holds the appointment, ready to send.
- **Interchange**, as between dtcm, Mailer and File Manager:
  - `amade-calendar file…` schedules the appointments in the files, so an `.ics` attachment or file
    opened from amade-mail or amade-files lands in the Calendar (the desktop entry handles
    `text/calendar`). Files and text dropped anywhere on the window are scheduled too.
  - Two formats are understood: iCalendar, and the dtcm appointment text that starts with
    `** Calendar Appointment **` and lists Date, Start, End and What (in the user's date format, in
    CDE's month/day/year or as an ISO date). Entries whose UID already exists are added as copies.
  - The **Drag Appointment** icon of the Appointment Editor carries the appointment out: as an `.ics` file for
    the File Manager or a mail attachment list, as dtcm text for a text field. **Mail…** opens the
    mailer with the appointment as the message text.
- **Printing**, as in dtcm: *File → Print Current View* (Ctrl+P) prints the view on screen with the
  Printer Settings; *File → Print…* opens the Print dialog with Printer Name, Report Type (Day, Week,
  Month or Year view, Appointment List, To Do List), Copies, the range of dates, Print Command Options
  and *Print to file*. The pages are rendered by the JDK's print service as PostScript and handed to
  `lp` with the printer, the copies and the options (e.g. `-o nobanner`), or kept as the file.
  Day and Week pages show the hours and columns of the views, Month and Year the grids, the lists a
  table; the footer names the Calendar and the date of printing.
- **Options** (*File → Options…*) is one dialog with a *Category* menu, as in dtcm:
  - *Editor Defaults*: duration, start time, reminders and privacy of new appointments.
  - *Display Settings*: the hours shown in Day and Week view, 12- or 24-hour times, the default view.
  - *Date Format*: order of day, month and year, and the separator.
  - *Printer Settings*: printer or file, printer name, print command options, file name, *Units*
    (how many days, weeks, months or years *Print Current View* prints), copies, and whether to print
    only what others may see (appointments marked *Others See Time Only* print as busy, *Others See
    Nothing* are left out).
  - *Language*: English, Deutsch, Français, Español and Italiano. The Calendar follows the amadeOS
    desktop language; this overrides it for the Calendar alone (takes effect at the next start).

## Files

The calendar is one iCalendar file (RFC 5545), readable only by the user:

| File | Content |
|---|---|
| `~/.local/share/amadeos/calendar/calendar.ics` (or `$XDG_DATA_HOME/amadeos/calendar/`) | appointments as `VEVENT`, To Do items as `VTODO`, reminders as `VALARM`, repeats as `RRULE` |
| `~/.config/amadeos/calendar/calendar.conf` (or `$XDG_CONFIG_HOME/amadeos/calendar/`) | the options |

Times are local wall-clock times without a time zone, as in dtcm; times with a `TZID` or in UTC
from other programs are converted to the local time when read. Repeating appointments carry their
exceptions: days left out (`EXDATE`) and occurrences that were moved or changed (`RECURRENCE-ID`),
as a Google Calendar export has them. Appointments without a time may span several days
(`DTEND;VALUE=DATE`). Properties and components the Calendar does not know (locations, attendees,
time zones, alarms at an absolute time, ...) are kept and written back unchanged, so the file can be
exchanged with other calendar programs.

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
  times in the user's format; `ReminderCheck` finds the reminders that became due; `DtcmText` reads
  and writes the dtcm appointment text; `CalendarImporter` schedules iCalendar or dtcm text from files
  and drops. `service.print`: `ReportType`, `PrintRequest`, `ReportPages` (the pages of a report, a
  `Pageable` painted in black on white) and `CalendarPrinter` (PostScript through the JDK, then `lp`
  or a file).
- `ui.main`: `MainWindow`, `MainMenuBar`, `CalendarToolBar` and `MainPresenter`, which navigates,
  edits through the service and saves on the background executor. `ui.views` holds the four views
  and `MiniMonth`; `ui.editor` the two editors; `ui.lists` the list and find dialogs; `ui.options`
  the Options dialog; `ui.print` the Print dialog.

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

1. ~~Reminders and interchange~~: done, see *Interchange*. Open: sending Mail reminders without the
   compose window, once amade-mail can send from the command line.
2. ~~Printing~~: done, see *Printing*. Open: page size and position fields of dtcm's Printer
   Settings (the paper is A4, or Letter in the US).
3. ~~Exceptions of repeating appointments, time zones and multi-day appointments in the file~~: done,
   so a Google Calendar export (*Settings → Import & Export → Export*) imports without loss.
4. **Accounts** (amade-auth): one place on the desktop for third-party accounts (Google with OAuth2,
   others with a password in the keyring), shared by amade-mail and the Calendar.
5. **Other calendars on CalDAV**: the calendars of an account shown read-only next to your own, each
   in its colour, and *Import* of a whole server calendar; dtcm's Show Other Calendar and Compare
   Calendars.
6. **Synchronisation**: a local calendar bound to a server calendar, changes going both ways, so a
   calendar shared with someone else is just a second bound calendar.
7. **The rest of dtcm**: the Group Appointment Editor with mail to the participants, Time Zone in
   the View menu, and the command line tools lookup, insert and delete.

## License

EUPL-1.2, see [LICENSE](LICENSE).
