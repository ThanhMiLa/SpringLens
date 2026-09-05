package vn.io.codelearning.springapitester.state;

import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.AuthTypeEnum;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HeaderItem;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;

public class PlaintextStatePersistenceTest {

    @Test
    public void testCredentialsAndSensitiveLookingValuesRoundTripInProjectState() {
        SpringLensState state = new SpringLensState();
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/login", "AuthCtrl", "com.example", "login");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BASIC_AUTH);
        auth.setUsername("admin");
        auth.setPassword("super_secret_password_123");
        endpoint.setAuthConfig(auth);
        endpoint.addCustomHeader(new HeaderItem("Authorization", "Bearer top_secret_jwt", true));
        endpoint.addCustomHeader(new HeaderItem("Cookie", "JSESSIONID=session_cookie_secret_999", true));
        endpoint.addParameter(new ParameterModel("api_key", ParamTypeEnum.QUERY_PARAM, "String", "query_api_key_secret_888", true, "", ""));
        endpoint.addParameter(new ParameterModel("session", ParamTypeEnum.COOKIE, "String", "session_cookie_secret_999", true, "", ""));

        state.saveEndpoint(endpoint);

        Element element = XmlSerializer.serialize(state);
        String xml = new XMLOutputter(Format.getPrettyFormat()).outputString(element);
        Assert.assertTrue(xml.contains("super_secret_password_123"));
        Assert.assertTrue(xml.contains("top_secret_jwt"));
        Assert.assertTrue(xml.contains("session_cookie_secret_999"));
        Assert.assertTrue(xml.contains("query_api_key_secret_888"));

        EndpointModel restored = new EndpointModel(HttpMethodEnum.POST, "/api/login", "AuthCtrl", "com.example", "login");
        restored.addParameter(new ParameterModel("api_key", ParamTypeEnum.QUERY_PARAM, "String"));
        restored.addParameter(new ParameterModel("session", ParamTypeEnum.COOKIE, "String"));
        state.restoreEndpoint(restored);

        Assert.assertEquals("admin", restored.getAuthConfig().getUsername());
        Assert.assertEquals("super_secret_password_123", restored.getAuthConfig().getPassword());
        Assert.assertEquals("Bearer top_secret_jwt", restored.getCustomHeaders().get(0).getValue());
        Assert.assertEquals("JSESSIONID=session_cookie_secret_999", restored.getCustomHeaders().get(1).getValue());
        Assert.assertEquals("query_api_key_secret_888", restored.getParameters().get(0).getCurrentValue());
        Assert.assertEquals("session_cookie_secret_999", restored.getParameters().get(1).getCurrentValue());
    }

    @Test
    public void testManualEndpointPreservesSensitiveLookingValues() {
        SpringLensState state = new SpringLensState();
        EndpointModel endpoint = new EndpointModel();
        endpoint.setId("manual-credential-test");
        endpoint.setName("Manual endpoint");
        endpoint.setManual(true);
        endpoint.setHttpMethod(HttpMethodEnum.GET);
        endpoint.setPath("https://example.test/api");
        ParameterModel parameter = new ParameterModel("client_secret", ParamTypeEnum.QUERY_PARAM,
                "String", "manual-default", true, "", "");
        parameter.setCurrentValue("manual-secret");
        endpoint.addParameter(parameter);

        state.saveEndpoint(endpoint);

        EndpointSavedState saved = state.manualEndpoints.get(0);
        Assert.assertEquals("manual-secret", saved.manualParameters.get(0).getCurrentValue());
        Assert.assertEquals("manual-default", saved.manualParameters.get(0).getDefaultValue());
    }
}
