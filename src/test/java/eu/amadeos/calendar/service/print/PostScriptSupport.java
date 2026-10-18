package eu.amadeos.calendar.service.print;

import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.Destination;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.nio.file.Files;
import java.nio.file.Path;

/** Whether this JDK can write PostScript to a file here (it needs a print service, e.g. CUPS). */
public final class PostScriptSupport {
    private PostScriptSupport() {
    }

    public static boolean available() {
        try {
            Path temp = Files.createTempFile("ps-check", ".ps");
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintable((g, pf, i) -> i == 0 ? Printable.PAGE_EXISTS : Printable.NO_SUCH_PAGE);
            PrintRequestAttributeSet attrs = new HashPrintRequestAttributeSet();
            attrs.add(new Destination(temp.toUri()));
            job.print(attrs);
            boolean ok = Files.size(temp) > 0;
            Files.deleteIfExists(temp);
            return ok;
        } catch (Exception e) {
            return false;
        }
    }
}
