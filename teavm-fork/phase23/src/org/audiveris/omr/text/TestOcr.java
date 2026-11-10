package org.audiveris.omr.text;

import org.audiveris.omr.sheet.Sheet;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * §23 desktop harness only: an OCR implementation that returns lines parsed
 * from a prefed L/W/C text file (system property {@code testocr.file}),
 * using the exact same parser as the Wasm bridge (BrowserOCR.parse, made
 * package-visible by ROUND 16). This lets the desktop run consume the
 * identical OCR input as the Wasm run, so the comparison is pure
 * bridge fidelity (TextBuilder / lyric attachment), not OCR quality.
 * Never shipped: lives only in phase23/classes, ahead of program jars.
 */
public final class TestOcr
        implements OCR
{
    private static final TestOcr INSTANCE = new TestOcr();

    private static final SortedSet<String> LANGUAGES = new TreeSet<>(
            List.of("eng", "deu", "fra", "ita", "spa", "lat"));

    private TestOcr ()
    {
    }

    public static TestOcr getInstance ()
    {
        return INSTANCE;
    }

    @Override
    public SortedSet<String> getSupportedLanguages ()
    {
        return LANGUAGES;
    }

    @Override
    public double getMinConfidence ()
    {
        return 0.65; // as TesseractOCR / BrowserOCR
    }

    @Override
    public String identify ()
    {
        return "TestOcr (prefed tesseract.js text)";
    }

    @Override
    public boolean isAvailable ()
    {
        return true;
    }

    @Override
    public boolean supports (String langSpec)
    {
        final List<String> codes = (langSpec == null) ? List.of() : Language.codesOf(langSpec);
        return !codes.isEmpty() && LANGUAGES.containsAll(codes);
    }

    @Override
    public List<TextLine> recognize (Sheet sheet,
                                     BufferedImage image,
                                     Point topLeft,
                                     String langSpec,
                                     LayoutMode layoutMode,
                                     String label)
    {
        try {
            // §23 trap-4 diagnostic: dump the clean image desktop builds, to
            // compare pixel-for-pixel with the Wasm clean image (phase23/*-clean.gray).
            String saveImage = System.getProperty("testocr.saveimage");
            if ((saveImage != null) && (image != null)) {
                javax.imageio.ImageIO.write(image, "png", new java.io.File(saveImage));
                System.out.println("TestOcr saved clean image " + saveImage + " "
                        + image.getWidth() + "x" + image.getHeight());
            }

            String file = System.getProperty("testocr.file");
            String answer = Files.readString(Paths.get(file));
            List<TextLine> lines = BrowserOCR.parse(sheet, answer);

            if (topLeft != null) {
                for (TextLine line : lines) {
                    line.translate(topLeft.x, topLeft.y);
                }
            }

            // §23 trap-4 follow-up (harness fix): TestOcr used to return the
            // WHOLE page's lines for every call, including the small
            // rehearsal-enclosure buffers scanned by RehearsalsBuilder —
            // lines far outside the buffer then blew up
            // TextBuilder.getSections with an AIOOBE on desktop Hallelujah.
            // Keep only lines intersecting this image, as a real OCR run
            // would return. Whole-sheet calls are unaffected (all lines
            // intersect the full image).
            if (image != null) {
                java.awt.Rectangle imageRect = new java.awt.Rectangle(
                        0, 0, image.getWidth(), image.getHeight());
                lines.removeIf(line -> !line.getBounds().intersects(imageRect));
            }

            System.out.println("TestOcr parsed lines=" + lines.size() + " from " + file);
            return lines;
        } catch (Exception ex) {
            throw new RuntimeException("TestOcr failed", ex);
        }
    }
}
