package teavm;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.audiveris.omr.score.Score;
import org.audiveris.omr.sheet.Book;
import org.audiveris.omr.sheet.SheetStub;
import org.audiveris.omr.step.OmrStep;
import org.audiveris.omr.ui.symbol.MusicFont;
import org.audiveris.omr.util.OmrExecutors;

/**
 * §23 desktop verification — run to PAGE on a PNG regenerated from the
 * .gray bytes (same input as Wasm), with OCR fed from a prefed L/W/C text
 * file via TestOcr (system property testocr.file), export via MxlWriter.
 * Usage: DesktopOcrExport <png> <out.musicxml>
 * Prints lyric counts and the syllable sequence for comparison with Wasm.
 */
public class DesktopOcrExport
{
    public static void main (String[] args) throws Exception
    {
        String png = args[0];
        String out = args[1];
        BufferedImage img = ImageIO.read(new java.io.File(png));
        java.lang.reflect.Field cliField = org.audiveris.omr.Main.class.getDeclaredField("cli");
        cliField.setAccessible(true);
        cliField.set(null, new org.audiveris.omr.CLI(org.audiveris.omr.WellKnowns.TOOL_NAME));
        OmrExecutors.restart();
        MusicFont.checkMusicFont();
        Book book = new Book(Paths.get(png));
        book.addStub(new SheetStub(book, 1));
        SheetStub stub = book.getStub(1);
        stub.reachStep(OmrStep.PAGE, false);
        for (Score score : book.getScores()) {
            String xml = MxlWriter.build(score);
            Files.writeString(Paths.get(out), xml);
            System.out.println("DESKTOP-OCR-EXPORTED " + out + " len=" + xml.length());
            Matcher m = Pattern.compile("<lyric[ >]").matcher(xml);
            int n = 0;
            while (m.find()) n++;
            System.out.println("LYRIC elements=" + n);
            Matcher lm = Pattern.compile("<lyric(?:\\s[^>]*)?>(.*?)</lyric>", Pattern.DOTALL).matcher(xml);
            StringBuilder sb = new StringBuilder();
            while (lm.find()) {
                Matcher t = Pattern.compile("<text>([^<]*)</text>").matcher(lm.group(1));
                if (t.find()) {
                    if (sb.length() > 0) sb.append(" | ");
                    sb.append(t.group(1));
                }
            }
            System.out.println("LYRIC-TEXTS: " + sb);
        }
    }
}
