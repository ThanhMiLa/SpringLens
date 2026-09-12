package vn.io.codelearning.springapitester.ui;

import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.openapi.editor.Editor;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.RequestTab;

import java.lang.reflect.Field;

public class EndpointDetailPanelTabMemoryTest extends BasePlatformTestCase {

    public void testEachEndpointRestoresItsOwnLastRequestTab() throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> {
            EndpointDetailPanel panel = new EndpointDetailPanel(getProject());
            try {
                EndpointModel endpointA = manualEndpoint("a");
                EndpointModel endpointB = manualEndpoint("b");
                JBTabbedPane requestTabs = requestTabs(panel);

                panel.displayEndpoint(endpointA);
                assertEquals(0, requestTabs.getSelectedIndex());

                requestTabs.setSelectedIndex(EndpointDetailPanel.requestTabIndex(RequestTab.HEADERS));
                assertEquals(RequestTab.HEADERS, endpointA.getSelectedRequestTab());

                panel.displayEndpoint(endpointB);
                assertEquals(0, requestTabs.getSelectedIndex());

                requestTabs.setSelectedIndex(EndpointDetailPanel.requestTabIndex(RequestTab.BODY));
                assertEquals(RequestTab.BODY, endpointB.getSelectedRequestTab());

                panel.displayEndpoint(endpointA);
                assertEquals(EndpointDetailPanel.requestTabIndex(RequestTab.HEADERS), requestTabs.getSelectedIndex());

                panel.displayEndpoint(endpointB);
                assertEquals(EndpointDetailPanel.requestTabIndex(RequestTab.BODY), requestTabs.getSelectedIndex());
            } finally {
                releaseEditor(panel, "requestBodyEditor");
                releaseEditor(panel, "responseBodyEditor");
            }
        });
    }

    private EndpointModel manualEndpoint(String id) {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/" + id, "", "", id);
        endpoint.setManual(true);
        endpoint.setId("request-tab-" + id);
        return endpoint;
    }

    private JBTabbedPane requestTabs(EndpointDetailPanel panel) {
        return (JBTabbedPane) readField(panel, "requestTabs");
    }

    private void releaseEditor(EndpointDetailPanel panel, String fieldName) {
        CodeEditorUtil.releaseEditor((Editor) readField(panel, fieldName));
    }

    private Object readField(EndpointDetailPanel panel, String fieldName) {
        try {
            Field field = EndpointDetailPanel.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(panel);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to access EndpointDetailPanel field: " + fieldName, e);
        }
    }
}
