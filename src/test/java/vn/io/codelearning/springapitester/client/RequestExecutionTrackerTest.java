package vn.io.codelearning.springapitester.client;

import org.junit.Assert;
import org.junit.Test;

public class RequestExecutionTrackerTest {

    @Test
    public void testOnlyLatestRequestForEndpointIsCurrent() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        long first = tracker.begin("endpoint-a");
        long second = tracker.begin("endpoint-a");

        Assert.assertFalse(tracker.isLatest("endpoint-a", first));
        Assert.assertTrue(tracker.isLatest("endpoint-a", second));

        tracker.clear("endpoint-a", first);
        Assert.assertTrue(tracker.isLatest("endpoint-a", second));
        tracker.clear("endpoint-a", second);
        Assert.assertFalse(tracker.isLatest("endpoint-a", second));
    }

    @Test
    public void testRequestsAreIndependentAcrossEndpoints() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        long endpointA = tracker.begin("endpoint-a");
        long endpointB = tracker.begin("endpoint-b");

        Assert.assertTrue(tracker.isLatest("endpoint-a", endpointA));
        Assert.assertTrue(tracker.isLatest("endpoint-b", endpointB));
        Assert.assertFalse(tracker.isLatest("endpoint-a", endpointB));
    }
}
