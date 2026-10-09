package com.sarrai.app;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.google.android.gms.tasks.Tasks;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class FileTextExtractor {
    private static final int MAX_CHARS = 60000;
    private static final int MAX_XML_BYTES = 4 * 1024 * 1024;

    private FileTextExtractor() {}

    public static String extract(Context context, Uri uri) throws Exception {
        String name = getName(context, uri);
        String lower = name.toLowerCase(Locale.ROOT);
        String mime = context.getContentResolver().getType(uri);
        String result;

        if (lower.endsWith(".pdf") || "application/pdf".equals(mime)) {
            result = extractPdf(context, uri);
        } else if (lower.endsWith(".xlsx")) {
            result = extractXlsx(context, uri);
        } else if (lower.endsWith(".docx") || lower.endsWith(".pptx")) {
            result = extractOfficeXml(context, uri, lower.endsWith(".docx"));
        } else if (lower.endsWith(".txt") || lower.endsWith(".csv")
                || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".xml") || lower.endsWith(".log")) {
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                result = readLimited(in);
            }
        } else if ((mime != null && mime.startsWith("image/"))
                || lower.matches(".*\\.(png|jpe?g|webp|bmp|gif)$")) {
            result = extractImage(context, uri);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported file type: " + name
                    + ". Supported: PDF, DOCX, XLSX, PPTX, TXT, CSV and images."
            );
        }

        if (result == null || result.trim().isEmpty()) {
            return "[No extractable text found in " + name
                    + ". The file may be scanned, empty, or contain unsupported content.]";
        }
        if (result.length() > MAX_CHARS) {
            result = result.substring(0, MAX_CHARS)
                    + "\n\n[Text truncated to fit the local AI context.]";
        }
        return "FILE: " + name + "\n" + result;
    }

    private static String extractPdf(Context context, Uri uri) throws Exception {
        PDFBoxResourceLoader.init(context.getApplicationContext());
        StringBuilder output = new StringBuilder();

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             PDDocument pdf = PDDocument.load(in)) {
            if (pdf.getNumberOfPages() == 0) return "";

            PDFTextStripper stripper = new PDFTextStripper();
            PDFRenderer renderer = new PDFRenderer(pdf);
            TextRecognizer recognizer = TextRecognition.getClient(
                    TextRecognizerOptions.DEFAULT_OPTIONS);

            try {
                for (int page = 1; page <= pdf.getNumberOfPages()
                        && output.length() < MAX_CHARS; page++) {
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    String pageText = stripper.getText(pdf);

                    if (pageText != null && pageText.trim().length() >= 20) {
                        output.append("\n--- PDF page ").append(page)
                                .append(" ---\n").append(pageText).append('\n');
                    } else {
                        Bitmap bitmap = null;
                        try {
                            bitmap = renderer.renderImageWithDPI(page - 1, 150);
                            String ocr = recognizeBitmap(recognizer, bitmap);
                            output.append("\n--- PDF page ").append(page)
                                    .append(" (OCR) ---\n").append(ocr).append('\n');
                        } catch (Exception e) {
                            output.append("\n[OCR failed on PDF page ")
                                    .append(page).append(": ")
                                    .append(e.getClass().getSimpleName()).append("]\n");
                        } finally {
                            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                        }
                    }
                }
            } finally {
                recognizer.close();
            }
        }
        return output.toString();
    }

    private static String extractImage(Context context, Uri uri) throws Exception {
        InputImage image = InputImage.fromFilePath(context, uri);
        TextRecognizer recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            return Tasks.await(recognizer.process(image), 60, TimeUnit.SECONDS).getText();
        } finally {
            recognizer.close();
        }
    }

    private static String recognizeBitmap(TextRecognizer recognizer, Bitmap bitmap)
            throws Exception {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        return Tasks.await(recognizer.process(image), 60, TimeUnit.SECONDS).getText();
    }

    private static String getName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(
                uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) return c.getString(i);
            }
        } catch (Exception ignored) {}
        return uri.getLastPathSegment() == null ? "selected file"
                : uri.getLastPathSegment();
    }

    private static String readLimited(InputStream in) throws Exception {
        if (in == null) throw new IllegalArgumentException("Cannot open selected file.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buffer)) != -1 && total < MAX_CHARS * 4) {
            int keep = Math.min(n, MAX_CHARS * 4 - total);
            out.write(buffer, 0, keep);
            total += keep;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static Document parseXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(
                "http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(
                "http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature(
                "http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private static String tagName(Node node) {
        String name = node.getLocalName();
        return name == null ? node.getNodeName() : name;
    }

    private static NodeList descendants(Element element, String wanted) {
        return element.getElementsByTagNameNS("*", wanted);
    }

    private static String extractXlsx(Context context, Uri uri) throws Exception {
        Map<String, byte[]> entries = new HashMap<>();

        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(raw)) {
            if (raw == null) throw new IllegalArgumentException("Cannot open Excel file.");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("../")) {
                    throw new IllegalArgumentException("Invalid Excel archive entry.");
                }
                if (entry.isDirectory()) continue;

                if (name.equals("xl/sharedStrings.xml")
                        || name.equals("xl/workbook.xml")
                        || name.equals("xl/_rels/workbook.xml.rels")
                        || name.matches("xl/worksheets/sheet[0-9]+\\.xml")) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = zip.read(buffer)) != -1) {
                        if (bytes.size() + n > MAX_XML_BYTES) {
                            throw new IllegalArgumentException(
                                    "Excel XML entry is too large: " + name);
                        }
                        bytes.write(buffer, 0, n);
                    }
                    entries.put(name, bytes.toByteArray());
                }
            }
        }

        List<String> shared = new ArrayList<>();
        byte[] sharedBytes = entries.get("xl/sharedStrings.xml");
        if (sharedBytes != null) {
            Document doc = parseXml(sharedBytes);
            NodeList items = descendants(doc.getDocumentElement(), "si");
            for (int i = 0; i < items.getLength(); i++) {
                shared.add(items.item(i).getTextContent());
            }
        }

        Map<String, String> relTargets = new HashMap<>();
        byte[] relBytes = entries.get("xl/_rels/workbook.xml.rels");
        if (relBytes != null) {
            Document doc = parseXml(relBytes);
            NodeList rels = descendants(doc.getDocumentElement(), "Relationship");
            for (int i = 0; i < rels.getLength(); i++) {
                Element e = (Element) rels.item(i);
                relTargets.put(e.getAttribute("Id"), e.getAttribute("Target"));
            }
        }

        Map<String, String> sheetNames = new HashMap<>();
        byte[] workbookBytes = entries.get("xl/workbook.xml");
        if (workbookBytes != null) {
            Document doc = parseXml(workbookBytes);
            NodeList sheets = descendants(doc.getDocumentElement(), "sheet");
            for (int i = 0; i < sheets.getLength(); i++) {
                Element e = (Element) sheets.item(i);
                String id = e.getAttributeNS(
                        "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
                        "id");
                String target = relTargets.get(id);
                if (target != null) {
                    String path = target.startsWith("/")
                            ? target.substring(1) : "xl/" + target;
                    while (path.contains("xl/../")) path = path.replace("xl/../", "");
                    if (path.startsWith("xl/worksheets/")) {
                        sheetNames.put(path, e.getAttribute("name"));
                    } else if (path.startsWith("worksheets/")) {
                        sheetNames.put("xl/" + path, e.getAttribute("name"));
                    }
                }
            }
        }

        List<String> sheetPaths = new ArrayList<>();
        for (String name : entries.keySet()) {
            if (name.matches("xl/worksheets/sheet[0-9]+\\.xml")) {
                sheetPaths.add(name);
            }
        }
        Collections.sort(sheetPaths, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                int na = Integer.parseInt(a.replaceAll("\\D+", ""));
                int nb = Integer.parseInt(b.replaceAll("\\D+", ""));
                return Integer.compare(na, nb);
            }
        });

        StringBuilder out = new StringBuilder();
        for (String path : sheetPaths) {
            if (out.length() >= MAX_CHARS) break;
            String label = sheetNames.get(path);
            if (label == null || label.isEmpty()) {
                label = "Sheet " + (sheetPaths.indexOf(path) + 1);
            }
            out.append("\n--- ").append(label).append(" ---\n");

            Document doc = parseXml(entries.get(path));
            NodeList rows = descendants(doc.getDocumentElement(), "row");
            for (int r = 0; r < rows.getLength() && out.length() < MAX_CHARS; r++) {
                Element row = (Element) rows.item(r);
                NodeList cells = descendants(row, "c");
                boolean first = true;

                for (int c = 0; c < cells.getLength() && out.length() < MAX_CHARS; c++) {
                    Element cell = (Element) cells.item(c);
                    String ref = cell.getAttribute("r");
                    String type = cell.getAttribute("t");
                    String value = "";

                    if ("inlineStr".equals(type)) {
                        NodeList textNodes = descendants(cell, "t");
                        StringBuilder inline = new StringBuilder();
                        for (int j = 0; j < textNodes.getLength(); j++) {
                            inline.append(textNodes.item(j).getTextContent());
                        }
                        value = inline.toString();
                    } else {
                        NodeList values = descendants(cell, "v");
                        if (values.getLength() > 0) {
                            value = values.item(0).getTextContent();
                        }
                        if ("s".equals(type) && !value.isEmpty()) {
                            try {
                                int index = Integer.parseInt(value);
                                value = index >= 0 && index < shared.size()
                                        ? shared.get(index) : "[invalid shared string]";
                            } catch (NumberFormatException ignored) {}
                        } else if ("b".equals(type)) {
                            value = "1".equals(value) ? "TRUE" : "FALSE";
                        }
                    }

                    if (!first) out.append(" | ");
                    out.append(ref.isEmpty() ? "cell" : ref).append("=")
                            .append(value);
                    first = false;
                }
                if (!first) out.append('\n');
            }
        }
        return out.toString();
    }

    private static String extractOfficeXml(
            Context context, Uri uri, boolean word) throws Exception {
        StringBuilder output = new StringBuilder();
        try (InputStream raw = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(raw)) {
            if (raw == null) throw new IllegalArgumentException("Cannot open Office file.");
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null
                    && output.length() < MAX_CHARS) {
                String path = entry.getName();
                boolean wanted = word
                        ? path.equals("word/document.xml")
                            || path.startsWith("word/header")
                            || path.startsWith("word/footer")
                        : path.matches("ppt/slides/slide[0-9]+\\.xml");

                if (!wanted || entry.isDirectory()) continue;
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = zip.read(buffer)) != -1) {
                    if (bytes.size() + n > MAX_XML_BYTES) break;
                    bytes.write(buffer, 0, n);
                }
                Document doc = parseXml(bytes.toByteArray());
                NodeList nodes = descendants(doc.getDocumentElement(), "t");
                for (int i = 0; i < nodes.getLength()
                        && output.length() < MAX_CHARS; i++) {
                    String value = nodes.item(i).getTextContent();
                    if (value != null && !value.isEmpty()) output.append(value).append(' ');
                }
                output.append('\n');
            }
        }
        return output.toString();
    }
}