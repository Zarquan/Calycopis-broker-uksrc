<!--
  <meta:header>
    <meta:licence>
      Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)

      This information is free software: you can redistribute it and/or modify
      it under the terms of the GNU General Public License as published by
      the Free Software Foundation, either version 3 of the License, or
      (at your option) any later version.

      This information is distributed in the hope that it will be useful,
      but WITHOUT ANY WARRANTY; without even the implied warranty of
      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
      GNU General Public License for more details.

      You should have received a copy of the GNU General Public License
      along with this program.  If not, see <http://www.gnu.org/licenses/>.
    </meta:licence>
  </meta:header>

  AIMetrics: [
      {
      "timestamp": "2026-10-20T11:57:26",
      "name": "@deepseek-ai/dsh",
      "version": "0.2.0-rc.2",
      "model": "deepseek-flash",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      }
    ]
-->

# Evaluation of RFC 9457 problem details for ExecutionBroker error responses

Date: 2026-10-20
Issue: [uksrc/Calycopis-broker#90](https://github.com/uksrc/Calycopis-broker/issues/90)
Parent: [uksrc/Calycopis-broker#89](https://github.com/uksrc/Calycopis-broker/issues/89)
External: Jira TRD-109 (linked from the issue)
Prototype: `Calycopis-broker` and `Calycopis-openapi`, branch `20261020-zrq-rfc9457-errors`

## 1. Summary and recommendation

The broker now has a working RFC 9457 implementation for the error paths that
issue #90 identified: invalid session updates, unknown sessions and offer sets,
ownership failures, authentication and authorisation failures, malformed
requests, and unexpected exceptions. The error body is the OpenAPI
`ProblemDetails` model and the media type is always
`application/problem+json`, including when the caller asks for XML or YAML.

The recommendation to the IVOA is:

1. **Adopt RFC 9457 for HTTP-level errors.** Use the standard members
   (`type`, `title`, `status`, `detail`, `instance`) and the registered
   `application/problem+json` media type.
2. **Define a problem type namespace.** The prototype uses
   `urn:ivoa:calycopis:error:<slug>` as a provisional namespace. A stable IVOA
   registry, or a rule for dereferenceable URIs, is needed before the schema
   becomes a standard.
3. **Decide the XML and YAML representation.** RFC 9457 registers only a JSON
   media type. The broker prototype is deliberately strict and returns JSON
   errors for every `Accept` value. If the IVOA requires errors in the same
   three formats as normal responses, that needs an explicit profile decision
   and extra implementation work (see section 6.2).
4. **Keep domain results separate from protocol errors.** An offer set with
   `result: NO` and a failed direct execution are domain outcomes, not HTTP
   protocol failures. The prototype keeps the direct `400` failed-session body
   by default and makes the RFC 9457 form opt-in.
5. **Use extension members for IVOA diagnostics.** The `messages` member is a
   natural extension for the existing `MessageItem` model. The prototype
   implements it and shows it in the examples.

## 2. Scope and method

The work covered three pieces:

- An evaluation of the current broker error behaviour and the IVOA API model.
- A prototype implementation in the broker and in the OpenAPI schema.
- Tests and a draft report for the IVOA.

The evaluation is based on the OpenAPI schema, the broker source, the IVOA
ExecutionBroker specification source, RFC 9457, and the prototype test results.
The prototype uses Spring Boot 4.1 and Spring Framework 7.

## 3. RFC 9457 in brief

RFC 9457 defines a JSON object for describing an HTTP error. The standard
members are:

- `type` — a URI reference identifying the problem type. If it is absent, the
  type defaults to `about:blank`.
- `title` — a short, human-readable summary of the problem type.
- `status` — the HTTP status code for this occurrence.
- `detail` — a human-readable explanation for this occurrence.
- `instance` — a URI reference identifying this occurrence, usually the request
  path.

The registered media type is `application/problem+json`. Extension members are
allowed, which is how IVOA-specific diagnostics can be added without changing
the standard members. The RFC replaces RFC 7807 and keeps the same object shape.

RFC 9457 does not define an XML or YAML media type. A service that uses other
formats has to decide how to represent problem details there, and should say so
in its own profile.

## 4. Current error behaviour

The inspection found the following behaviour before the change.

| Condition | Before |
|---|---|
| Unknown update type, path, value, or phase transition | Logged a warning, saved the unchanged session, and returned `200`. Seven TODOs pointed at #90. |
| Unknown session | Empty `404`. |
| Unknown offer set | Empty `404`. |
| Foreign session update | Empty `403`. |
| Unknown Docker stdout/stderr session | Empty `404`. |
| Rejected direct execution | `400` with an `AbstractExecutionSession` (the schema's *FailedExecutionResponse*). |
| Unauthenticated request | Spring Security's default `401`. |
| Authenticated but not an admin | Spring Security's default `403`. |
| Malformed request body | Spring Boot's default error response. |
| Unhandled exception | Spring Boot's default error response. |

The schema's five paths carried the comment `TODO Errors return error codes and
ErrorResponse`. Only the `/direct` `400` response was documented, as an
`AbstractExecutionSession`.

This is a real interoperability gap: a client cannot tell an invalid update from
a successful no-op, and the other errors have no documented body at all.

## 5. Evaluation

### 5.1 What problem details solve

The current API distinguishes errors by HTTP status alone. RFC 9457 adds a
machine-readable type, a stable title, occurrence detail, and a representation
of the request that failed. That is enough for a client to:

- distinguish "session not found" from "invalid update" without parsing prose;
- show a useful message while keeping the protocol error separate from the
  domain result;
- attach structured IVOA diagnostics through an extension member;
- treat the error body as part of the schema and generate client models for it.

For a standard as young as the ExecutionBroker schema, having the error model in
the same OpenAPI document is more valuable than leaving each implementation to
invent its own body.

### 5.2 Strict JSON versus the three-format convention

The ExecutionBroker API accepts and returns JSON, XML, and YAML. RFC 9457
registers only `application/problem+json`. The prototype resolves the tension by
being strict: every error is `application/problem+json`, even when the request
asks for XML or YAML. The normal responses keep their three-format negotiation.

The alternative is to honour `Accept` for errors and return problem-shaped
JSON, XML, or YAML. The prototype does **not** implement it, for these reasons:

- It needs an explicit content selector rather than relying on the generated
  controller `produces` list. A selector is manageable, but it is another
  behaviour to test and document.
- The schema has to list the extra error media types on every response. The
  schema pre-processor only merges `schemas`, not reusable `responses`, so the
  response maps are repeated per path.
- The generated `ProblemDetails` model has XML annotations, and the broker has
  Jackson 2 XML and YAML converters, but the XML converter's mapper does not
  have the same `JavaTimeModule` configuration as the YAML converter. The
  `messages` timestamps would need a converter configuration fix and XML/YAML
  round-trip tests.
- Spring Security's `401` and `403` are written before MVC, so honouring
  `Accept` there needs its own writer or an explicit decision to keep those two
  responses JSON-only.
- The test matrix roughly triples, and only `application/problem+json` is
  registered by the RFC, so the XML/YAML mapping has to be justified as an IVOA
  profile extension.

If the IVOA decides that error bodies must follow the normal three-format
convention, the work is a clear follow-up, not a redesign. The report records it
as an open question rather than pretending the strict choice is the only one.

### 5.3 Domain failures versus protocol errors

An offer set with `result: NO` is a successful HTTP response carrying a domain
result. A direct execution that is rejected is also a domain outcome, and the
schema deliberately documents its `400` body as a failed execution session. The
prototype keeps that distinction:

- Protocol and validation errors use RFC 9457.
- The direct-execution failure keeps its `AbstractExecutionSession` body by
  default.
- The flag
  `calycopis.broker.errors.direct-execution-problem-details` switches the direct
  failure to an RFC 9457 `execution-rejected` document for the evaluation.

The two forms were implemented and tested so the IVOA can compare them. The
problem form carries the failed session's `meta.messages` as the `messages`
extension and does not duplicate the session body.

### 5.4 Status codes and problem types

The prototype uses these mappings:

| Condition | Status | Type |
|---|---|---|
| Invalid update type, path, value, or transition | `400` | `urn:ivoa:calycopis:error:invalid-update` |
| Rejected direct execution, flag enabled | `400` | `urn:ivoa:calycopis:error:execution-rejected` |
| Unknown session, including Docker connectors | `404` | `urn:ivoa:calycopis:error:session-not-found` |
| Unknown offer set | `404` | `urn:ivoa:calycopis:error:offerset-not-found` |
| Foreign session update | `403` | `urn:ivoa:calycopis:error:access-denied` |
| Spring Security authentication failure | `401` | `urn:ivoa:calycopis:error:unauthorized` |
| Spring Security access denied | `403` | `urn:ivoa:calycopis:error:access-denied` |
| Unhandled exception | `500` | `urn:ivoa:calycopis:error:internal-error` |

Framework errors that the broker does not own, such as a malformed body or an
unknown URL, keep Spring's status and title and use `about:blank` as the type.
The prototype returns them as problem documents but does not invent a type URI
for each framework condition. The IVOA should decide whether `about:blank` is
acceptable for such errors or whether every error must have a registered type.

### 5.5 Extension members and messages

The schema's `ProblemDetails` has an optional `messages` extension member, an
array of the existing `MessageItem` model. It is populated for invalid updates
and for the opt-in direct rejection. This gives clients structured diagnostics
without parsing `detail`, and it reuses the message vocabulary the broker and
the schema already have.

The alternative would be a separate `errors` array or a VOTable-style error
block. The prototype recommends `messages` because it is already in the
ExecutionBroker model and appears throughout component metadata.

### 5.6 Security and privacy

The prototype:

- never returns a stack trace, exception class, SQL text, or credentials;
- returns a generic `500` detail and logs the exception on the server;
- truncates echoed update paths and values;
- uses the request path as `instance`, not the full URL;
- preserves `WWW-Authenticate` on `401` responses.

The IVOA profile should state the same rules. RFC 9457 has a security
considerations section, and an error body is an easy place to leak internals.

### 5.7 Client and schema impact

The OpenAPI schema now documents `ProblemDetails` and the error responses, so the
generated Java and Python clients have the model. The Python client's
`ApiException` path is unchanged; a client that wants the problem object can
parse the response body. The Java Spring package exposes `IvoaProblemDetails`
for clients that use the generated model.

The Spring server deliberately uses the schema-generated model on the wire and
converts Spring's internal `ProblemDetail` to it at the boundary. This keeps the
published schema authoritative and keeps `messages` typed. The alternative,
using Spring's `ProblemDetail` directly, is viable if the IVOA decides the error
model is exactly the RFC object with no schema-specific members; in that case a
contract test would need to keep the JSON aligned with the schema.

### 5.8 Jackson 2 and Jackson 3

The broker currently has both Jackson 2 and Jackson 3 on the classpath. Spring
Boot 4.1's default JSON handling and its RFC 9457 mixin target Jackson 3, while
the generated models and the custom YAML/XML converters use Jackson 2. The
generated `ProblemDetails` model avoids depending on which converter wins: it is
a plain bean with Jackson 2 annotations, and the Spring `ProblemDetail` is only
an internal representation. The error body has no nested `properties` wrapper.

A future migration to Spring Boot 4 + Jackson 3 codegen would remove the dual
stack. It is a separate change: it needs `useSpringBoot4` and `useJackson3` in the
codegen POM, Jackson 3 XML/YAML dependencies, a replacement for the Jackson 2
`YamlConverter`, and a Springdoc compatibility check. The recommendation is to
keep `IvoaProblemDetails` on the wire after that migration as well.

### 5.9 Backward compatibility and versioning

The error status codes are unchanged. The 404/403 bodies change from empty to a
problem document, which is additive for clients that only look at the status.
The direct `400` body is unchanged unless the evaluation flag is enabled. The
schema and package versions move from `1.0.8` to `1.0.9`, which is an additive
minor change.

The one compatibility caveat is the strict media type: a client that only
accepts XML and treats a non-XML error body as fatal will now receive JSON for an
error. That is the deliberate prototype choice recorded in section 5.2.

## 6. Prototype implementation

### 6.1 Schema

`Calycopis-openapi` adds:

- a `ProblemDetails` schema with `type`, `title`, `status`, `detail`, `instance`,
  and the `messages` extension, `additionalProperties: true`, and an XML root
  name;
- `400`/`403`/`404`/`default` responses using `application/problem+json` on the
  affected paths;
- both the failed-session and the problem content types on the `/direct` `400`
  response, with a description of the evaluation flag.

The schema and package versions are `1.0.9`, `1.0.9-SNAPSHOT`, and
`1.0.9.dev0`.

### 6.2 Broker

The broker adds:

- `InvalidExecutionSessionUpdateException` in the engine, thrown by the seven
  update-handler branches that previously logged and continued;
- a `ProblemType` enum and a `ProblemDetailsFactory` that build the generated
  `IvoaProblemDetails`;
- a `ProblemDetailsAdvice` that maps the engine and web exceptions, converts
  framework `ProblemDetail` bodies, and forces `application/problem+json`;
- a `BrokerProblemException` for the delegates and controllers;
- Spring Security entry point and access-denied handlers so `401`/`403` use the
  same body;
- the direct-execution flag and its settings binding;
- the `calycopis.broker.errors` configuration block, defaulting the flag to
  `false`.

The update handler now validates before mutating. The regression risk in the
offer-accept loop (rejecting sibling sessions) was handled by only rejecting
siblings that are still `OFFERED`; a direct update from any other phase is an
error.

The prototype also fixed two pre-existing failure-path bugs that the direct
response comparison exposed. A failed session has no UUID, so its metadata URL
is now built only when a UUID is present, and it may have no executable or
compute resource, so those members are now added only when present. Without
these guards, the documented `400` failed-session body produced a `500`.

### 6.3 Tests

The Java tests cover the advice, the invalid-update exception paths, both
direct-response modes, and the settings binding. The Python tests cover the raw
HTTP responses for 400/401/403/404, the strict media type, the `messages`
extension, the unchanged session after a rejected update, and the default direct
body on the mock platform.

## 7. Example exchanges

An unknown session:

```http
GET /sessions/00000000-0000-0000-0000-000000000000 HTTP/1.1
Accept: application/xml
```

```http
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{
  "type": "urn:ivoa:calycopis:error:session-not-found",
  "title": "Execution session not found",
  "status": 404,
  "detail": "No execution session with UUID [00000000-0000-0000-0000-000000000000].",
  "instance": "/sessions/00000000-0000-0000-0000-000000000000"
}
```

An invalid update:

```json
{
  "kind": "uri:enum-value-update",
  "path": "/bogus",
  "value": "CANCELLED"
}
```

```http
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json

{
  "type": "urn:ivoa:calycopis:error:invalid-update",
  "title": "Invalid execution session update",
  "status": 400,
  "detail": "Unsupported update path [/bogus].",
  "instance": "/sessions/6f1c...",
  "messages": [
    {
      "kind": "urn:ivoa:calycopis:message:invalid-update",
      "level": "ERROR",
      "template": "Invalid execution session update",
      "values": {
        "path": "/bogus"
      },
      "message": "Unsupported update path [/bogus]."
    }
  ]
}
```

The `messages` member is the IVOA extension; the standard members are unchanged.

## 8. Test results

- Java unit tests: 46 tests, 0 failures, 0 errors. The new tests are
  `ProblemDetailsAdviceTest`, `InvalidExecutionSessionUpdateExceptionTest`,
  `DirectApiDelegateImplTest`, and `ErrorResponseSettingsImplTest`.
- OpenAPI schema, Java Spring package, Java client, and Python client builds:
  successful; `IvoaProblemDetails` is generated and the generated controller
  `produces` list includes `application/problem+json`.
- Python integration tests, against a broker running the `mock` profile with
  PostgreSQL:
  - `tests/python/any`: 47 passed, including the 11 new problem details tests.
  - `tests/python/mock/test_direct_problem_details.py`: 1 passed.
  - `tests/python/mock/test_mock_validators.py`, `test_mock_direct_execution.py`,
    and `test_direct_problem_details.py`: 46 passed, 1 failed. The failure is a
    pre-existing mock `JupyterNotebook` validation case whose offer-set parser
    path returns a null executable result; this work does not touch that path.
- The full `any` suite covers the new tests alongside the existing identity,
  labels, offer-set, start-window, costs, and profiling tests.

## 9. Recommendation to the IVOA

1. Add an error-response section to the ExecutionBroker specification and
   reference RFC 9457.
2. Define a problem type namespace and require either a registered type URI or
   a documented `about:blank` exception for framework errors.
3. Decide whether errors follow the normal XML/YAML content negotiation or are
   always `application/problem+json`. The strict prototype is a starting point,
   not a substitute for that decision.
4. Recommend the `messages` extension for structured diagnostics, reusing the
   existing `MessageItem` model.
5. Keep domain outcomes (`result: NO`, failed execution session) separate from
   protocol errors, and document the distinction.
6. Require the security and privacy rules in section 5.6.

## 10. Open questions for the IVOA

- Should every error have a registered type URI, or is `about:blank` acceptable
  for framework-generated errors?
- Should errors be available in XML and YAML, and if so, what media types and
  root elements should they use?
- Should the `messages` member be part of the standard error model or a
  Calycopis extension?
- Should a rejected direct execution be a problem document or a domain result?
  The prototype supports both for comparison.
- Who owns the problem type registry, and how are new types added?

## 11. Draft report to the IVOA

The text below is ready to post as an issue or comment in
[ivoa-std/ExecutionBroker](https://github.com/ivoa-std/ExecutionBroker), or to
use as the basis for an Interop report. It is not posted by this change.

Title: **RFC 9457 problem details for ExecutionBroker error responses — evaluation and prototype**

```markdown
The Calycopis ExecutionBroker prototype has evaluated RFC 9457 problem details
for its error responses, following uksrc/Calycopis-broker#90.

The prototype:

- adds an OpenAPI `ProblemDetails` schema and documents the error responses on
  the ExecutionBroker paths;
- returns `application/problem+json` for invalid updates, unknown sessions and
  offer sets, ownership failures, authentication and authorisation failures,
  malformed requests, and unexpected exceptions;
- adds a `messages` extension reusing the existing `MessageItem` model;
- keeps the existing domain outcomes separate, including the failed direct
  execution body, with an opt-in RFC 9457 form for comparison;
- has Java and Python tests for the error paths.

The evaluation and examples are in the Calycopis-broker report
`docs/rfc9457-error-responses.md`.

Questions for the IVOA:

1. Should the ExecutionBroker profile require RFC 9457 for HTTP-level errors?
2. Should every error have a registered type URI, or is `about:blank` acceptable
   for framework errors?
3. Should errors follow the normal JSON/XML/YAML content negotiation, or is
   `application/problem+json` the required error type?
4. Should the `messages` extension be part of the standard error model?
5. Should a rejected direct execution be a problem document or a domain result?

The prototype is deliberately strict: it returns `application/problem+json`
even when the request asks for XML or YAML. The report records the negotiated
alternative and its implementation cost for discussion.

AIMetrics: [
    {
    "interval": "2026-10-20T10:43:00/2026-10-20T12:30:00",
    "name": "@deepseek-ai/dsh",
    "version": "0.2.0-rc.2",
    "model": "deepseek-flash",
    "contribution": {
      "value": 100,
      "units": "%"
      }
    }
  ]
```

## 12. References

- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [IVOA ExecutionBroker specification](https://github.com/ivoa-std/ExecutionBroker)
- [uksrc/Calycopis-broker#90](https://github.com/uksrc/Calycopis-broker/issues/90)
- [uksrc/Calycopis-openapi](https://github.com/uksrc/Calycopis-openapi)
