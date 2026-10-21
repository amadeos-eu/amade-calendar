package eu.amadeos.apps.calendar.service.print;

import eu.amadeos.apps.calendar.domain.PrintSettings;
import eu.amadeos.commons.print.PrintJobs;

import java.awt.print.Pageable;
import java.awt.print.PrinterException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Prints report pages the way dtcm did: the pages become a PostScript file (written by the JDK's
 * print service), which is kept when printing to a file or handed to {@code lp} with the printer
 * name, the number of copies and the print command options.
 */
public final class CalendarPrinter {
    /** Runs the print command; replaced in tests. */
    public interface Command {
        /** @return what the command printed, e.g. the request id */
        String run(List<String> command) throws IOException;
    }

    /** The outcome: the PostScript file kept, or the output of {@code lp}. */
    public record Outcome(Path file, String output) {
    }

    private final Command command;

    public CalendarPrinter() {
        this(CalendarPrinter::runProcess);
    }

    public CalendarPrinter(Command command) {
        this.command = command;
    }

    // --8<-- [start:print]
    public Outcome print(Pageable pages, PrintSettings settings, Path fileTarget, String jobName)
            throws IOException, PrinterException {
        if (settings.destination() == PrintSettings.Destination.FILE) {
            if (fileTarget.getParent() != null) Files.createDirectories(fileTarget.getParent());
            PrintJobs.writePostScript(pages, fileTarget, jobName);
            return new Outcome(fileTarget, "");
        }
        Path temp = Files.createTempFile("amade-calendar-", ".ps");
        try {
            PrintJobs.writePostScript(pages, temp, jobName);
            List<String> lp = new ArrayList<>(List.of("lp", "-t", jobName));
            if (!settings.printerName().isEmpty()) lp.addAll(List.of("-d", settings.printerName()));
            if (settings.copies() > 1) lp.addAll(List.of("-n", Integer.toString(settings.copies())));
            if (!settings.options().isEmpty()) lp.addAll(List.of(settings.options().trim().split("\\s+")));
            lp.add(temp.toAbsolutePath().toString());
            return new Outcome(null, command.run(lp));
        } finally {
            Files.deleteIfExists(temp);
        }
    }
    // --8<-- [end:print]

    static String runProcess(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (var in = process.getInputStream()) {
            output = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        }
        int exit;
        try {
            exit = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while printing", e);
        }
        if (exit != 0) throw new IOException(output.isEmpty() ? command.getFirst() + " failed (" + exit + ")" : output);
        return output;
    }
}
