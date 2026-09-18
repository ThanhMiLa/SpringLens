package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.Callable;

public class SpringConfigResolutionThreadingTest extends BasePlatformTestCase {

    public void testResolutionRejectsEventDispatchThread() throws Exception {
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());
        service.invalidateCache();

        EdtTestUtil.runInEdtAndWait(() -> {
            try {
                service.resolveServerConfig();
                fail("Configuration resolution must reject the event dispatch thread");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("background thread"));
            }
        });
    }

    public void testResolutionRunsInBackgroundSmartReadAction() throws Exception {
        myFixture.addFileToProject("src/main/resources/application.properties", "server.port=9091");
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());
        service.invalidateCache();

        SpringServerConfig config = ApplicationManager.getApplication().executeOnPooledThread(
                (Callable<SpringServerConfig>) service::resolveServerConfig
        ).get();

        assertEquals(9091, config.getPort());
    }
}
