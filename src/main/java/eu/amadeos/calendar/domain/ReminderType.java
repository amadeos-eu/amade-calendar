package eu.amadeos.calendar.domain;

/**
 * The four CDE reminder kinds. Each becomes a {@code VALARM}: Beep is {@code ACTION:AUDIO}, Popup and
 * Flash are {@code ACTION:DISPLAY} (Flash marked with {@code X-AMADE-FLASH:TRUE}), Mail is {@code ACTION:EMAIL}.
 */
public enum ReminderType {
    BEEP, FLASH, POPUP, MAIL
}
