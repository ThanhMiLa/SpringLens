package vn.io.codelearning.springapitester.client;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;

import java.util.List;

public class QueryParameterUrlBuilderTest {

    @Test
    public void testApplyQueryParametersJoinsMultipleValuesWithoutTrailingSeparator() {
        String url = QueryParameterUrlBuilder.applyQueryParameters(
                "http://localhost:8888/post/my-posts",
                List.of(queryParameter("page", "1"), queryParameter("size", "10"))
        );

        Assert.assertEquals("http://localhost:8888/post/my-posts?page=1&size=10", url);
        Assert.assertFalse(url.endsWith("&"));
    }

    @Test
    public void testApplyQueryParametersPreservesUnmanagedQueryAndFragment() {
        String url = QueryParameterUrlBuilder.applyQueryParameters(
                "http://localhost:8888/post/my-posts?sort=createdAt#results",
                List.of(queryParameter("page", "1"), queryParameter("size", "10"))
        );

        Assert.assertEquals(
                "http://localhost:8888/post/my-posts?sort=createdAt&page=1&size=10#results",
                url
        );
    }

    @Test
    public void testApplyQueryParametersOmitsBlankAndDisabledValues() {
        ParameterModel blank = queryParameter("filter", "   ");
        ParameterModel disabled = queryParameter("includeArchived", "true");
        disabled.setEnabled(false);

        String url = QueryParameterUrlBuilder.applyQueryParameters(
                "http://localhost:8888/post/my-posts?filter=old&includeArchived=false",
                List.of(blank, disabled, queryParameter("page", "1"))
        );

        Assert.assertEquals("http://localhost:8888/post/my-posts?page=1", url);
    }

    @Test
    public void testApplyQueryParametersReplacesManagedValuesAndIsIdempotent() {
        List<ParameterModel> parameters = List.of(queryParameter("page", "2"));
        String url = QueryParameterUrlBuilder.applyQueryParameters(
                "http://localhost:8888/post/my-posts?page=1&page=old",
                parameters
        );

        Assert.assertEquals("http://localhost:8888/post/my-posts?page=2", url);
        Assert.assertEquals(url, QueryParameterUrlBuilder.applyQueryParameters(url, parameters));
    }

    @Test
    public void testApplyQueryParametersEncodesSpecialCharactersAndPreservesPathVariables() {
        String url = QueryParameterUrlBuilder.applyQueryParameters(
                "http://localhost:8888/post/{postId:[0-9]+}",
                List.of(queryParameter("filter", "a&b=c tiếng Việt"))
        );

        Assert.assertTrue(url.startsWith("http://localhost:8888/post/{postId:[0-9]+}?filter="));
        Assert.assertTrue(url.contains("a%26b%3Dc%20ti%E1%BA%BFng%20Vi%E1%BB%87t"));
    }

    @Test
    public void testRemoveQueryParametersKeepsOnlyUnmanagedValues() {
        String url = QueryParameterUrlBuilder.removeQueryParameters(
                "http://localhost:8888/post/my-posts?sort=createdAt&page=1&size=10",
                List.of(queryParameter("page", "1"), queryParameter("size", "10"))
        );

        Assert.assertEquals("http://localhost:8888/post/my-posts?sort=createdAt", url);
    }

    private ParameterModel queryParameter(String name, String value) {
        ParameterModel parameter = new ParameterModel(name, ParamTypeEnum.QUERY_PARAM, "String");
        parameter.setCurrentValue(value);
        return parameter;
    }
}
