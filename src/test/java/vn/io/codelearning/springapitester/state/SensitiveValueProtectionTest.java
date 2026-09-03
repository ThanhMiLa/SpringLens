package vn.io.codelearning.springapitester.state;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

import java.util.HashMap;
import java.util.Map;

public class SensitiveValueProtectionTest {

    @Test
    public void testSensitiveValueClassifierDefaultAndCustomPatterns() {
        // Case-insensitivity & default keywords
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("AUTHORIZATION", ParamTypeEnum.HEADER));
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("jsessionid", ParamTypeEnum.COOKIE));
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("user_token", ParamTypeEnum.QUERY_PARAM));
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("client_secret", ParamTypeEnum.QUERY_PARAM));
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("adminPassword", ParamTypeEnum.FORM_DATA));
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("my_api_key", ParamTypeEnum.QUERY_PARAM));

        // Non-sensitive
        Assert.assertFalse(SensitiveValueClassifier.isSensitive("page", ParamTypeEnum.QUERY_PARAM));
        Assert.assertFalse(SensitiveValueClassifier.isSensitive("limit", ParamTypeEnum.QUERY_PARAM));
        Assert.assertFalse(SensitiveValueClassifier.isSensitive("Content-Type", ParamTypeEnum.HEADER));

        // Custom patterns
        Assert.assertFalse(SensitiveValueClassifier.isSensitive("internalOrgCode", ParamTypeEnum.QUERY_PARAM));
        SensitiveValueClassifier.addCustomPattern("internalOrgCode");
        Assert.assertTrue(SensitiveValueClassifier.isSensitive("internalOrgCode", ParamTypeEnum.QUERY_PARAM));
        SensitiveValueClassifier.clearCustomPatterns();
        Assert.assertFalse(SensitiveValueClassifier.isSensitive("internalOrgCode", ParamTypeEnum.QUERY_PARAM));
    }

    @Test
    public void testSensitiveJsonRedaction() {
        String json = "{\"id\":1,\"token\":\"secret_jwt_token\",\"password\":\"P@ssword1\",\"safe\":\"hello\"}";
        String redacted = SensitiveValueClassifier.redactSensitiveJson(json);
        Assert.assertFalse(redacted.contains("secret_jwt_token"));
        Assert.assertFalse(redacted.contains("P@ssword1"));
        Assert.assertTrue(redacted.contains("[REDACTED]"));
        Assert.assertTrue(redacted.contains("\"safe\":\"hello\""));
    }

    @Test
    public void testSerializedXmlContainsNoSensitiveParametersOrSecrets() {
        MemoryBackend backend = new MemoryBackend();
        CredentialStore store = new CredentialStore("test-project", backend);

        SpringLensState state = new SpringLensState();
        state.attachCredentialStoreForTest(store);
        state.persistResponseHistory = true;

        // 1. Scanned Endpoint with sensitive header, cookie, query param, auth, and response
        EndpointModel scanned = new EndpointModel(HttpMethodEnum.POST, "/api/login", "AuthCtrl", "com.example", "login");
        scanned.getAuthConfig().setAuthType(AuthTypeEnum.BASIC_AUTH);
        scanned.getAuthConfig().setUsername("admin");
        scanned.getAuthConfig().setPassword("super_secret_password_123");
        scanned.addParameter(new ParameterModel("Authorization", ParamTypeEnum.HEADER, "String", "Bearer top_secret_jwt", true, "", ""));
        scanned.addParameter(new ParameterModel("JSESSIONID", ParamTypeEnum.COOKIE, "String", "session_cookie_secret_999", true, "", ""));
        scanned.addParameter(new ParameterModel("api_key", ParamTypeEnum.QUERY_PARAM, "String", "query_api_key_secret_888", true, "", ""));
        scanned.setLastResponseBody("{\"status\":\"ok\",\"accessToken\":\"token_body_secret_777\"}");

        state.saveEndpoint(scanned);

        // 2. Manual Endpoint with sensitive manual parameters
        EndpointModel manual = new EndpointModel();
        manual.setId("manual-uuid-42");
        manual.setName("Payment Gateway");
        manual.setManual(true);
        manual.setHttpMethod(HttpMethodEnum.POST);
        manual.setPath("https://payment.example.com/checkout");
        manual.addParameter(new ParameterModel("client_secret", ParamTypeEnum.QUERY_PARAM, "String", "manual_secret_xyz", true, "", ""));
        manual.addParameter(new ParameterModel("normal_param", ParamTypeEnum.QUERY_PARAM, "String", "public_value", true, "", ""));

        state.saveEndpoint(manual);

        // Serialize state to IntelliJ XML representation
        Element element = XmlSerializer.serialize(state);
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        String xml = outputter.outputString(element);

        // Verify that NO sensitive values exist anywhere in the generated XML
        Assert.assertFalse("Password must not be in XML", xml.contains("super_secret_password_123"));
        Assert.assertFalse("Header secret must not be in XML", xml.contains("top_secret_jwt"));
        Assert.assertFalse("Session cookie must not be in XML", xml.contains("session_cookie_secret_999"));
        Assert.assertFalse("Query API key must not be in XML", xml.contains("query_api_key_secret_888"));
        Assert.assertFalse("Manual param secret must not be in XML", xml.contains("manual_secret_xyz"));
        Assert.assertFalse("Response body secret must not be in XML", xml.contains("token_body_secret_777"));

        // Public values should still be present
        Assert.assertTrue("Public param should be in XML", xml.contains("public_value"));

        // 3. Verify memory restoration restores all sensitive values from PasswordSafe
        EndpointModel restoredScanned = new EndpointModel(HttpMethodEnum.POST, "/api/login", "AuthCtrl", "com.example", "login");
        restoredScanned.addParameter(new ParameterModel("Authorization", ParamTypeEnum.HEADER, "String"));
        restoredScanned.addParameter(new ParameterModel("JSESSIONID", ParamTypeEnum.COOKIE, "String"));
        restoredScanned.addParameter(new ParameterModel("api_key", ParamTypeEnum.QUERY_PARAM, "String"));
        state.restoreEndpoint(restoredScanned);

        Assert.assertEquals("super_secret_password_123", restoredScanned.getAuthConfig().getPassword());
        Assert.assertEquals("Bearer top_secret_jwt", restoredScanned.getParameters().get(0).getCurrentValue());
        Assert.assertEquals("session_cookie_secret_999", restoredScanned.getParameters().get(1).getCurrentValue());
        Assert.assertEquals("query_api_key_secret_888", restoredScanned.getParameters().get(2).getCurrentValue());

        // 4. Verify Clear All Data wipes secrets
        state.clearAllData();
        EndpointSavedState savedScanned = state.endpoints.get(state.getEndpointKey(scanned));
        Assert.assertNull(savedScanned);
        Assert.assertNull(store.load("scanned-id"));
    }

    private static final class MemoryBackend implements CredentialStore.Backend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String serviceName) {
            return values.get(serviceName);
        }

        @Override
        public void set(String serviceName, String value) {
            if (value == null) values.remove(serviceName);
            else values.put(serviceName, value);
        }
    }
}
