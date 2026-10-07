package vn.io.codelearning.springapitester.scanner;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.UnknownConfigurationType;
import com.intellij.execution.configurations.UnknownRunConfiguration;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jdom.Element;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.util.GatewayConfigReader.GatewayConfig;
import vn.io.codelearning.springapitester.util.GatewayEndpointResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public class RunConfigurationProfileReaderTest extends BasePlatformTestCase {

    private final List<RunnerAndConfigurationSettings> createdConfigurations = new ArrayList<>();

    @Override
    protected void tearDown() throws Exception {
        try {
            EdtTestUtil.runInEdtAndWait(() -> {
                RunManager manager = RunManager.getInstance(getProject());
                manager.setSelectedConfiguration(null);
                for (RunnerAndConfigurationSettings settings : createdConfigurations) {
                    manager.removeConfiguration(settings);
                }
            });
        } finally {
            super.tearDown();
        }
    }

    public void testSavedSpringBootProfileLoadsGatewayWithoutSpringBootPlugin() throws Exception {
        myFixture.addFileToProject("src/main/resources/application-dev.yml", """
                server:
                  port: 8888
                spring:
                  cloud:
                    gateway:
                      server:
                        webflux:
                          routes:
                            - id: user-service
                              uri: http://localhost:8080
                              predicates:
                                - Path=/user/**
                """);
        RunnerAndConfigurationSettings settings = addRunConfiguration("Gateway dev", getModule().getName(), "dev");
        selectConfiguration(settings);
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());

        SpringServerConfig server = resolveServerConfig(service);
        GatewayConfig gateway = ApplicationManager.getApplication().executeOnPooledThread(
                (Callable<GatewayConfig>) service::resolveGatewayConfig
        ).get();

        assertEquals(8888, server.getPort());
        assertEquals("dev", server.getActiveProfile());
        assertEquals("8888", gateway.port);
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/auth/login", "AuthController", "example", "login");
        endpoint.setModuleName("user-service");
        endpoint.setDirectBaseUrl("http://localhost:8080/user");
        assertEquals("http://localhost:8888/user/api/auth/login",
                GatewayEndpointResolver.resolve(endpoint, gateway).getFullGatewayUrl());
    }

    public void testUniqueModuleConfigurationIsUsedWhenAnotherServiceIsSelected() throws Exception {
        addRunConfiguration("Gateway dev", getModule().getName(), "dev");
        RunnerAndConfigurationSettings other = addRunConfiguration("Other service", "other-service", "prod");
        selectConfiguration(other);

        assertEquals("dev", readProfiles(new ArrayList<>()).get("spring.profiles.active"));
    }

    public void testSelectedConfigurationFromAnotherModuleDoesNotLeakProfiles() throws Exception {
        selectConfiguration(addRunConfiguration("Other service", "other-service", "prod"));

        assertTrue(readProfiles(new ArrayList<>()).isEmpty());
    }

    public void testAmbiguousConfigurationsRequireSelection() throws Exception {
        addRunConfiguration("Gateway dev", getModule().getName(), "dev");
        addRunConfiguration("Gateway prod", getModule().getName(), "prod");
        selectConfiguration(null);
        List<String> diagnostics = new ArrayList<>();

        assertTrue(readProfiles(diagnostics).isEmpty());
        assertEquals(1, diagnostics.size());
        assertTrue(diagnostics.get(0).contains("Multiple run configurations"));
    }

    public void testChangingSelectionInvalidatesResolvedProfile() throws Exception {
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-prod.yaml", "server:\n  port: 9090\n");
        RunnerAndConfigurationSettings dev = addRunConfiguration("Gateway dev", getModule().getName(), "dev");
        RunnerAndConfigurationSettings prod = addRunConfiguration("Gateway prod", getModule().getName(), "prod");
        selectConfiguration(dev);
        SpringConfigResolutionService service = SpringConfigResolutionService.getInstance(getProject());
        assertEquals(8888, resolveServerConfig(service).getPort());

        selectConfiguration(prod);

        assertEquals(9090, resolveServerConfig(service).getPort());
    }

    public void testRunProfileOverridesProfileDeclaredInFile() throws Exception {
        myFixture.addFileToProject("src/main/resources/application.yaml", """
                spring:
                  profiles:
                    active: prod
                """);
        myFixture.addFileToProject("src/main/resources/application-dev.yaml", "server:\n  port: 8888\n");
        myFixture.addFileToProject("src/main/resources/application-prod.yaml", "server:\n  port: 9090\n");
        selectConfiguration(addRunConfiguration("Gateway dev", getModule().getName(), "dev"));

        assertEquals(8888, resolveServerConfig(SpringConfigResolutionService.getInstance(getProject())).getPort());
    }

    public void testProfilePrecedenceWithinRunConfiguration() {
        Element config = new Element("configuration");
        config.addContent(new Element("envs").addContent(new Element("env")
                .setAttribute("name", "SPRING_PROFILES_ACTIVE").setAttribute("value", "environment")));
        assertEquals("environment", RunConfigurationProfileReader.readProfileProperties(config).get("spring.profiles.active"));

        config.addContent(option("VM_PARAMETERS", "-Xmx512m -Dspring.profiles.active=vm"));
        assertEquals("vm", RunConfigurationProfileReader.readProfileProperties(config).get("spring.profiles.active"));

        config.addContent(option("ACTIVE_PROFILES", "dev"));
        assertEquals("dev", RunConfigurationProfileReader.readProfileProperties(config).get("spring.profiles.active"));

        config.addContent(option("PROGRAM_PARAMETERS", "--spring.profiles.active=\"local,test\""));
        assertEquals("local,test", RunConfigurationProfileReader.readProfileProperties(config).get("spring.profiles.active"));
    }

    public void testDefaultProfileAndUnrelatedEnvironmentValues() {
        Element config = new Element("configuration");
        config.addContent(new Element("envs")
                .addContent(new Element("env").setAttribute("name", "SPRING_PROFILES_DEFAULT").setAttribute("value", "dev"))
                .addContent(new Element("env").setAttribute("name", "UNRELATED_SETTING").setAttribute("value", "ignored")));

        assertEquals(Map.of("spring.profiles.default", "dev"), RunConfigurationProfileReader.readProfileProperties(config));
    }

    private RunnerAndConfigurationSettings addRunConfiguration(String name, String moduleName, String profile) throws Exception {
        RunnerAndConfigurationSettings[] result = new RunnerAndConfigurationSettings[1];
        EdtTestUtil.runInEdtAndWait(() -> {
            ConfigurationFactory factory = UnknownConfigurationType.getInstance().getConfigurationFactories()[0];
            UnknownRunConfiguration configuration = new UnknownRunConfiguration(factory, getProject());
            configuration.setName(name);
            Element serialized = new Element("configuration")
                    .setAttribute("name", name).setAttribute("type", "SpringBootApplicationConfigurationType");
            serialized.addContent(new Element("module").setAttribute("name", moduleName));
            serialized.addContent(option("ACTIVE_PROFILES", profile));
            configuration.readExternal(serialized);
            RunManager manager = RunManager.getInstance(getProject());
            result[0] = manager.createConfiguration(configuration, factory);
            manager.addConfiguration(result[0]);
            createdConfigurations.add(result[0]);
        });
        return result[0];
    }

    private void selectConfiguration(RunnerAndConfigurationSettings settings) throws Exception {
        EdtTestUtil.runInEdtAndWait(() -> RunManager.getInstance(getProject()).setSelectedConfiguration(settings));
    }

    private Map<String, String> readProfiles(List<String> diagnostics) throws Exception {
        return ApplicationManager.getApplication().executeOnPooledThread(
                () -> RunConfigurationProfileReader.readProfiles(getProject(), getModule(), diagnostics)
        ).get();
    }

    private SpringServerConfig resolveServerConfig(SpringConfigResolutionService service) throws Exception {
        return ApplicationManager.getApplication().executeOnPooledThread(
                () -> service.resolveServerConfig(getModule())
        ).get();
    }

    private Element option(String name, String value) {
        return new Element("option").setAttribute("name", name).setAttribute("value", value);
    }
}
