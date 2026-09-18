package vn.io.codelearning.springapitester.ui;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.ServerConfigMetadata;

public class EndpointDetailPanelServerConfigTooltipTest {

    @Test
    public void testTooltipReportsPreparedConfigState() {
        EndpointModel resolved = endpoint(new ServerConfigMetadata("/project/application.yml", false, false));
        EndpointModel fallback = endpoint(new ServerConfigMetadata("", true, false));
        EndpointModel unresolved = endpoint(new ServerConfigMetadata("/project/application.yml", false, true));

        Assert.assertEquals(
                "Resolved from: /project/application.yml",
                EndpointDetailPanel.serverConfigTooltip(resolved));
        Assert.assertEquals(
                "Info: Default fallback port used (no server.port configured).",
                EndpointDetailPanel.serverConfigTooltip(fallback));
        Assert.assertEquals(
                "Warning: Configuration has unresolved placeholders; fallback port used.",
                EndpointDetailPanel.serverConfigTooltip(unresolved));
    }

    @Test
    public void testTooltipIsClearedForEndpointsWithoutScannerMetadata() {
        EndpointModel endpoint = endpoint(new ServerConfigMetadata("/project/application.yml", false, false));
        endpoint.setManual(true);
        Assert.assertNull(EndpointDetailPanel.serverConfigTooltip(endpoint));

        endpoint.setManual(false);
        endpoint.setAbsoluteUrl(true);
        Assert.assertNull(EndpointDetailPanel.serverConfigTooltip(endpoint));

        endpoint.setAbsoluteUrl(false);
        endpoint.setServerConfigMetadata(null);
        Assert.assertNull(EndpointDetailPanel.serverConfigTooltip(endpoint));
    }

    private EndpointModel endpoint(ServerConfigMetadata metadata) {
        EndpointModel endpoint = new EndpointModel();
        endpoint.setServerConfigMetadata(metadata);
        return endpoint;
    }
}
