package vn.io.codelearning.springapitester.client;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.state.EndpointSavedState;
import vn.io.codelearning.springapitester.state.SpringLensState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ResponseReaderTest {

    @Test
    public void testSmallTextResponseRetainsCurrentBehavior() throws IOException {
        String json = "{\"status\":\"ok\",\"code\":200}";
        ResponseBody body = ResponseBody.create(MediaType.parse("application/json"), json);

        ResponseReader.ReadResult result = ResponseReader.readBody(body, "application/json", 1024);
        Assert.assertFalse(result.isBinary());
        Assert.assertFalse(result.isTruncated());
        Assert.assertEquals(json, result.getText());
        Assert.assertEquals(json.getBytes(StandardCharsets.UTF_8).length, result.getTotalBytes());
    }

    @Test
    public void testLargeResponseTruncation() throws IOException {
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            large.append("line_number_").append(i).append("\n");
        }
        String largeStr = large.toString();
        ResponseBody body = ResponseBody.create(MediaType.parse("text/plain"), largeStr);

        int maxLimit = 4096; // 4 KB
        ResponseReader.ReadResult result = ResponseReader.readBody(body, "text/plain", maxLimit);

        Assert.assertTrue(result.isTruncated());
        Assert.assertFalse(result.isBinary());
        Assert.assertTrue(result.getText().contains("--- [Response truncated at"));
        // Ensure text size without banner is bounded by maxLimit
        Assert.assertTrue(result.getRawBytes().length <= maxLimit);
    }

    @Test
    public void testBinaryContentTypeDetection() throws IOException {
        byte[] pngBytes = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
        ResponseBody body = ResponseBody.create(MediaType.parse("image/png"), pngBytes);

        ResponseReader.ReadResult result = ResponseReader.readBody(body, "image/png", 1024);
        Assert.assertTrue(result.isBinary());
        Assert.assertTrue(result.getText().contains("[Binary data: image/png"));
        Assert.assertArrayEquals(pngBytes, result.getRawBytes());
    }

    @Test
    public void testBinaryPayloadDetectionByContent() throws IOException {
        // Content-type is text/plain but data has null bytes and binary control characters
        byte[] binarySample = new byte[256];
        binarySample[10] = 0x00;
        binarySample[20] = 0x01;
        binarySample[30] = 0x02;

        ResponseBody body = ResponseBody.create(MediaType.parse("text/plain"), binarySample);
        ResponseReader.ReadResult result = ResponseReader.readBody(body, "text/plain", 1024);

        Assert.assertTrue(result.isBinary());
        Assert.assertTrue(result.getText().contains("[Binary data:"));
    }

    @Test
    public void testVietnameseAndUtf8JsonIsNotClassifiedAsBinary() throws IOException {
        String vietnameseJson = "{\"courses\":[{\"id\":1,\"title\":\"Khóa học Lập trình Spring Boot nâng cao\"," +
                "\"description\":\"Học viên sẽ nắm vững kiến thức kiến trúc microservices và thực hành dự án thực tế.\"," +
                "\"instructor\":\"Nguyễn Văn A\",\"price\":\"1.200.000đ\"}]}";

        ResponseBody body = ResponseBody.create(MediaType.parse("application/json;charset=UTF-8"), vietnameseJson);
        ResponseReader.ReadResult result = ResponseReader.readBody(body, "application/json;charset=UTF-8", 1024 * 1024);

        Assert.assertFalse(result.isBinary());
        Assert.assertFalse(result.isTruncated());
        Assert.assertEquals(vietnameseJson, result.getText());

        // Also verify with raw isBinaryData on byte sample
        byte[] bytes = vietnameseJson.getBytes(StandardCharsets.UTF_8);
        Assert.assertFalse(ResponseReader.isBinaryData(bytes, bytes.length));
    }

    @Test
    public void testUtf8BoundarySafety() {
        // 4-byte UTF-8 emoji: 🚀 is 0xF0, 0x9F, 0x9A, 0x80
        byte[] emoji = "Hello 🚀 World".getBytes(StandardCharsets.UTF_8);

        // Find emoji start
        int emojiStart = -1;
        for (int i = 0; i < emoji.length; i++) {
            if ((emoji[i] & 0xFF) == 0xF0) {
                emojiStart = i;
                break;
            }
        }
        Assert.assertTrue(emojiStart > 0);

        // Splitting 1 byte into the emoji (emojiStart + 1)
        int safe1 = ResponseReader.findSafeUtf8Boundary(emoji, emojiStart + 1);
        Assert.assertEquals(emojiStart, safe1);

        // Splitting 2 bytes into the emoji (emojiStart + 2)
        int safe2 = ResponseReader.findSafeUtf8Boundary(emoji, emojiStart + 2);
        Assert.assertEquals(emojiStart, safe2);

        // Splitting 3 bytes into the emoji (emojiStart + 3)
        int safe3 = ResponseReader.findSafeUtf8Boundary(emoji, emojiStart + 3);
        Assert.assertEquals(emojiStart, safe3);

        // Exactly at completion (emojiStart + 4)
        int safe4 = ResponseReader.findSafeUtf8Boundary(emoji, emojiStart + 4);
        Assert.assertEquals(emojiStart + 4, safe4);

        // Verify that decoding with safe cut does not produce replacement character \uFFFD
        String safeStr = new String(emoji, 0, safe2, StandardCharsets.UTF_8);
        Assert.assertFalse(safeStr.contains("\uFFFD"));
    }

    @Test
    public void testPersistedResponseTruncationInState() {
        SpringLensState state = new SpringLensState();
        state.persistResponseHistory = true;

        EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/large", "C", "P", "m");
        char[] bigChars = new char[500 * 1024]; // 500 KB
        Arrays.fill(bigChars, 'X');
        ep.setLastResponseBody(new String(bigChars));

        state.saveEndpoint(ep);

        EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(ep));
        Assert.assertNotNull(saved);
        Assert.assertTrue(saved.lastResponseBody.length() <= SpringLensState.MAX_PERSISTED_BODY_BYTES + 100);
        Assert.assertTrue(saved.lastResponseBody.contains("Persisted snapshot truncated at 256 KB"));
    }

    @Test
    public void testResponseStorageQuotaEnforcement() {
        SpringLensState state = new SpringLensState();
        state.persistResponseHistory = true;

        // Add 25 endpoints each with 250 KB response (Total ~6.25 MB > 5 MB quota)
        char[] chars = new char[250 * 1024];
        Arrays.fill(chars, 'A');
        String chunk = new String(chars);

        for (int i = 0; i < 25; i++) {
            EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/endpoint/" + i, "C", "P", "m" + i);
            ep.setMethodSignature("m" + i + "()");
            ep.setLastResponseBody(chunk);
            state.saveEndpoint(ep);
        }

        // Calculate total response storage
        long totalStorage = 0;
        for (EndpointSavedState s : state.endpoints.values()) {
            totalStorage += s.lastResponseBody.length();
        }

        // Must be capped by MAX_TOTAL_RESPONSE_STORAGE_BYTES
        Assert.assertTrue(totalStorage <= SpringLensState.MAX_TOTAL_RESPONSE_STORAGE_BYTES);
    }

    @Test
    public void testStreamingLargePayloadHaltsWithoutOOM() throws IOException {
        // Create an OkHttp ResponseBody backed by a custom Source generating 100 MB on the fly
        final long hundredMb = 100L * 1024 * 1024;
        okio.Source syntheticLargeSource = new okio.Source() {
            private long generated = 0;
            @Override
            public long read(okio.Buffer sink, long byteCount) {
                if (generated >= hundredMb) return -1;
                long toProduce = Math.min(byteCount, hundredMb - generated);
                byte[] chunk = new byte[(int) Math.min(toProduce, 65536)];
                Arrays.fill(chunk, (byte) 'A');
                sink.write(chunk);
                generated += chunk.length;
                return chunk.length;
            }
            @Override public okio.Timeout timeout() { return okio.Timeout.NONE; }
            @Override public void close() {}
        };

        ResponseBody body = ResponseBody.create(
                MediaType.parse("text/plain"),
                hundredMb,
                okio.Okio.buffer(syntheticLargeSource)
        );

        int maxLimit = 1024 * 1024; // 1 MB cap
        ResponseReader.ReadResult result = ResponseReader.readBody(body, "text/plain", maxLimit);

        Assert.assertTrue(result.isTruncated());
        Assert.assertEquals(hundredMb, result.getTotalBytes());
        Assert.assertTrue(result.getRawBytes().length <= maxLimit);
        Assert.assertTrue(result.getText().contains("... [truncated: showing"));
    }

    @Test
    public void testResponseHistoryBoundedAndEvictsOldest() {
        SpringLensState state = new SpringLensState();
        state.persistResponseHistory = true;

        EndpointModel ep = new EndpointModel(HttpMethodEnum.POST, "/api/history", "Controller", "Pkg", "method");
        state.saveEndpoint(ep);

        // Save 30 distinct responses sequentially
        for (int i = 1; i <= 30; i++) {
            ep.setLastResponseStatusCode(200);
            ep.setLastResponseStatusMessage("OK " + i);
            ep.setLastResponseBody("{\"run\":" + i + "}");
            ep.setLastResponseTimeTakenMs(i * 10L);
            state.saveEndpoint(ep);
        }

        EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(ep));
        Assert.assertNotNull(saved);
        Assert.assertEquals(EndpointSavedState.MAX_RESPONSE_HISTORY_ENTRIES, saved.responseHistory.size());

        // The oldest remaining entry should be run #11 (1..10 evicted)
        Assert.assertEquals("OK 11", saved.responseHistory.get(0).statusMessage);
        // The newest remaining entry should be run #30
        Assert.assertEquals("OK 30", saved.responseHistory.get(19).statusMessage);
    }

    @Test
    public void testRawResponseBytesFidelityPreserved() {
        byte[] originalBinary = new byte[]{(byte) 0x00, (byte) 0xFF, (byte) 0xFE, (byte) 0x12, (byte) 0x34};
        EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/binary", "C", "P", "m");
        ep.setLastResponseRawBytes(originalBinary);

        byte[] retrieved = ep.getLastResponseRawBytes();
        Assert.assertArrayEquals(originalBinary, retrieved);

        // Mutation on returned array does not affect stored array
        retrieved[0] = (byte) 0xAA;
        Assert.assertEquals((byte) 0x00, ep.getLastResponseRawBytes()[0]);
    }
}
