package com.app.modules.templates.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.ObjectFactory;
import org.docx4j.wml.P;
import org.docx4j.wml.R;
import org.docx4j.wml.Text;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRenderServiceTest {

    private final TemplateRenderService service = new TemplateRenderService();

    @Test
    void renderHtml_replacesTokens() {
        byte[] out = service.renderHtml("<p>Hello {{ mission.titre }}</p>", Map.of("mission.titre", "Paris"));
        assertEquals("<p>Hello Paris</p>", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void renderDocx_replacesVariables() throws Exception {
        byte[] docx = makeDocxWithText("Ordre: ${mission.titre}");
        byte[] rendered = service.renderDocx(docx, Map.of("mission.titre", "Mission A"));

        WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new java.io.ByteArrayInputStream(rendered));
        String xml = pkg.getMainDocumentPart().getXML();
        assertTrue(xml.contains("Mission A"));
        assertFalse(xml.contains("${mission.titre}"));
    }

    @Test
    void convertDocxToPdf_producesPdfBytes() throws Exception {
        byte[] docx = makeDocxWithText("Hello PDF");
        byte[] pdf = service.convertDocxToPdf(docx);
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        String header = new String(pdf, 0, Math.min(pdf.length, 4), StandardCharsets.US_ASCII);
        assertEquals("%PDF", header);
    }

    private static byte[] makeDocxWithText(String text) throws Exception {
        WordprocessingMLPackage pkg = WordprocessingMLPackage.createPackage();
        ObjectFactory f = new ObjectFactory();
        P p = f.createP();
        R r = f.createR();
        Text t = f.createText();
        t.setValue(text);
        r.getContent().add(t);
        p.getContent().add(r);
        pkg.getMainDocumentPart().addObject(p);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pkg.save(out);
        return out.toByteArray();
    }
}

