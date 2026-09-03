package vn.io.codelearning.springapitester.client;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointIdentity;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

import java.util.concurrent.CompletableFuture;

public class AsyncRequestRaceConditionTest {

    @Test
    public void testOutOfOrderCompletionSupersedesOldRequest() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        EndpointModel endpointA = new EndpointModel(HttpMethodEnum.GET, "/api/items", "ItemController", "com.example", "getItems");

        // First request sent at UI generation 1
        RequestExecutionContext ctx1 = tracker.begin(endpointA, 1L);
        Assert.assertEquals(RequestExecutionState.IN_FLIGHT, ctx1.getState());
        Assert.assertEquals(1L, ctx1.getUiGeneration());

        // Fast re-send or second request sent at UI generation 1
        RequestExecutionContext ctx2 = tracker.begin(endpointA, 1L);
        Assert.assertEquals(RequestExecutionState.SUPERSEDED, ctx1.getState());
        Assert.assertTrue("Context 1 must be terminal", ctx1.isTerminal());
        Assert.assertEquals(RequestExecutionState.IN_FLIGHT, ctx2.getState());

        EndpointIdentity identityA = EndpointIdentity.fromEndpoint(endpointA);

        // Older request (ctx1) finishes out of order after newer request (ctx2) was sent
        Assert.assertFalse("Superseded context 1 must not be allowed to render to UI",
                ctx1.canRenderToUi(identityA, 1L));

        // Newer request finishes
        Assert.assertTrue("Latest context 2 can render to UI",
                ctx2.canRenderToUi(identityA, 1L));
        Assert.assertTrue(ctx2.transitionToTerminal(RequestExecutionState.SUCCESS));
        Assert.assertEquals(RequestExecutionState.SUCCESS, ctx2.getState());

        // Late attempt on ctx1 is a no-op
        Assert.assertFalse("Transitioning already-superseded ctx1 must fail (idempotent)",
                ctx1.transitionToTerminal(RequestExecutionState.SUCCESS));
        Assert.assertEquals(RequestExecutionState.SUPERSEDED, ctx1.getState());
    }

    @Test
    public void testRapidEndpointSwitchingIsolatesRendering() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        EndpointModel endpointA = new EndpointModel(HttpMethodEnum.GET, "/api/a", "CtrlA", "com.example", "getA");
        EndpointModel endpointB = new EndpointModel(HttpMethodEnum.GET, "/api/b", "CtrlB", "com.example", "getB");

        EndpointIdentity identityA = EndpointIdentity.fromEndpoint(endpointA);
        EndpointIdentity identityB = EndpointIdentity.fromEndpoint(endpointB);

        // User views A at UI generation 1 and clicks Send
        RequestExecutionContext ctxA = tracker.begin(endpointA, 1L);

        // User rapidly switches to B: UI generation increments to 2
        long currentUiGen = 2L;
        EndpointIdentity visibleIdentity = identityB;

        // Response for A arrives while user is on B
        Assert.assertFalse("Response for A must not render onto tab B",
                ctxA.canRenderToUi(visibleIdentity, currentUiGen));

        // When user switches back to A: UI generation increments to 3
        currentUiGen = 3L;
        visibleIdentity = identityA;

        // ctxA was initiated at gen 1, so late rendering from that generation is prevented
        Assert.assertFalse("Response for A from older UI generation must not overwrite new generation",
                ctxA.canRenderToUi(visibleIdentity, currentUiGen));
    }

    @Test
    public void testCancellationImmediatelySetsTerminalStateAndCancelsNetworkHandle() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/data", "DataCtrl", "com.example", "saveData");

        RequestExecutionContext ctx = tracker.begin(endpoint, 1L);

        okhttp3.Call mockCall = new OkHttpClient().newCall(new Request.Builder().url("http://localhost/test").build());
        CompletableFuture<HttpResponseModel> future = new CompletableFuture<>();
        HttpClientService.RequestHandle handle = new HttpClientService.RequestHandle(mockCall, future);
        ctx.setRequestHandle(handle);

        // User clicks Cancel
        ctx.cancel();
        Assert.assertEquals(RequestExecutionState.CANCELED, ctx.getState());
        Assert.assertTrue(ctx.isTerminal());
        Assert.assertTrue(mockCall.isCanceled());
        Assert.assertTrue(future.isCancelled());

        // Subsequent network arrival or error must be ignored
        Assert.assertFalse(ctx.transitionToTerminal(RequestExecutionState.SUCCESS));
        Assert.assertEquals(RequestExecutionState.CANCELED, ctx.getState());
    }

    @Test
    public void testDisposingTrackerTerminatesAllActiveContexts() {
        RequestExecutionTracker tracker = new RequestExecutionTracker();
        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/api/1", "C1", "p", "m1");
        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/api/2", "C2", "p", "m2");

        RequestExecutionContext ctx1 = tracker.begin(ep1, 1L);
        RequestExecutionContext ctx2 = tracker.begin(ep2, 2L);

        Assert.assertFalse(ctx1.isTerminal());
        Assert.assertFalse(ctx2.isTerminal());

        tracker.dispose();

        Assert.assertEquals(RequestExecutionState.DISPOSED, ctx1.getState());
        Assert.assertEquals(RequestExecutionState.DISPOSED, ctx2.getState());
        Assert.assertNull(tracker.getActiveContext(ep1));
        Assert.assertNull(tracker.getActiveContext(ep2));
    }
}
