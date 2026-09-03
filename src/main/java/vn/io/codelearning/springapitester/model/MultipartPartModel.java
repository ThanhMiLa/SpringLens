package vn.io.codelearning.springapitester.model;

import java.io.File;

/**
 * Đại diện cho một phần có cấu trúc trong multipart/form-data request.
 */
public final class MultipartPartModel {

    public static final long LARGE_FILE_THRESHOLD_BYTES = 50L * 1024 * 1024; // 50 MB

    private final String name;
    private final String textValue;
    private final File file;
    private final String contentType;
    private final String filenameOverride;

    public MultipartPartModel(String name, File file) {
        this(name, file, null, null);
    }

    public MultipartPartModel(String name, File file, String contentType) {
        this(name, file, contentType, null);
    }

    public MultipartPartModel(String name, File file, String contentType, String filenameOverride) {
        this.name = name != null ? name : "";
        this.file = file;
        this.textValue = null;
        this.contentType = contentType;
        this.filenameOverride = filenameOverride != null && !filenameOverride.isBlank()
                ? filenameOverride.trim()
                : (file != null ? file.getName() : "");
    }

    public MultipartPartModel(String name, String textValue, String contentType) {
        this.name = name != null ? name : "";
        this.file = null;
        this.textValue = textValue != null ? textValue : "";
        this.contentType = contentType;
        this.filenameOverride = null;
    }

    public String getName() {
        return name;
    }

    public String getTextValue() {
        return textValue;
    }

    public File getFile() {
        return file;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFilenameOverride() {
        return filenameOverride;
    }

    public boolean isFile() {
        return file != null;
    }

    public boolean isJson() {
        return "application/json".equalsIgnoreCase(contentType)
                || (textValue != null && ((textValue.trim().startsWith("{") && textValue.trim().endsWith("}"))
                || (textValue.trim().startsWith("[") && textValue.trim().endsWith("]"))));
    }

    public long getFileSize() {
        return (file != null && file.exists()) ? file.length() : 0L;
    }

    public boolean isLargeFile() {
        return getFileSize() > LARGE_FILE_THRESHOLD_BYTES;
    }

    /**
     * Xác thực tính hợp lệ của file nếu part này là file upload.
     */
    public void validate() {
        if (isFile()) {
            if (!file.exists()) {
                throw new IllegalArgumentException("File not found: " + file.getPath() + " for part: " + name);
            }
            if (file.isDirectory()) {
                throw new IllegalArgumentException("Path is a directory, not a file: " + file.getPath() + " for part: " + name);
            }
        }
    }
}
