import client.ClientFonts;
import java.awt.Font;
import java.awt.image.BufferedImage;

/** Optional private-client regression; no game source/assets are distributed with this probe. */
public final class ProbeClientFonts {
    public static void main(String[] args) throws Exception {
        ClientFonts.prepareIfConfigured();
        Class<?> texture = Class.forName("com.wurmonline.client.renderer.gui.text.FontTexture");
        var constructor = texture.getDeclaredConstructor(Font.class, boolean.class);
        var glyph = texture.getDeclaredMethod("getFontImage", char.class);
        constructor.setAccessible(true); glyph.setAccessible(true);
        int count = 0;
        for (String family : new String[]{Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED}) {
            for (int style = 0; style < 4; style++) {
                Object font = constructor.newInstance(new Font(family, style, 18), true);
                int ascent = (Integer)texture.getMethod("getAscent").invoke(font);
                BufferedImage image = (BufferedImage)glyph.invoke(font, 'W');
                int pixels = 0;
                for (int y=0; y<image.getHeight(); y++) for (int x=0; x<image.getWidth(); x++)
                    if ((image.getRGB(x,y) >>> 24) != 0) pixels++;
                if (ascent <= 0 || pixels == 0) throw new AssertionError("Wurm font texture is empty");
                System.out.println("[font-probe] WURM_GLYPH_OK family="+family+" style="+style+" ascent="+ascent+" pixels="+pixels);
                count++;
            }
        }
        System.out.println("[font-probe] WURM_FONT_PROBE_PASS styles="+count+"; real FontTexture metrics/raster only; GL upload/login not attempted");
    }
}
