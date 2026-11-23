import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates the logo files from the same geometry as packaging/icons/xmousebutton.svg.
 *
 *   java packaging/make-icons.java
 *
 * Writes packaging/icons/xmousebutton.{png,ico,icns} and the runtime copy used by the tray.
 */
class MakeIcons {

    static final Color BLUE_TOP = new Color(0x3B93F0);
    static final Color BLUE_BOTTOM = new Color(0x1550A8);
    static final Color ORANGE = new Color(0xFFB020);
    static final Color PALE = new Color(0xDCE8FA);

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".");
        Path icons = root.resolve("packaging/icons");
        Files.createDirectories(icons);

        BufferedImage master = draw(1024);
        ImageIO.write(scale(master, 512), "png", icons.resolve("xmousebutton.png").toFile());
        ImageIO.write(scale(master, 64), "png",
                root.resolve("src/main/resources/icons/xmousebutton-64.png").toFile());
        Files.write(icons.resolve("xmousebutton.ico"), ico(master, new int[]{16, 32, 48, 256}));
        Files.write(icons.resolve("xmousebutton.icns"), icns(master));
        System.out.println("wrote icons to " + icons);
    }

    static BufferedImage draw(int size) {
        double s = size / 1024.0;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s, s);

        g.setPaint(new GradientPaint(0, 0, BLUE_TOP, 0, 1024, BLUE_BOTTOM));
        g.fill(new RoundRectangle2D.Double(0, 0, 1024, 1024, 230, 230));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(312, 170, 400, 680, 200, 200));
        g.setColor(ORANGE);
        g.fill(new RoundRectangle2D.Double(336, 194, 168, 300, 80, 80));
        g.setColor(PALE);
        g.fill(new RoundRectangle2D.Double(520, 194, 168, 300, 80, 80));
        g.setColor(BLUE_BOTTOM);
        g.fill(new RoundRectangle2D.Double(486, 250, 52, 120, 26, 26));

        g.setStroke(new BasicStroke(30f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new java.awt.geom.Line2D.Double(372, 600, 640, 600));
        Path2D head = new Path2D.Double();
        head.moveTo(612, 562);
        head.lineTo(676, 600);
        head.lineTo(612, 638);
        head.closePath();
        g.fill(head);
        g.dispose();
        return img;
    }

    static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** ICO with PNG-compressed entries (supported by Windows Vista and later). */
    static byte[] ico(BufferedImage master, int[] sizes) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        byte[][] images = new byte[sizes.length][];
        for (int i = 0; i < sizes.length; i++) {
            images[i] = png(scale(master, sizes[i]));
        }
        writeLE16(out, 0);
        writeLE16(out, 1);
        writeLE16(out, sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int dim = sizes[i] >= 256 ? 0 : sizes[i];
            out.writeByte(dim);
            out.writeByte(dim);
            out.writeByte(0);
            out.writeByte(0);
            writeLE16(out, 1);
            writeLE16(out, 32);
            writeLE32(out, images[i].length);
            writeLE32(out, offset);
            offset += images[i].length;
        }
        for (byte[] data : images) {
            out.write(data);
        }
        out.flush();
        return bytes.toByteArray();
    }

    /** ICNS with PNG chunks at the standard sizes macOS reads. */
    static byte[] icns(BufferedImage master) throws IOException {
        String[][] chunks = {{"ic07", "128"}, {"ic08", "256"}, {"ic09", "512"}, {"ic10", "1024"}};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(body);
        for (String[] chunk : chunks) {
            byte[] data = png(scale(master, Integer.parseInt(chunk[1])));
            out.write(chunk[0].getBytes("US-ASCII"));
            out.writeInt(data.length + 8);
            out.write(data);
        }
        out.flush();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        DataOutputStream fo = new DataOutputStream(file);
        fo.write("icns".getBytes("US-ASCII"));
        fo.writeInt(body.size() + 8);
        fo.write(body.toByteArray());
        fo.flush();
        return file.toByteArray();
    }

    static void writeLE16(DataOutputStream out, int v) throws IOException {
        out.writeByte(v & 0xFF);
        out.writeByte((v >>> 8) & 0xFF);
    }

    static void writeLE32(DataOutputStream out, int v) throws IOException {
        writeLE16(out, v & 0xFFFF);
        writeLE16(out, (v >>> 16) & 0xFFFF);
    }
}
