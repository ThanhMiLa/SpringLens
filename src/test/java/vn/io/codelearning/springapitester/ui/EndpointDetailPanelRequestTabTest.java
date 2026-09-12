package vn.io.codelearning.springapitester.ui;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.RequestTab;

public class EndpointDetailPanelRequestTabTest {

    @Test
    public void testRequestTabIndexMappingIsStable() {
        Assert.assertEquals(0, EndpointDetailPanel.requestTabIndex(RequestTab.PARAMS));
        Assert.assertEquals(1, EndpointDetailPanel.requestTabIndex(RequestTab.HEADERS));
        Assert.assertEquals(2, EndpointDetailPanel.requestTabIndex(RequestTab.COOKIES));
        Assert.assertEquals(3, EndpointDetailPanel.requestTabIndex(RequestTab.AUTH));
        Assert.assertEquals(4, EndpointDetailPanel.requestTabIndex(RequestTab.BODY));

        Assert.assertEquals(RequestTab.PARAMS, EndpointDetailPanel.requestTabForIndex(0));
        Assert.assertEquals(RequestTab.HEADERS, EndpointDetailPanel.requestTabForIndex(1));
        Assert.assertEquals(RequestTab.COOKIES, EndpointDetailPanel.requestTabForIndex(2));
        Assert.assertEquals(RequestTab.AUTH, EndpointDetailPanel.requestTabForIndex(3));
        Assert.assertEquals(RequestTab.BODY, EndpointDetailPanel.requestTabForIndex(4));
    }

    @Test
    public void testInvalidRequestTabIndexFallsBackToParams() {
        Assert.assertEquals(RequestTab.PARAMS, EndpointDetailPanel.requestTabForIndex(-1));
        Assert.assertEquals(RequestTab.PARAMS, EndpointDetailPanel.requestTabForIndex(5));
        Assert.assertEquals(0, EndpointDetailPanel.requestTabIndex(null));
    }
}
