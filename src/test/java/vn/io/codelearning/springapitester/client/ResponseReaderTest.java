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
}
