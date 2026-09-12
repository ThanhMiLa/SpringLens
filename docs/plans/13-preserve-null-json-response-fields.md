# Plan 13: Preserve `null` fields in JSON responses

## Investigation result

The reported behaviour cannot be produced by SpringLens itself. This repository
is an IntelliJ HTTP-client plugin; it does not contain a Spring Boot server,
controller/DTO implementation, `application.yml`/`application.properties`, or
any Jackson `ObjectMapper` configuration.

The response path in the plugin also preserves an explicit JSON `null`:

1. `ResponseReader.readBody` decodes the received HTTP bytes without parsing or
   filtering JSON fields.
2. `HttpClientService.formatJson` parses a complete JSON response into Gson's
   `JsonElement` and pretty-prints that tree. A `JsonNull` member of a
   `JsonObject` remains a member when Gson serializes the tree.
3. `EndpointDetailPanel.applySuccessfulResponse` displays and caches that body
   string unchanged, apart from the existing binary/truncation safeguards.
4. `SpringLensState.saveEndpoint` persists the cached body unchanged, subject
   only to the existing response-size limit.

Therefore, if SpringLens shows:

```json
{
  "id": 1,
  "name": "Example"
}
```

then those are the bytes returned by the target API. The omission occurs while
the target Spring backend serializes its response, before SpringLens receives
it.

## Likely backend cause

In the backend repository, inspect the following scopes in order of precedence:

- `@JsonInclude(JsonInclude.Include.NON_NULL)` on a response DTO, its parent
  class, a mixin, or its package-level configuration;
- application configuration such as
  `spring.jackson.default-property-inclusion=non_null` (or the YAML
  equivalent);
- a custom `ObjectMapper`/`Jackson2ObjectMapperBuilder` that calls
  `setSerializationInclusion(NON_NULL)` or
  `serializationInclusion(NON_NULL)`; and
- a `MappingJackson2HttpMessageConverter`, `ResponseBodyAdvice`, DTO mapper,
  or response wrapper that removes `null` properties before serialization.

`NON_EMPTY` and `NON_ABSENT` are also incompatible with the required API
contract in cases such as empty values and `Optional.empty()` respectively, so
they must be reviewed alongside `NON_NULL`.

## Recommended solution

Make inclusion of `null` fields the backend's default API serialization policy.
Use Jackson's `ALWAYS` inclusion at the narrowest scope that satisfies the API
contract:

1. If all JSON endpoints must preserve nullable fields, set the global Spring
   Boot property:

   ```properties
   spring.jackson.default-property-inclusion=always
   ```

   or:

   ```yaml
   spring:
     jackson:
       default-property-inclusion: always
   ```

   Remove or change any conflicting custom `ObjectMapper` configuration to
   `JsonInclude.Include.ALWAYS`.

2. If only a defined response family has this contract, annotate its response
   DTO (or a shared response base class) explicitly instead:

   ```java
   @JsonInclude(JsonInclude.Include.ALWAYS)
   public class ExampleResponse {
       private Long id;
       private String name;
       private String description;
   }
   ```

   An explicit DTO annotation is preferable when legacy endpoints intentionally
   omit absent fields and changing the application-wide policy would be a
   compatibility break.

3. Do not solve this by replacing `null` with an empty string, empty object, or
   sentinel value. That changes the response meaning. Do not enable Gson
   `serializeNulls()` in SpringLens: it only affects Gson's reflective
   serialization of Java objects and cannot restore a property already omitted
   by the server.

The backend owner must choose between option 1 and option 2 after inventorying
existing API contracts. The bug report says fields with `null` values should
*always* be included, so option 1 is the expected final policy unless an
existing endpoint has a documented exception.

## Backend implementation steps

1. Search the backend source and deployment configuration for the exclusion
   mechanisms listed above, including profile-specific configuration and
   shared Jackson configuration modules.
2. Add an explicit `ALWAYS` policy at the selected scope and remove any more
   specific `NON_NULL`/`NON_EMPTY` override that would win for the affected
   DTO.
3. Verify that every controller response path uses the configured Jackson HTTP
   message converter. If an endpoint returns a pre-built `Map`, JSON string, or
   response from another serializer, update that path separately or document it
   as outside the Jackson contract.
4. Check generated API documentation/OpenAPI schemas: nullable properties
   should remain declared as properties and marked nullable where appropriate.
   Update examples that previously implied omission.

## Regression tests

Add backend integration coverage using the real MVC/WebFlux serialization path,
not just a standalone `ObjectMapper` unit test:

1. Arrange a controller response where `description` is `null`; assert the
   parsed JSON has the `description` member and that its value is JSON `null`.
2. Assert non-null fields retain their current names, values, nesting, date
   formatting, and status codes.
3. Cover a nested DTO and a list containing an object with a nullable member.
4. If the project supports active profiles or custom mappers, run the same
   assertion under each configuration path that serves the endpoint.
5. Add a negative compatibility test only for endpoints that intentionally
   retain an explicit, documented non-default inclusion rule.

For SpringLens, add a focused client regression test with MockWebServer that
returns:

```json
{"id":1,"name":"Example","description":null}
```

Assert that `HttpClientService` returns a formatted JSON body whose parsed
tree still has `description` and whose value is `JsonNull`. Also verify that
the body survives endpoint response-cache persistence/restoration. This test
guards the client boundary; it does not and cannot correct server-side
serialization.

## Verification and acceptance criteria

- Directly call the backend with `curl` (or an integration test) and confirm
  the raw response contains `"description":null`.
- Send that endpoint through SpringLens and confirm the Response tab shows the
  same property after pretty-printing and after reopening cached response
  history.
- Run the backend's test suite plus `./gradlew test` in SpringLens after adding
  its boundary regression test.
- Confirm existing non-null response values, HTTP status/headers, and response
  size/truncation behaviour are unchanged.

## Out of scope for this repository

Changing a Jackson setting, controller, or DTO in the backend cannot be
implemented here because no backend source is present. Once the backend
repository or the relevant Jackson configuration is provided, this plan can be
applied there; the only SpringLens code change warranted is the optional
client-boundary regression test described above.
