package vn.io.codelearning.springapitester.client;

import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class ResponseReader {

    public static final int DEFAULT_MAX_PREVIEW_BYTES = 2 * 1024 * 1024; // 2 MB
    public static final int DEFAULT_MAX_PERSISTED_BYTES = 256 * 1024; // 256 KB
    public static final int MAX_JSON_PRETTY_PRINT_CHARS = 256 * 1024; // 256 KB

    private ResponseReader() {}

    public static class ReadResult {
        private final String text;
        private final boolean isBinary;
        private final boolean isTruncated;
        private final long totalBytes;
        private final byte[] rawBytes;

        public ReadResult(String text, boolean isBinary, boolean isTruncated, long totalBytes, byte[] rawBytes) {
            this.text = text;
            this.isBinary = isBinary;
            this.isTruncated = isTruncated;
            this.totalBytes = totalBytes;
            this.rawBytes = rawBytes;
        }

        public String getText() { return text; }
        public boolean isBinary() { return isBinary; }
        public boolean isTruncated() { return isTruncated; }
        public long getTotalBytes() { return totalBytes; }
        public byte[] getRawBytes() { return rawBytes; }
    }

    public static boolean isBinaryContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return false;
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("image/")
                || ct.startsWith("audio/")
                || ct.startsWith("video/")
                || ct.contains("octet-stream")
                || ct.contains("pdf")
                || ct.contains("zip")
                || ct.contains("gzip")
                || ct.contains("tar")
                || ct.contains("binary");
    }

    public static boolean isBinaryData(byte[] sample, int length) {
        if (sample == null || length <= 0) return false;
        int checkLen = Math.min(length, 1024);
        int nonPrintable = 0;
        for (int i = 0; i < checkLen; i++) {
            byte b = sample[i];
            if (b == 0) {
                return true;
            }
            if ((b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) || b == 0x7F) {
                nonPrintable++;
            }
        }
        return nonPrintable > (checkLen * 0.1);
    }

    public static int findSafeUtf8Boundary(byte[] data, int length) {
        if (data == null || length <= 0) return 0;
        if (length > data.length) length = data.length;

        int i = length - 1;
        int continuationCount = 0;
        while (i >= 0 && continuationCount < 4 && (data[i] & 0xC0) == 0x80) {
            continuationCount++;
            i--;
        }

        if (i < 0) {
            return length;
        }

        byte lead = data[i];
        if ((lead & 0x80) == 0) {
            return length;
        }

        int expectedLength;
        if ((lead & 0xE0) == 0xC0) {
            expectedLength = 2;
        } else if ((lead & 0xF0) == 0xE0) {
            expectedLength = 3;
        } else if ((lead & 0xF8) == 0xF0) {
            expectedLength = 4;
        } else {
            return length;
        }

        int actualLength = 1 + continuationCount;
        if (actualLength < expectedLength) {
            return i;
        }

        return length;
    }

    public static ReadResult readBody(ResponseBody responseBody, String contentType, int maxBytes) throws IOException {
        if (responseBody == null) {
            return new ReadResult("", false, false, 0, new byte[0]);
        }

        BufferedSource source = responseBody.source();
        Buffer buffer = new Buffer();
        boolean isDeclaredBinary = isBinaryContentType(contentType);

        long bytesRead = 0;
        byte[] chunk = new byte[8192];
        while (bytesRead < maxBytes) {
            int toRead = (int) Math.min(chunk.length, maxBytes - bytesRead);
            int read = source.read(chunk, 0, toRead);
            if (read == -1) break;
            buffer.write(chunk, 0, read);
            bytesRead += read;
        }

        byte[] previewBytes = buffer.readByteArray();
        long totalBytes = bytesRead;

        boolean isTruncated = false;
        long contentLength = responseBody.contentLength();
        if (contentLength >= 0) {
            totalBytes = contentLength;
            isTruncated = contentLength > bytesRead;
        } else {
            if (!source.exhausted()) {
                isTruncated = true;
                long maxDrain = 10L * 1024 * 1024;
                long drained = 0;
                while (drained < maxDrain && !source.exhausted()) {
                    long read = source.read(buffer, 8192);
                    if (read == -1) break;
                    drained += read;
                    buffer.clear();
                }
                totalBytes += drained;
            }
        }

        boolean isBinary = isDeclaredBinary || isBinaryData(previewBytes, previewBytes.length);
        if (isBinary) {
            String mime = (contentType != null && !contentType.isBlank()) ? contentType : "application/octet-stream";
            String note = "[Binary data: " + mime + ", " + formatByteSize(totalBytes) + "]";
            if (isTruncated) {
                note += "\n\n--- [Binary preview truncated at " + formatByteSize(maxBytes) + ". Total size: " + formatByteSize(totalBytes) + "] ---";
            }
            return new ReadResult(note, true, isTruncated, totalBytes, previewBytes);
        }

        int safeBoundary = isTruncated
                ? findSafeUtf8Boundary(previewBytes, previewBytes.length)
                : previewBytes.length;

        String text = new String(previewBytes, 0, safeBoundary, StandardCharsets.UTF_8);
        if (isTruncated) {
            text += "\n\n--- [Response truncated at " + formatByteSize(maxBytes) + ". Total size: " + formatByteSize(totalBytes) + "] ---";
        }

        return new ReadResult(text, false, isTruncated, totalBytes, previewBytes);
    }

    public static String formatByteSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}
