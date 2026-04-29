package com.app.modules.templates.service;

import com.app.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateRenderService {

    private static final Pattern TOKEN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    public byte[] renderHtml(String html, Map<String, String> values) {
        if (html == null) html = "";
        String out = replaceTokens(html, values);
        return out.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] renderDocx(byte[] docxBytes, Map<String, String> values) {
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
            // NOTE: This is intentionally a first robust step: docx placeholders can be split across runs.
            // We use docx4j VariableReplace which works for ${var}. We'll pre-transform {{var}} -> ${var}.
            Map<String, String> vars = values.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                    e -> e.getKey(),
                    Map.Entry::getValue
            ));
            // Transform tokens inside document to ${key} for variableReplace.
            // For now we also accept users authoring ${key} directly.
            // Some DOCX fixtures (or external generators) may miss styles.xml; VariablePrepare assumes it exists.
            if (pkg.getMainDocumentPart().getStyleDefinitionsPart() == null) {
                StyleDefinitionsPart sdp = new StyleDefinitionsPart();
                sdp.unmarshalDefaultStyles();
                pkg.getMainDocumentPart().addTargetPart(sdp);
            }

            org.docx4j.model.datastorage.migration.VariablePrepare.prepare(pkg);
            pkg.getMainDocumentPart().variableReplace(vars);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            pkg.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("DOCX render failed ({} bytes, {} vars).", docxBytes != null ? docxBytes.length : 0, values != null ? values.size() : 0, e);
            throw new BusinessException("TEMPLATE_DOCX_RENDER_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Impossible de rendre le modèle DOCX.");
        }
    }

    public byte[] convertDocxToPdf(byte[] docxBytes) {
        try {
            WordprocessingMLPackage pkg = WordprocessingMLPackage.load(new ByteArrayInputStream(docxBytes));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Docx4J.toPDF(pkg, out);
            return out.toByteArray();
        } catch (Docx4JException e) {
            throw new BusinessException("TEMPLATE_PDF_RENDER_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Impossible de générer le PDF à partir du DOCX.");
        } catch (Exception e) {
            throw new BusinessException("TEMPLATE_PDF_RENDER_ERROR", org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Impossible de générer le PDF à partir du DOCX.");
        }
    }

    private static String replaceTokens(String input, Map<String, String> values) {
        if (values == null || values.isEmpty()) return input;
        Matcher m = TOKEN.matcher(input);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String value = values.getOrDefault(key, "");
            m.appendReplacement(sb, Matcher.quoteReplacement(value == null ? "" : value));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

