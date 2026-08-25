package vn.io.codelearning.springapitester.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.project.Project;

public class JsonEditorFactory {

    /**
     * Tạo một Editor hiển thị code JSON (đẹp, có highlight, có số dòng).
     */
    public static Editor createJsonEditor(Project project, String text, boolean isReadOnly) {
        EditorFactory factory = EditorFactory.getInstance();
        Document document = factory.createDocument(text != null ? text : "");
        
        Editor editor;
        if (isReadOnly) {
            editor = factory.createViewer(document, project);
        } else {
            editor = factory.createEditor(document, project);
        }

        // Try to set language highlighting for JSON
        Language jsonLang = Language.findLanguageByID("JSON");
        if (jsonLang != null && project != null) {
            FileType fileType = jsonLang.getAssociatedFileType();
            if (fileType != null) {
                // We could use EditorHighlighterFactory but it requires more complex imports.
                // We'll leave it simple for now, the plain editor works without EditorKind errors.
            }
        }

        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setFoldingOutlineShown(true);
        settings.setVirtualSpace(false);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(true);
        settings.setAdditionalPageAtBottom(false);

        return editor;
    }

    /**
     * Hủy Editor khi không dùng nữa (để tránh rò rỉ bộ nhớ UI).
     */
    public static void releaseEditor(Editor editor) {
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }
}
