package vn.io.codelearning.springapitester.model;

import java.io.File;

/**
 * Đại diện cho một phần trong multipart/form-data request.
 */
public final class MultipartPartModel {

    private final String name;
    private final String textValue;
    private final File file;
    private final String contentType;

    public MultipartPartModel(String name, File file) {
        this(name, file, null);
    }

    public MultipartPartModel(String name, File file, String contentType) {
        this.name = name != null ? name : "";
        this.file = file;
        this.textValue = null;
        this.contentType = contentType;
    }

    public MultipartPartModel(String name, String textValue, String contentType) {
        this.name = name != null ? name : "";
        this.file = null;
        this.textValue = textValue != null ? textValue : "";
        this.contentType = contentType;
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

    public boolean isFile() {
        return file != null;
    }
}
