package com.consigno.pdf.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Génère l'image d'apparence de signature ConsignO.
 *
 * <p>Utilisé à deux endroits :
 * <ul>
 *   <li>Sidebar + ghost PDF viewer — via {@link #render(int, int, LocalDate)}</li>
 *   <li>Embedding dans le PDF signé — via PDFBox content stream (voir PdfBoxSignatureServiceImpl)</li>
 * </ul>
 */
public final class SignatureAppearanceRenderer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color PRIMARY = new Color(0x2D, 0x72, 0xB8);
    private static final Color INK     = new Color(0x1C, 0x2E, 0x3D);
    private static final Color MUTED   = new Color(0x5A, 0x7A, 0x8A);

    private SignatureAppearanceRenderer() {}

    /**
     * Rend l'apparence de signature en image matricielle.
     *
     * @param widthPx  largeur en pixels
     * @param heightPx hauteur en pixels
     * @param date     date à afficher
     * @return image RGB de l'apparence
     */
    public static BufferedImage render(int widthPx, int heightPx, LocalDate date) {
        BufferedImage img = new BufferedImage(widthPx, heightPx, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

            // Fond blanc
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, widthPx, heightPx);

            // Clip avant tout tracé pour garantir que rien ne déborde
            g.clipRect(0, 0, widthPx, heightPx);

            // Bande bleue gauche
            g.setColor(PRIMARY);
            g.fillRect(0, 0, 5, heightPx);

            // Bordure bleue — outer edge à strokeWidth/2 px de chaque bord (pas à 0 = exclu par le clip)
            float strokeWidth = 1.5f;
            float inset = strokeWidth; // outer = inset - sw/2 = 0.75px à l'intérieur, toujours visible
            g.setStroke(new BasicStroke(strokeWidth));
            g.setColor(PRIMARY);
            g.draw(new java.awt.geom.Rectangle2D.Float(
                    inset, inset,
                    widthPx - 2 * inset, heightPx - 2 * inset));

            int textX    = 12;
            int maxTextW = widthPx - textX - 4; // espace disponible après la bande bleue

            // Ligne 1 : "Signé avec ConsignO Desktop" — gras
            // Taille calculée pour tenir dans la largeur disponible
            int fontSize1 = Math.max(8, heightPx / 7);
            Font font1 = new Font("SansSerif", Font.BOLD, fontSize1);
            g.setFont(font1);
            // Réduire si le texte est encore trop long
            while (g.getFontMetrics().stringWidth("Sign\u00e9 avec ConsignO Desktop") > maxTextW
                    && fontSize1 > 7) {
                fontSize1--;
                font1 = new Font("SansSerif", Font.BOLD, fontSize1);
                g.setFont(font1);
            }
            FontMetrics fm1 = g.getFontMetrics();

            // Ligne 2 : date — normal, muted
            int fontSize2 = Math.max(7, fontSize1 - 2);
            Font font2 = new Font("SansSerif", Font.PLAIN, fontSize2);
            g.setFont(font2);
            FontMetrics fm2 = g.getFontMetrics();

            // Centrage vertical du bloc texte
            int totalH  = fm1.getHeight() + 3 + fm2.getHeight();
            int blockTop = (heightPx - totalH) / 2;
            int y1 = blockTop + fm1.getAscent();
            int y2 = y1 + fm1.getDescent() + 3 + fm2.getAscent();

            // Dessin ligne 1
            g.setFont(font1);
            g.setColor(INK);
            g.drawString("Sign\u00e9 avec ConsignO Desktop", textX, y1);

            // Dessin ligne 2
            g.setFont(font2);
            g.setColor(MUTED);
            g.drawString(date.format(DATE_FMT), textX, y2);

        } finally {
            g.dispose();
        }
        return img;
    }
}
