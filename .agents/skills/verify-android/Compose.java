import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Draws verify-android's PR-ready images with the JDK alone, which every Android build already needs.
 * Run as {@code java Compose.java <jobs.tsv>}. Each job file line is tab separated:
 *
 * <pre>
 * grid   out.png  height        starts an image of captioned frames
 * row    label                  starts a row (label may be empty)
 * cell   caption  path          one frame; an empty or unreadable path draws a placeholder
 * poster out.png  src.png height  the frame scaled, with a play badge
 * </pre>
 *
 * Prints {@code ok<TAB>out} or {@code fail<TAB>out<TAB>reason} per image and exits 0.
 */
public class Compose {
    static final int GAP = 16, CAPTION = 34, LABEL = 36;
    static final Color BG = Color.WHITE, FG = new Color(0x1f2328), MUTED = new Color(0xd1d9e0);

    record Cell(String caption, String path) {}
    record Row(String label, List<Cell> cells) {}

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        String out = null;
        int height = 720;
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(new File(args[0]).toPath())) {
            String[] f = line.split("\t", -1);
            switch (f[0]) {
                case "grid" -> {
                    finish(out, height, rows);
                    out = f[1];
                    height = Integer.parseInt(f[2]);
                    rows = new ArrayList<>();
                }
                case "row" -> rows.add(new Row(f.length > 1 ? f[1] : "", new ArrayList<>()));
                case "cell" -> {
                    if (rows.isEmpty()) rows.add(new Row("", new ArrayList<>()));
                    rows.get(rows.size() - 1).cells().add(new Cell(f[1], f.length > 2 ? f[2] : ""));
                }
                case "poster" -> {
                    finish(out, height, rows);
                    out = null;
                    rows = new ArrayList<>();
                    report(f[1], () -> poster(f[1], f[2], Integer.parseInt(f[3])));
                }
                default -> { }
            }
        }
        finish(out, height, rows);
    }

    interface Job { void run() throws Exception; }

    static void report(String out, Job job) {
        try {
            job.run();
            System.out.println("ok\t" + out);
        } catch (Exception e) {
            System.out.println("fail\t" + out + "\t" + String.valueOf(e.getMessage()).replace('\n', ' '));
        }
    }

    static void finish(String out, int height, List<Row> rows) {
        if (out != null) report(out, () -> grid(out, height, rows));
    }

    static BufferedImage read(String path) {
        try {
            return path.isEmpty() ? null : ImageIO.read(new File(path));
        } catch (Exception e) {
            return null;
        }
    }

    static int scaledWidth(BufferedImage img, int height) {
        // A missing frame keeps a phone's proportions so the rows still line up.
        return img == null ? height * 9 / 20 : Math.max(1, img.getWidth() * height / img.getHeight());
    }

    static Graphics2D pen(BufferedImage canvas) {
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return g;
    }

    static void grid(String out, int height, List<Row> rows) throws Exception {
        if (rows.stream().allMatch(r -> r.cells().isEmpty())) throw new IllegalArgumentException("no frames");
        List<List<BufferedImage>> images = new ArrayList<>();
        int width = 0, total = GAP;
        for (Row row : rows) {
            List<BufferedImage> imgs = new ArrayList<>();
            int w = GAP;
            for (Cell c : row.cells()) {
                BufferedImage img = read(c.path());
                imgs.add(img);
                w += scaledWidth(img, height) + GAP;
            }
            images.add(imgs);
            width = Math.max(Math.max(width, w), labelWidth(row.label()) + 2 * GAP);
            total += (row.label().isEmpty() ? 0 : LABEL) + height + CAPTION + GAP;
        }
        BufferedImage canvas = new BufferedImage(width, total, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pen(canvas);
        g.setColor(BG);
        g.fillRect(0, 0, width, total);
        int y = GAP;
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            if (!row.label().isEmpty()) {
                g.setColor(FG);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
                g.drawString(row.label(), GAP, y + 26);
                y += LABEL;
            }
            int x = GAP;
            for (int i = 0; i < row.cells().size(); i++) {
                BufferedImage img = images.get(r).get(i);
                int w = scaledWidth(img, height);
                if (img != null) {
                    g.drawImage(img, x, y, w, height, null);
                    g.setColor(MUTED);
                    g.setStroke(new BasicStroke(1));
                    g.drawRect(x, y, w - 1, height - 1);
                } else {
                    g.setColor(new Color(0xf6f8fa));
                    g.fillRect(x, y, w, height);
                    g.setColor(MUTED);
                    g.drawRect(x, y, w - 1, height - 1);
                    centered(g, "not taken", x, w, y + height / 2, new Font(Font.SANS_SERIF, Font.PLAIN, 20));
                }
                g.setColor(FG);
                centered(g, row.cells().get(i).caption(), x, w, y + height + 24, new Font(Font.SANS_SERIF, Font.PLAIN, 20));
                x += w + GAP;
            }
            y += height + CAPTION + GAP;
        }
        g.dispose();
        write(canvas, out);
    }

    static int labelWidth(String label) {
        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        int w = g.getFontMetrics(new Font(Font.SANS_SERIF, Font.BOLD, 24)).stringWidth(label);
        g.dispose();
        return w;
    }

    static void centered(Graphics2D g, String text, int x, int width, int baseline, Font font) {
        g.setFont(font);
        FontMetrics m = g.getFontMetrics();
        while (text.length() > 1 && m.stringWidth(text) > width) text = text.substring(0, text.length() - 2) + "…";
        g.drawString(text, x + (width - m.stringWidth(text)) / 2, baseline);
    }

    static void poster(String out, String src, int height) throws Exception {
        BufferedImage img = read(src);
        if (img == null) throw new IllegalArgumentException("cannot read " + new File(src).getName());
        int w = scaledWidth(img, height);
        BufferedImage canvas = new BufferedImage(w, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = pen(canvas);
        g.drawImage(img, 0, 0, w, height, null);
        int d = Math.min(w, height) / 4, cx = w / 2, cy = height / 2;
        g.setColor(new Color(0, 0, 0, 150));
        g.fillOval(cx - d / 2, cy - d / 2, d, d);
        g.setColor(Color.WHITE);
        int t = d / 4;
        g.fillPolygon(new Polygon(new int[] {cx - t * 2 / 3, cx - t * 2 / 3, cx + t}, new int[] {cy - t, cy + t, cy}, 3));
        g.dispose();
        write(canvas, out);
    }

    static void write(BufferedImage canvas, String out) throws Exception {
        if (!ImageIO.write(canvas, "png", new File(out))) throw new IllegalStateException("no PNG writer");
    }
}
