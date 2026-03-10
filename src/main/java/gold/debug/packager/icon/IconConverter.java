package gold.debug.packager.icon;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// png/jpg → ico
public class IconConverter {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static Path ensureIco(Path imagePath, Path workDir) throws IOException {
        String name = imagePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".ico")) return imagePath;

        BufferedImage src = ImageIO.read(imagePath.toFile());
        if (src == null) throw new IOException("无法读取图片（仅支持 png/jpg/jpeg/ico）: " + imagePath);

        Files.createDirectories(workDir);
        Path out = workDir.resolve(stripExt(imagePath.getFileName().toString()) + ".ico");
        writeIcoWithPngImages(src, out);
        return out;
    }

    private static void writeIcoWithPngImages(BufferedImage src, Path out) throws IOException {
        class Entry {
            int w, h;
            byte[] png;
        }

        List<Entry> entries = new ArrayList<>();
        for (int s : SIZES) {
            BufferedImage scaled = scaleTo(src, s, s);
            byte[] png = toPngBytes(scaled);
            Entry e = new Entry();
            e.w = s; e.h = s; e.png = png;
            entries.add(e);
        }

        try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(out)))) {
            // ICONDIR
            writeLEShort(dos, 0);              // reserved
            writeLEShort(dos, 1);              // type=1 icon
            writeLEShort(dos, entries.size()); // count

            int offset = 6 + entries.size() * 16;
            // ICONDIRENTRY list
            for (Entry e : entries) {
                dos.writeByte(e.w == 256 ? 0 : e.w); // 0 means 256
                dos.writeByte(e.h == 256 ? 0 : e.h);
                dos.writeByte(0); // color count
                dos.writeByte(0); // reserved
                writeLEShort(dos, 1);   // planes
                writeLEShort(dos, 32);  // bitcount (png anyway)
                writeLEInt(dos, e.png.length);
                writeLEInt(dos, offset);
                offset += e.png.length;
            }

            // image data (PNG blobs)
            for (Entry e : entries) {
                dos.write(e.png);
            }
        }
    }

    private static BufferedImage scaleTo(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return dst;
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    private static void writeLEShort(DataOutputStream dos, int v) throws IOException {
        dos.writeByte(v & 0xFF);
        dos.writeByte((v >>> 8) & 0xFF);
    }

    private static void writeLEInt(DataOutputStream dos, int v) throws IOException {
        dos.writeByte(v & 0xFF);
        dos.writeByte((v >>> 8) & 0xFF);
        dos.writeByte((v >>> 16) & 0xFF);
        dos.writeByte((v >>> 24) & 0xFF);
    }

    private static String stripExt(String s) {
        int i = s.lastIndexOf('.');
        return i >= 0 ? s.substring(0, i) : s;
    }
}

