package vn.io.codelearning.springapitester.client;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;

import java.util.List;

public class PathVariableUrlResolverTest {

    @Test
    public void testResolvePathVariablesReplacesSimpleAndRegexPlaceholders() {
        String resolvedUrl = PathVariableUrlResolver.resolvePathVariables(
                "http://localhost:8888/files/{fileName}/versions/{version:[0-9]+}",
                List.of(pathVariable("fileName", "report.pdf"), pathVariable("version", "2"))
        );

        Assert.assertEquals("http://localhost:8888/files/report.pdf/versions/2", resolvedUrl);
    }

    @Test
    public void testResolvePathVariablesUsesLatestValueFromTemplate() {
        ParameterModel fileName = pathVariable("fileName", "first.pdf");
        String template = "http://localhost:8888/files/{fileName}";

        Assert.assertEquals(
                "http://localhost:8888/files/first.pdf",
                PathVariableUrlResolver.resolvePathVariables(template, List.of(fileName))
        );

        fileName.setCurrentValue("second.pdf");

        Assert.assertEquals(
                "http://localhost:8888/files/second.pdf",
                PathVariableUrlResolver.resolvePathVariables(template, List.of(fileName))
        );
    }

    @Test
    public void testResolvePathVariablesKeepsBlankPlaceholdersAndUsesDefaultValue() {
        ParameterModel blank = pathVariable("fileName", "");
        ParameterModel defaultValue = new ParameterModel(
                "version", ParamTypeEnum.PATH_VARIABLE, "Integer", "1", false, "", "");
        defaultValue.setCurrentValue("");

        String resolvedUrl = PathVariableUrlResolver.resolvePathVariables(
                "http://localhost:8888/files/{fileName}/versions/{version}",
                List.of(blank, defaultValue)
        );

        Assert.assertEquals("http://localhost:8888/files/{fileName}/versions/1", resolvedUrl);
    }

    @Test
    public void testResolvePathVariablesNullSafeAndIgnoresOtherTypes() {
        Assert.assertNull(PathVariableUrlResolver.resolvePathVariables(null, List.of()));
        Assert.assertEquals(
                "http://localhost:8080/users/{id}",
                PathVariableUrlResolver.resolvePathVariables("http://localhost:8080/users/{id}", null)
        );

        ParameterModel queryParam = new ParameterModel("id", ParamTypeEnum.QUERY_PARAM, "String");
        queryParam.setCurrentValue("100");
        java.util.List<ParameterModel> listWithNullAndOtherTypes = new java.util.ArrayList<>();
        listWithNullAndOtherTypes.add(null);
        listWithNullAndOtherTypes.add(queryParam);

        String result = PathVariableUrlResolver.resolvePathVariables(
                "http://localhost:8080/users/{id}",
                listWithNullAndOtherTypes
        );
        Assert.assertEquals("http://localhost:8080/users/{id}", result);
    }

    @Test
    public void testResolvePathVariablesWhitespacePreservesPlaceholder() {
        ParameterModel whitespaceParam = pathVariable("id", "   ");
        String result = PathVariableUrlResolver.resolvePathVariables(
                "http://localhost:8080/users/{id}",
                List.of(whitespaceParam)
        );
        Assert.assertEquals("http://localhost:8080/users/{id}", result);
    }

    private ParameterModel pathVariable(String name, String value) {
        ParameterModel parameter = new ParameterModel(name, ParamTypeEnum.PATH_VARIABLE, "String");
        parameter.setCurrentValue(value);
        return parameter;
    }
}
