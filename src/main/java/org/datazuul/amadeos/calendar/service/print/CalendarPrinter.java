package org.datazuul.amadeos.calendar.service.print;

import org.datazuul.amadeos.calendar.domain.PrintSettings;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import javax.print.attribute.standard.JobName;
import java.awt.print.Pageable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
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

    public Outcome print(Pageable pages, PrintSettings settings, Path fileTarget, String jobName)
            throws IOException, PrinterException {
        if (settings.destination() == PrintSettings.Destination.FILE) {
            if (fileTarget.getParent() != null) Files.createDirectories(fileTarget.getParent());
            render(pages, fileTarget, jobName);
            return new Outcome(fileTarget, "");
        }
        Path temp = Files.createTempFile("amade-calendar-", ".ps");
        try {
            render(pages, temp, jobName);
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

    /** Writes the pages as PostScript to the file. */
    public static void render(Pageable pages, Path file, String jobName) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName(jobName);
        job.setPageable(pages);
        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Destination(file.toUri()));
        attributes.add(new JobName(jobName, null));
        job.print(attributes);
    }

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

    /** The printers the system knows, for the Printer Name fields. */
    public static List<String> printerNames() {
        List<String> names = new ArrayList<>();
        try {
            for (javax.print.PrintService s : javax.print.PrintServiceLookup.lookupPrintServices(null, null)) {
                names.add(s.getName());
            }
        } catch (RuntimeException e) {
            // no print system: an empty list
        }
        return names;
    }
}
