package vn.io.codelearning.springapitester.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightVirtualFile;

public class CodeEditorUtil {

    /**
     * Tạo một Editor có hỗ trợ Syntax Highlighting (Tô màu cú pháp) dựa trên loại file (json, html, xml...)
     */
    public static Editor createEditor(Project project, String text, String extension, boolean isReadOnly) {
        EditorFactory factory = EditorFactory.getInstance();
        
        // 1. Xác định FileType dựa trên đuôi mở rộng (json, html, xml)
        FileType fileType = FileTypeManager.getInstance().getFileTypeByExtension(extension.toLowerCase());
        
        // 2. Tạo một file ảo trong bộ nhớ (LightVirtualFile) để IntelliJ tự động nhận diện cú pháp
        LightVirtualFile virtualFile = new LightVirtualFile("dummy." + extension, fileType, text != null ? text : "");
        Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
        if (document == null) {
            document = factory.createDocument(text != null ? text : "");
        }

        // 3. Khởi tạo Editor
        Editor editor;
        if (isReadOnly) {
            editor = factory.createViewer(document, project);
        } else {
            editor = factory.createEditor(document, project, fileType, false);
        }

        // 4. Cấu hình giao diện (số dòng, gập code)
        EditorSettings settings = editor.getSettings();
        settings.setLineNumbersShown(true);
        settings.setFoldingOutlineShown(true);
        settings.setVirtualSpace(false);
        settings.setLineMarkerAreaShown(false);
        settings.setIndentGuidesShown(true);
        settings.setAdditionalPageAtBottom(false);

        return editor;
    }

    public static void releaseEditor(Editor editor) {
        if (editor != null && !editor.isDisposed()) {
            EditorFactory.getInstance().releaseEditor(editor);
        }
    }
}
