package eu.amadeos.calendar.domain;

import java.util.Objects;

/**
 * The "Printer Settings" category of the options: where "Print Current View" goes and how.
 *
 * @param destination printer or file
 * @param printerName the printer, empty for the default printer
 * @param options     print command options passed to {@code lp}, e.g. {@code -o nobanner}
 * @param fileName    the PostScript file written when printing to a file
 * @param units       how many periods "Print Current View" prints, e.g. 2 weeks in Week view
 * @param copies      number of copies
 * @param asOthersSee leave out what the appointments' privacy hides from others
 */
public record PrintSettings(Destination destination, String printerName, String options, String fileName, int units,
                            int copies, boolean asOthersSee) {

    public enum Destination { PRINTER, FILE }

    public static final PrintSettings DEFAULTS = new PrintSettings(Destination.PRINTER, "", "", "calendar.ps", 1, 1, false);

    public PrintSettings {
        destination = Objects.requireNonNullElse(destination, Destination.PRINTER);
        printerName = printerName == null ? "" : printerName.strip();
        options = options == null ? "" : options.strip();
        fileName = fileName == null || fileName.isBlank() ? "calendar.ps" : fileName.strip();
        units = Math.clamp(units, 1, 52);
        copies = Math.clamp(copies, 1, 99);
    }
}
