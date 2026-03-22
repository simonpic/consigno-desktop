module consigno.pdf {
    requires consigno.common;
    requires consigno.crypto;

    requires org.apache.pdfbox;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.slf4j;

    // java.desktop requis pour BufferedImage (rendu PDFBox → PdfRenderService)
    requires java.desktop;

    exports com.consigno.pdf.service;
    exports com.consigno.pdf.model;
}
