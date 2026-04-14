package com.eventops.integration.export;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Utility class for generating CSV content with optional watermark headers.
 *
 * <p>Handles field escaping according to RFC 4180: fields that contain commas,
 * double quotes, or newlines are enclosed in double quotes, and embedded double
 * quotes are escaped by doubling them.</p>
 *
 * <p>This is a stateless utility — not a Spring bean.</p>
 */
public class CsvGenerator {

    private CsvGenerator() {
        // utility class — prevent instantiation
    }

    /**
     * Writes a complete CSV document to the given writer.
     *
     * @param writer          the target writer (e.g. FileWriter, StringWriter)
     * @param watermarkHeader optional watermark comment block to prepend; {@code null} to omit
     * @param headers         the CSV column header names
     * @param rows            the data rows; each element is a String array matching the headers
     * @throws IOException if an I/O error occurs while writing
     */
    public static void writeCsv(Writer writer, String watermarkHeader, String[] headers, List<String[]> rows) throws IOException {
        if (watermarkHeader != null) {
            writer.write(watermarkHeader);
            writer.write("\n");
        }
        writer.write(String.join(",", headers));
        writer.write("\n");
        for (String[] row : rows) {
            writer.write(String.join(",", escapeFields(row)));
            writer.write("\n");
        }
        writer.flush();
    }

    /**
     * Escapes an array of field values for safe CSV output.
     *
     * <p>Each field that contains a comma, double quote, or newline is wrapped
     * in double quotes. Embedded double quotes are escaped by doubling them.
     * {@code null} values are replaced with an empty string.</p>
     *
     * @param fields the raw field values
     * @return a new array with escaped values
     */
    private static String[] escapeFields(String[] fields) {
        String[] escaped = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            String val = fields[i] != null ? fields[i] : "";
            if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
                escaped[i] = "\"" + val.replace("\"", "\"\"") + "\"";
            } else {
                escaped[i] = val;
            }
        }
        return escaped;
    }
}
