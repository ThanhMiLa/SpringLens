package vn.io.codelearning.springapitester.state;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;

import java.util.ArrayList;
import java.util.List;

public class PruneOrphanTest {

    @Test
    public void testPruneOrphanScannedEndpointsEmptyListNoOp() {
        SpringLensState state = new SpringLensState();
        EndpointSavedState saved = new EndpointSavedState();
        saved.id = "test-1";
        state.endpoints.put("scanned:test-1", saved);

        List<EndpointModel> scanned = new ArrayList<>();
        // this shouldn't remove anything because it's empty
        state.pruneOrphanScannedEndpoints(scanned);

        Assert.assertTrue("Should not remove when list is empty", state.endpoints.containsKey("scanned:test-1"));
    }

    @Test
    public void testPruneOrphanScannedEndpointsNullNoOp() {
        SpringLensState state = new SpringLensState();
        EndpointSavedState saved = new EndpointSavedState();
        saved.id = "test-2";
        state.endpoints.put("scanned:test-2", saved);

        state.pruneOrphanScannedEndpoints(null);

        Assert.assertTrue("Should not remove when list is null", state.endpoints.containsKey("scanned:test-2"));
    }
}
