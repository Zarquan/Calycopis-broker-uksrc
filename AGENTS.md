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
      "timestamp": "2026-08-26T13:00:15",
      "name": "Cursor CLI",
      "version": "2026.02.13-41ac335",
      "model": "Claude 4.6 Opus (Thinking)",
      "contribution": {
        "value": 100,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-08-27T11:22:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-05T12:41:19",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-11T11:50:08",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-14T06:00:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 10,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-14T06:40:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-14T11:11:41",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-14T12:30:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      },
      {
      "timestamp": "2026-09-14T13:00:00",
      "name": "@deepseek-ai/dsh",
      "version": "0.1.1-rc.2",
      "model": "deepseek-v4-flash",
      "contribution": {
        "value": 1,
        "units": "%"
        }
      }
    ]
-->

# Calycopis - Execution Broker
This project implements the IVOA Execution Broker service as a Spring Boot web application.

## High-level overview

* The Execution Broker interface is intended to provide an abstract interface that provides a common API for a range of different execution platforms, OpenStack, Docker, Kubernetes, Slurm, Panda etc.
* An Execution Broker service takes an execution request (what to run + required resources) and returns a set of offers for execution sessions that the platform can execute.
* When a user accepts an offer, the Execution Broker prepares and executes the task on the platform, providing a common abstract interface to monitor the status and get access to the execution.
* The service is implemented as a Spring Boot application (`AmbleckApplication`) with a `mock` platform (no real execution) and a `docker` platform (runs containers via the local Docker/Podman service).

### Main concepts:

 * Offer sets: Offers for execution sessions that the broker can provide for a given request (OfferSetRequest → OfferSetResponse).
 * Execution sessions: Concrete instance of an execution session.
 * Resources: Executables, compute, storage, volumes, and data resources (S3, Rucio, IVOA, SKAO).
 * Lifecycle: Phases and schedules for components and sessions.
 * Costs and metrics: Estimated cost and metric ranges advertised per session and per compute resource.
 * Identity: Local and admin identities used for authentication.

### API surface – endpoints & behavior

The API is defined by the OpenAPI schema in the Calycopis-openapi project
(see [OpenAPI schema](#openapi-schema) below). The web layer exposes the
following endpoints:

 * `POST /requests` (ExecutionRequest)
   * Request body: ExecutionRequest in application/json, application/xml, or application/yaml.
   * Responses:
     * 303 See Other with required Location header pointing to /offersets/{uuid}.
     * 200 OK with OfferSetResponse directly.
   * Implications:
     * Clients must be prepared for both immediate data (200) and async-style redirect (303) patterns.
     * Good for implementations that might be slow and prefer to hand back a polling URL.
 * `POST /direct` (DirectExecution)
   * Request body: ExecutionRequest in the same three media types.
   * Responses:
     * 303 See Other with a Location header pointing to the created execution session.
     * 200 OK with AbstractExecutionSession.
     * 400 Bad Request with AbstractExecutionSession describing why the request was rejected.
   * Skips the offer process: executes the request directly and returns the session.
 * `GET /offersets/{uuid}` (OfferSetSelect)
   * Path param uuid with uuid format.
   * Returns OfferSetResponse in the same three media types.
   * Represents a stable, queryable resource for a particular offer set.
 * `GET /sessions/{uuid}` (SessionSelect)
   * Retrieves an execution session by UUID.
   * Response type is AbstractExecutionSession, a oneOf of SimpleExecutionSession and ScheduledExecutionSession.
   * Design choice: server returns polymorphic session; clients must look at the kind discriminator.
 * `POST /sessions/{uuid}` (SessionUpdate)
   * Path param uuid is a session identifier.
   * Request body: AbstractUpdate (discriminated union of specific update types).
   * Response: AbstractExecutionSession.
   * Pattern: "patch by update-command" rather than JSON Patch – the kind of update and path decide what to change.
 * `POST /admin/identities` (AdminIdentityController)
   * Creates an admin identity, returning JSON (content-type application/json).
   * Part of the local authentication support.

### Security

 * The schema itself does not define securitySchemes; it is transport/auth agnostic.
 * The broker implements local authentication in `spring/security/`:
   * `SecurityConfig` – Spring Security wiring.
   * `LocalAuthenticationProvider` / `AdminAuthenticationProvider` – authentication providers for local and admin identities.
   * `IdentityResolver` – resolves the current identity from the request.
 * Identity entities and factories live in `engine/entities/identity/` with the Spring wiring in `spring/identity/`.
 * Project plan includes support for OAuth2 / bearer tokens.

### Data model – core building blocks

 * Polymorphism via discriminators
   * AbstractExecutable → DockerContainer, SingularityContainer, JupyterNotebook (URI kind values as mapping keys).
   * AbstractComputeResource → SimpleComputeResource.
   * AbstractStorageResource → SimpleStorageResource.
   * AbstractVolumeMount → SimpleVolumeMount.
   * AbstractDataResource → SimpleDataResource, S3DataResource, RucioDataResource, IvoaDataResource, SkaoDataResource.
   * AbstractExecutionSession → SimpleExecutionSession, ScheduledExecutionSession.
   * AbstractOption and AbstractUpdate also use discriminators for option/update variants.
 * Base component model
   * AbstractComponent supplies kind (URI) and meta (ComponentMetadata).
   * ComponentMetadata adds identifiers, human text, timestamps, messages, and options.
   * LifecycleComponent embeds phase and schedule for components with state.
 * Execution composition
   * ExecutionRequestComponents and SimpleExecutionComponents share the same structure:
     * executable (AbstractExecutable),
     * compute (AbstractComputeResource),
     * storage (AbstractStorageResourceList),
     * volumes (AbstractVolumeMountList),
     * data (AbstractDataResourceList).
   * This gives a uniform way to describe what an execution needs vs. what an execution session actually has.
 * Offer sets
   * OfferSetRequest = ExecutionRequestComponents + optional RequestedScheduleBlock.
   * OfferSetResponse = AbstractComponent + fields:
     * result (YES/NO) with semantics about whether the service can handle the request.
     * Optional description.
     * offers: array of AbstractExecutionSession (polymorphic sessions).

### Scheduling & lifecycle

 * Lifecycle states
   * LifecyclePhase and SimpleExecutionSessionPhase give rich state machines (INITIAL/WAITING/PREPARING/AVAILABLE/RUNNING/RELEASING/COMPLETED/FAILED/CANCELLED etc.).
   * SimpleExecutionSessionPhase includes extra states (e.g. OFFERED, ACCEPTED, REJECTED, EXPIRED) on top of generic lifecycle phases to support the offer lifecycle.
 * Lifecycle vs schedule structures
   * LifecycleSchedule and ScheduledExecutionSchedule both have preparing, available, releasing fields referencing _StartDuration types.

### Executables & runtime environment

 * DockerContainer
   * Includes image (DockerImageSpec), privileged, entrypoint, environment (NameValueMap), and network (DockerNetworkSpec).
   * DockerImageSpec includes locations array, digest, and platform (DockerPlatformSpec).
   * DockerNetworkSpec → ports array → DockerNetworkPort (with internal and external ports, protocol, access flag, path).
 * Other executable types
   * SingularityContainer – simple location URL.
   * JupyterNotebook – location URL (further work is needed to support different notebook references).
   * All reuse AbstractExecutable → consistent kind and lifecycle handling.

### Compute, storage, volumes, and data

 * Compute
   * SimpleComputeResource with nested SimpleComputeCores (min/max) and SimpleComputeMemory (min/max GiB), plus volumes.
   * This gives brokers enough flexibility to negotiate between min/max resources.

 * Storage & volumes
   * SimpleStorageResource with SimpleStorageSize (min/max GiB) and optional data list.
   * SimpleVolumeMount uses path, mode (READONLY / READWRITE), cardinality (SINGLE / CONTAINER), resources list.
   * The API distinguishes between where data lives (AbstractDataResource) and how it is presented to the executable's filesystem (volume mounts).

 * Data resources
   * AbstractDataResource includes storage and kind discriminator, branching into:
     * SimpleDataResource – single downloadable URL.
     * S3DataResource – endpoint/template/bucket/object for object storage.
     * RucioDataResource – via RucioDataResourceBlock with endpoint/scope/object/type.
     * IvoaDataResource – IVOA metadata via DID, ObsCore, DataLink.
     * SkaoDataResource – SKAO-specific metadata extending IvoaDataResource with namespace/objectname/objecttype/datasize/checksum/replicas.
   * The design is intended to be extensible; new data backends can be added via new kind URIs.

### Costs and metrics

 * The broker advertises estimated costs and metrics for sessions and compute
   resources, defined in the `calycopis.broker.costs-and-metrics` section of
   `application.yaml` (e.g. monetary cost, energy, carbon, compute performance, IO throughput).
 * Cost and metric entities live in `engine/entities/cost/` and `engine/entities/metric/`.
 * A demonstration of comparing offers across multiple brokers with different
   cost/metric profiles is provided in the `demo/` directory.

### Messages, options, and updates

 * Messages
   * MessageItem models log/diagnostic messages (kind/time/level/template/values/message) aligned with Message Templates standard.
   * Appears within ComponentMetadata.messages, so every component may carry rich diagnostics.

 * Options API
   * AbstractOption (discriminated on kind) allows the service to present configurable options for components, each targeting a path.
   * Flavours:
     * StringValueOption with optional regex pattern.
     * EnumValueOption with allowed values.
     * IntegerValueOption/IntegerDeltaOption with min/max and units.
   * Pattern: server advertises what can be tuned; client chooses values within constraints.

 * Updates API
   * AbstractUpdate mirrors AbstractOption for making actual changes to those targets.
   * Flavours:
     * StringValueUpdate, EnumValueUpdate (string payloads).
     * IntegerValueUpdate, IntegerDeltaUpdate (numeric payloads with units).
   * Combined with `POST /sessions/{uuid}`: forms a command-style patch system – path+kind+value/delta.

### Media types and representation details

 * All the endpoints support application/json, application/xml, and application/yaml.
 * URI formats
   * Many fields (kind, url, ivoid) use format: uri, aligning with general web semantics and IVOA identifiers.

## OpenAPI schema

 * The OpenAPI schema for the Execution Broker is published in the `https://github.com/ivoa/Calycopis-openapi/` project on GitHub (formerly Calycopis-schema).
 * There is a local copy of the Calycopis-openapi project available at `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/`.
 * The Execution Broker API is defined in `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/schema/v1.0/execution-broker.yaml`.

 * The Calycopis-broker project depends on the `net.ivoa.calycopis:calycopis-openapi-spring` package, which contains Spring Boot classes generated from the schema.
 * The version is taken from the `openapi.spring.version` property of `config.yaml` (currently `1.0.7-SNAPSHOT`) and passed to Maven through the `CALYCOPIS_OPENAPI_SPRING_VERSION` environment variable (see [Version management](#version-management)).
 * The Maven project for the `calycopis-openapi-spring` package is available at `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/codegen/java/spring`.
 * The source code for the generated Spring Boot classes is available at `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/codegen/java/spring/target/generated-sources/openapi`.

 * The Calycopis-broker project uses Python client classes generated from the schema for testing.
 * The Python project for the Python client classes (`calycopis_openapi_client`) is generated into `/Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/codegen/python/client/target/`.

## Package architecture

The Java source code is split into two top-level package trees under
`net.ivoa.calycopis.broker`:

### `engine/` — Framework-neutral domain logic

Contains interfaces, JPA entity classes, factory interfaces, validator interfaces,
and processing logic. Uses `jakarta.persistence.*` (Jakarta EE standard) annotations
for persistence but has **zero** Spring Framework imports (`org.springframework.*`).

 * `engine/entities/` — Domain interfaces and JPA entity classes.
   * `engine/entities/component/` — Base types: `Component`, `ComponentEntity`, `LifecycleComponent`, `LifecycleComponentEntity`.
   * `engine/entities/<concept>/` — Each resource type (compute, storage, data, executable, volume, cost, metric, message, identity, session, offerset).
   * `engine/entities/<concept>/simple/` — Schema-type tier (e.g. `SimpleComputeResource`).
   * `engine/entities/<concept>/simple/<platform>/` — Platform-specific entity subclasses (e.g. `mock/`, `docker/`).
 * `engine/functional/` — Cross-cutting functional logic.
   * `engine/functional/factory/` — `FactoryBase` / `FactoryBaseImpl`.
   * `engine/functional/platform/` — `Platform` interface and per-platform interfaces (`MockPlatform`, `DockerPlatform`).
   * `engine/functional/processing/` — Processing loop and request entities:
     * `ProcessingRequest` / `ProcessingRequestEntity` / `ProcessingService` / `ProcessingServiceImpl`.
     * `processing/action/` — Processing actions (`ProcessingAction`, `SimpleSleepAction`) and mock actions (`action/mock/`).
     * `processing/component/` — Per-component processing requests (`PrepareComponentRequestEntity`, `MonitorComponentRequestEntity`, `ReleaseComponentRequestEntity`, `CancelComponentRequestEntity`, `FailComponentRequestEntity`).
     * `processing/session/` — Per-session processing requests (`PrepareSessionRequestEntity`, `ReleaseSessionRequestEntity`, `CancelSessionRequestEntity`, `FailSessionRequestEntity`, `UpdateSessionRequestEntity`).
   * `engine/functional/validator/` — Validator base interfaces and `ValidatorFactory`.
   * `engine/functional/booking/` — Resource booking logic.
 * `engine/query/` — Query handler abstractions (`AbstractQueryHandler`).
 * `engine/util/` — Utilities (`URIBuilder`, `ListWrapper`, etc.).

### `spring/` — Spring-specific implementations

Contains all classes that depend on the Spring Framework (`org.springframework.*`).
These are the concrete wiring that connects the domain logic to Spring Boot's
dependency injection, transaction management, scheduling, and web layer.

 * `spring/webapp/` — API delegate implementations (`RequestsApiDelegateImpl`, `DirectApiDelegateImpl`, `OffersetsApiDelegateImpl`, `SessionsApiDelegateImpl`, `AdminIdentityController`), the main `@SpringBootApplication` class (`AmbleckApplication`), `BaseDelegateImpl`, `YamlConverter`, and `ServletInitializer`.
 * `spring/jpa/` — All Spring Data `@Repository` interfaces (centralised in one package rather than co-located with entities).
 * `spring/platform/mock/` — `MockPlatformImpl` (`@Component`, `@Profile("mock")`) and `MockPlatformSettingsImpl`.
 * `spring/platform/docker/` — `DockerPlatformImpl` (`@Component`, `@Profile("docker")`).
 * `spring/processing/` — `SpringProcessingServiceImpl` with `@Scheduled` loop.
 * `spring/booking/` — Booking service Spring `@Component` implementations.
 * `spring/query/` — Query service Spring `@Component` implementations.
 * `spring/security/` — Spring Security configuration and authentication providers.
 * `spring/identity/` — Spring wiring for identity entities.

### Design rationale

Spring Framework annotations are isolated in `spring/` so that the domain logic in
`engine/` could, in principle, be reused with a different DI/web framework. JPA
annotations remain in `engine/` because they are part of the Jakarta EE standard and
not Spring-specific.

## Design patterns

### Three-tier Entity/Factory/Validator pattern

Every domain concept (compute, storage, data, executable, volume, session) follows a
three-tier inheritance pattern with consistent file roles at each tier. The entity
interfaces, JPA entity classes, factory interfaces, and validator interfaces live in
`engine/entities/<concept>/`. The Spring-specific wiring (repositories in
`spring/jpa/`, `@Component` factories, `@Component` validators) lives in the platform
implementation packages.

```
engine/entities/<concept>/                 ← Tier 1: Abstract base
engine/entities/<concept>/simple/          ← Tier 2: Schema type (e.g. SimpleComputeResource)
engine/entities/<concept>/simple/<platform>/ ← Tier 3: Platform-specific entity subclass
spring/jpa/                                ← Repositories for all entities (centralised)
```

**Tier 1 – Abstract base** (`engine/entities/<concept>/`)
Defines the polymorphic root for a family of types. Files:
| File | Package | Role |
|------|---------|------|
| `Abstract<Concept>.java` | `engine/entities/<concept>/` | Public interface extending `LifecycleComponent`. Defines `WEBAPP_PATH` and domain-specific getters. |
| `Abstract<Concept>Entity.java` | `engine/entities/<concept>/` | JPA `@Entity` with `@Inheritance(JOINED)`. Abstract base class holding the session reference and common persistence fields. |
| `Abstract<Concept>EntityFactory.java` | `engine/entities/<concept>/` | Factory interface for creating new entities from validation results and for selecting an existing one based on its identifier. |
| `Abstract<Concept>EntityFactoryImpl.java` | `engine/entities/<concept>/` | Abstract factory implementation (extends `FactoryBaseImpl`). |
| `Abstract<Concept>Validator.java` | `engine/entities/<concept>/` | Validator interface extending `Validator<IvoaType, EntityType>`. Contains a nested `Result` interface and `ResultBean` class. |
| `Abstract<Concept>ValidatorImpl.java` | `engine/entities/<concept>/` | Abstract validator implementation (extends `AbstractValidatorImpl`). |
| `Abstract<Concept>ValidatorFactory.java` | `engine/entities/<concept>/` | Combines `Validator` and `ValidatorFactory` — acts as a chain-of-responsibility dispatcher. |
| `Abstract<Concept>ValidatorFactoryImpl.java` | `engine/entities/<concept>/` | Iterates registered validators until one returns `ACCEPTED` or `FAILED`. |
| `Spring<Concept>EntityRepository.java` | `spring/jpa/` | Spring `@Repository` interface extending `SpringAbstractEntityRepository`. |

**Tier 2 – Schema type** (`engine/entities/<concept>/simple/`)
A concrete type from the OpenAPI schema (e.g. `SimpleComputeResource`). Files:
| File | Package | Role |
|------|---------|------|
| `Simple<Concept>.java` | `engine/entities/<concept>/simple/` | Interface extending `Abstract<Concept>`. Defines `TYPE_DISCRIMINATOR` URI and type-specific getters. |
| `Simple<Concept>Entity.java` | `engine/entities/<concept>/simple/` | JPA `@Entity` with `@DiscriminatorValue`. Adds type-specific `@Column` fields. Still abstract — leaves platform-specific methods (like `getPrepareAction`) unimplemented. |
| `Simple<Concept>EntityFactory.java` | `engine/entities/<concept>/simple/` | Factory interface extending the abstract factory. |
| `Simple<Concept>EntityFactoryImpl.java` | `engine/entities/<concept>/simple/` | Abstract factory implementation. |
| `Simple<Concept>Validator.java` | `engine/entities/<concept>/simple/` | Validator interface extending the abstract validator. |
| `Simple<Concept>ValidatorImpl.java` | `engine/entities/<concept>/simple/` | Validates the specific Ivoa type using exact class matching (`getClass() ==`, not `instanceof`). Creates a `ResultBean` with a `build()` method that delegates to the entity factory. |

**Tier 3 – Platform implementation** (`engine/entities/<concept>/simple/<platform>/`)
A concrete, instantiable implementation for a specific platform (e.g. `mock`, `docker`).
This is the tier where entity classes become non-abstract. The entity subclass lives
in `engine/entities/` while the Spring `@Component` factory and validator implementations
that wire it into the application context also live here. Repositories are **not** created
per platform — each entity uses the central `Spring<Concept>EntityRepository` in `spring/jpa/`.
| File | Package | Role |
|------|---------|------|
| `<Platform><Concept>.java` | `engine/entities/<concept>/simple/<platform>/` | Interface extending `Simple<Concept>`. |
| `<Platform><Concept>Entity.java` | `engine/entities/<concept>/simple/<platform>/` | Concrete JPA `@Entity`. Implements platform-specific behavior (e.g. `getPrepareAction`). |
| `<Platform><Concept>EntityFactory.java` | `engine/entities/<concept>/simple/<platform>/` | Factory interface with a `create()` method taking the platform-specific validator result. |
| `<Platform><Concept>EntityFactoryImpl.java` | `engine/entities/<concept>/simple/<platform>/` | Concrete `@Component` factory. Receives the repository via `@Autowired` and calls `repository.save()`. |
| `<Platform><Concept>Validator.java` | `engine/entities/<concept>/simple/<platform>/` | Validator interface extending the schema-type validator. |
| `<Platform><Concept>ValidatorImpl.java` | `engine/entities/<concept>/simple/<platform>/` | Concrete `@Component` validator. Registered with the `ValidatorFactory` at startup. |

### How the pieces connect at runtime
1. **Request arrives** → `OfferSetRequestParser` extracts each component (executable, compute, storage, etc.)
2. **Validation** → For each component, the parser calls the corresponding `ValidatorFactory.validate()`. The factory iterates its registered validators (chain-of-responsibility). Each
validator uses exact class matching to decide if it should handle the request. It returns `CONTINUE` the factory should continue to the next validator, `ACCEPTED`
if the component was validated and accepted, and `FAILED` if the component failed the validation.
3. **Result accumulation** → Accepted validators produce a `Result` object (containing the validated Ivoa bean) and add it to the `OfferSetRequestParserContext`.
4. **Entity creation** → When an offer is built, `Result.build(session, offer)` is called, which delegates to the entity factory's `create()` method. The factory constructs the entity
and persists it via the repository.
5. **Serialization** → Entities implement `makeBean(URIBuilder)` to convert back to Ivoa beans for the API response.

### Adding a new platform implementation
To add a new platform (e.g. `docker`):
1. Create the platform interface in `engine/functional/platform/docker/` (e.g. `DockerPlatform.java`).
2. Create the Spring `@Component` implementation in `spring/platform/docker/` (e.g. `DockerPlatformImpl.java`), following the mock pattern. The implementation starts as a copy of the mock platform, using the same validators and factories.
3. Create the platform-specific entity subclass package at `engine/entities/<concept>/simple/docker/` (e.g. `engine/entities/compute/simple/docker/`).
4. Create the 6 files following the mock pattern: interface, entity, factory interface, factory impl, validator interface, validator impl. (Repositories stay centralised in `spring/jpa/`.)
5. The entity class is the only non-trivial one — implement `getPrepareAction()` with real logic to connect to the platform and run a container.
6. The factory impl is a `@Component` that receives the repository via `@Autowired` and calls `repository.save()`.
7. The validator impl is a `@Component` that registers itself with the `ValidatorFactory` at startup.
8. Update the `DockerPlatformImpl` in `spring/platform/docker/` to register and use the new classes.

### Adding a new resource type
To add an entirely new resource type (e.g. `gpu`):
1. Create the abstract tier package at `engine/entities/gpu/` with the abstract tier files following the compute pattern.
2. Create `engine/entities/gpu/simple/` with the schema-type tier files.
3. Create `engine/entities/gpu/simple/mock/` with the platform-specific entity, factory, and validator files.
4. Add a Spring `@Repository` interface to `spring/jpa/` for the new entity.
5. Add a corresponding `ValidatorFactory` and wire it into `OfferSetRequestParser`.
6. Add the new component to `ExecutionRequestComponents` / `SimpleExecutionComponents` in the schema.

## Coding conventions

 * **No binary files in the source tree.** Do not add compiled artefacts, wheel files (`.whl`), JAR files, container images, or any other binary blobs to the version-controlled source tree. Build outputs should be written to a dedicated `build/` or `target/` directory that is excluded via `.gitignore`. If a binary file is needed as a build input (e.g. a wheel copied into a Docker build context), place it in a `build/` sub-directory with a `.gitignore` that excludes its contents.

 * **Do not suppress errors.** Never redirect output to `/dev/null`, pipe stderr to `/dev/null`, or use `|| true` to hide failures in build scripts, Dockerfiles, or CI pipelines. If a command might legitimately fail (e.g. an optional tool that may not be available), handle the failure explicitly with a clear comment explaining why it is acceptable to continue, and ensure the error output remains visible for debugging.

 * Detailed rules for handling file headers and unexpected behaviour are defined in the `agents/rules/` directory:
   * [`agents/rules/licence-header.mdc`](agents/rules/licence-header.mdc) — GPL licence header that must be added to all new source files.
   * [`agents/rules/copyright-year.mdc`](agents/rules/copyright-year.mdc) — Copyright year in the licence header must be updated to the current year when a file is modified.
   * [`agents/rules/ai-metrics.mdc`](agents/rules/ai-metrics.mdc) — AIMetrics block must be added or updated in file headers for all created or modified files, and appended to the end of any git commit messages created by the agent.
   * [`agents/rules/unexpected-behaviour.mdc`](agents/rules/unexpected-behaviour.mdc) — Ask the user before working around unexpected or unusual behaviour from an API or service.

 * Every git commit message created by an agent must end with an AIMetrics block.
 * The `name`, `version`, and `model` values in a commit message block are the same as the values used for a file header block (see `agents/rules/ai-metrics.mdc`).
 * The `contribution.value` is an estimate of how much of the changes in the commit were contributed by the agent. If all of the changes were contributed by the agent, set the value to `100`.
 * A commit message block uses `interval` — an ISO 8601 interval covering the current session (e.g. `2026-02-14T15:30:00/2026-03-14T05:00:00`) — instead of the `timestamp` used in file headers.
 * The format for an AIMetrics block at the end of a commit message:

    ```
    AIMetrics: [
        {
        "interval": "<ISO 8601 interval covering the current session, e.g. 2026-02-14T15:30:00/2026-03-14T05:00:00>",
        "name": "<agent/tool name from the current session>",
        "version": "<agent/tool version from the current session>",
        "model": "<model identifier from the current session>",
        "contribution": {
          "value": <percentage>,
          "units": "%"
          }
        }
      ]
    ```

 * The implementation is based on the [Spring Boot](https://spring.io/projects/spring-boot) framework.
 * Where possible generic [Java Persistence API](https://en.wikipedia.org/wiki/Jakarta_Persistence) (JPA) annotations should be used rather than Spring framework specific ones, to make it easier to port the project to a different framework in the future.
 * Avoid fragile patterns. If your proposed solution requires workarounds such as `@Transient` fields
   with `initEntity()` helpers, static singletons, `ApplicationContextAware` lookups, or any other
   mechanism that bypasses the normal dependency injection and entity lifecycle, **stop and ask the user
   for confirmation before implementing it**. These patterns are fragile because they rely on specific
   call paths and break when entities are loaded indirectly (e.g. via a Hibernate reference from another
   entity). There is usually a cleaner alternative, such as passing the required dependency through a
   method parameter.
 * The code style should favour clarity over brevity.
 * **Do not define constants that replicate existing enum values.** When an
   OpenAPI schema or generated model already provides an enum for a value
   (e.g. `IvoaSimpleSessionConnector.StatusEnum.PREPARING`), use the enum values
   directly rather than declaring local `String` or constant aliases for them.
   Replicated constants duplicate the schema's vocabulary, drift out of sync
   when the schema changes, and add nothing over the enum itself.
 * Do NOT use the `?:` ternary conditional operator. Always use an expanded `if/else` block instead.

    For example, this:
    ```
    imageName = (locations != null && !locations.isEmpty())
        ? locations.get(0)
        : null;
    ```
    should be written as:
    ```
    if (locations != null && !locations.isEmpty())
        {
        imageName = locations.get(0);
        }
    else {
        imageName = null;
        }
    ```

    Similarly, this:
    ```
    x=y>27?y-27:y;
    ```
    should be written as:
    ```
    if (y > 27)
        {
        x = y - 27 ;
        }
    else {
        x = y ;
        }
    ```
* An exception to this rule is that `?:` ternary conditional operators are allowed when passing values to logging messages.

## Development platform

### Docker container

 * Development is performed inside the `calycopis-dev` container (see
   [Task-to-container mapping](#task-to-container-mapping)). The container
   is built from the `docker/fedora-base/` base image with the tooling
   added by the `docker/developer-tools/` layer, and the `calycopis-pytest`
   container uses the same `developer-tools` image. The `calycopis-dsh`
   container instead uses the `deepseek-harness` image, which is built on
   top of `developer-tools` (see below).
 * The `fedora-base` image is a RedHat Fedora container with the following tools installed:
   * atop, bind-utils, curl, dateutils, diffutils, findutils, git, gnupg, gzip, hostname,
     htop, iotop, ipcalc, jq, less, nano, openssh-clients, patch, procps-ng, pwgen, rsync,
     s3cmd, sed, tar, wget, which, xmlstarlet, yamllint, yq, zip
 * The `developer-tools` layer adds:
   * Java 25 JDK (`java-25-openjdk-devel`) — pinned to a specific version;
     installing the `latest` package caused problems with the Maven compiler plugin
   * Podman
   * Python 3 and pip (`python3`, `python3-pip`)
   * PyYAML (`python3-pyyaml`) for the OpenAPI processor
   * The PostgreSQL client (`postgresql`) for `pg_isready` / `psql`

 * Additional tools can be installed using `dnf` but requires user permission to do so.

 * The `deepseek-harness` image is built from `developer-tools` (via the
   `docker/deepseek-harness/` layer) and adds Node.js, pnpm, and the DeepSeek
   harness itself. It is used for the DSH web container (`calycopis-dsh`):
   * Exposes the DSH web proxy port **3081**.
   * Declares `VOLUME /root/.dsh` so the user's DSH configuration is mounted
     at runtime rather than baked into the image; the web proxy plugin must be
     (re)installed at runtime against that configuration (see
     [Running the DSH web container](#running-the-dsh-web-container)).

## Docker service

### Host Podman service

 * The host system runs Podman as a rootless service.
 * See https://docs.podman.io/en/latest/markdown/podman-system-service.1.html for details.
 * Each development container is launched with a volume mount mapping the unix
   socket for the host Podman service into the container, enabling agents
   running inside the containers to access the Podman service on the host.
 * The `bin/container-host.sh` script discovers the Podman socket path and
   exports it as the `CONTAINER_HOST` / `CONTAINER_PATH` environment
   variables. It is used by the CI workflow; locally the socket is mounted
   with an explicit `CONTAINER_HOST` environment variable:

```
podman run \
  ....
  --env "DOCKER_HOST=unix:///run/podman/podman.sock" \
  --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
  --volume "${XDG_RUNTIME_DIR}/podman/podman.sock:/run/podman/podman.sock:rw,z" \
  ....
  ....
```

> **WARNING** — mounting the Podman socket into a container exposes the
> user's Podman service to that container.

### Environment file

The container, pod, and network names, the shared directories, and the
database details are defined in a `calycopis.env` file at the repository
root. It is created during setup and passed to every container with
`--env-file`:

```
CALYCOPIS_DEV_NAME=calycopis-dev
CALYCOPIS_POD_NAME=calycopis-pod
CALYCOPIS_NET_NAME=calycopis-network
CALYCOPIS_LOG_DIR=/var/calycopis/log
CALYCOPIS_DATA_DIR=/var/calycopis/data
CALYCOPIS_CONFIG_DIR=/etc/calycopis
CALYCOPIS_DB_NAME=calycopis-db-name
CALYCOPIS_DB_HOST=calycopis-db-host
CALYCOPIS_DB_PORT=5432
```

The outer environment (sourced from `${HOME}/calycopis.env` and
`${HOME}/dsh.env` on the host) supplies the supporting variables used in the
commands below: `CALYCOPIS_CODE`, `CALYCOPIS_ROOT`, `XDG_RUNTIME_DIR`, and
`DSH_HOME`.

### Network and pod

The containers run inside a dedicated Podman network and pod:

```bash
podman network create \
    --subnet 172.30.100.0/24 \
    "${CALYCOPIS_NET_NAME:?}"

podman pod create \
    --name "${CALYCOPIS_POD_NAME:?}" \
    --network "${CALYCOPIS_NET_NAME:?}" \
    --publish 8082:8082 \
    --publish 3081:3081
```

 * Port **8082** is the broker service.
 * Port **3081** is the DSH web proxy.

### Architecture: container-in-container via the Podman socket

The development environment involves four containers, plus the application
containers created by the broker:

 1. **The host machine** — runs the Podman service and owns the host filesystem.
 2. **`calycopis-dev`** — the development container where the Cursor agent and
    the Java broker run. It owns the anonymous volumes (see below).
 3. **`calycopis-db-host`** — the PostgreSQL database container.
 4. **`calycopis-pytest`** — the container used to create the test data and
    run the Python test suite.
 5. **`calycopis-dsh`** — the DSH (DeepSeek harness) web container, running
    the DSH harness (agents and web UI) and providing the web proxy on
    port 3081.
 6. **Application containers** (e.g. `heliophorus-cantliei`,
    `heliophorus-androcles`) — created by the broker via the Podman API.

### Task-to-container mapping

Each task should be run in the container that is intended for it, rather
than in whichever container happens to be convenient:

| Task | Container |
|------|-----------|
| Build and run the Java broker | `calycopis-dev` |
| Create the test data and build/run the Python tests | `calycopis-pytest` |
| Run the DSH harness (agents and web UI) | `calycopis-dsh` |

The three development containers currently share the same base image
(`developer-tools`) and a similar set of tools, so any of them could
technically perform any of the tasks. However, the images are expected to
become more specialised over time for the task they are intended for, so
where possible keep each task in its designated container. Running each
task in its own container also keeps the task-specific tooling (for example
the Python test dependencies in `calycopis-pytest`, or the DSH plugins in
`calycopis-dsh`) installed only where it is needed, and stops one container
from becoming an uncontrolled accumulation of everything.

The bind-mounted `podman.sock` socket bridges the containers and the host:
API calls made inside a development container are forwarded to the Podman
service on the host. Critically, the Podman service executes those calls in
the context of the **host** filesystem, not the calling container's
filesystem.

### Anonymous volumes and `--volumes-from`

`calycopis-dev` is launched with **anonymous volumes** for the shared
configuration and data directories. The lifetime of an anonymous volume is
linked to the lifetime of the container that created it:

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "${CALYCOPIS_DEV_NAME:?}" \
    --volume /etc/calycopis \
    --volume /var/calycopis/log \
    --volume /var/calycopis/data \
    --env    "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --volume "${XDG_RUNTIME_DIR:?}/podman/podman.sock:/run/podman/podman.sock:rw,z" \
    --volume "${CALYCOPIS_ROOT:?}:/Calycopis:rw,z" \
    --volume "${HOME:?}/.m2:/root/.m2:rw,z" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    localhost/calycopis/developer-tools:2026.09.14 \
    bash
```

The other containers are launched in the same pod, each with
`--volumes-from calycopis-dev`, so all four containers see the same files:

 * `/etc/calycopis` — broker and database configuration.
 * `/var/calycopis/log` — log output.
 * `/var/calycopis/data` — test data and other shared data.

Because the volumes are anonymous, they are removed with `calycopis-dev`:
recreating the dev container means re-running the configuration setup (see
[Database service](#database-service)).

### Running the Python test container

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "calycopis-pytest" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    --volumes-from "${CALYCOPIS_DEV_NAME:?}" \
    localhost/calycopis/developer-tools:2026.09.14 \
    bash
```

### Running the DSH web container

The DSH harness (agents and the web UI) runs in the `calycopis-dsh`
container (see [Task-to-container mapping](#task-to-container-mapping)):

```bash
podman run \
    --rm \
    --tty \
    --interactive \
    --pod  "${CALYCOPIS_POD_NAME:?}" \
    --name "calycopis-dsh" \
    --env "CONTAINER_HOST=unix:///run/podman/podman.sock" \
    --env-file "${CALYCOPIS_CODE:?}/calycopis.env" \
    --volumes-from "${CALYCOPIS_DEV_NAME:?}" \
    --env "DSH_HOME=/opt/dsh" \
    --volume "${DSH_HOME:?}:/opt/dsh:rw,z" \
    localhost/calycopis/deepseek-harness:2026.09.14 \
    bash
```

Inside `calycopis-dsh`, the web proxy plugin is (re)installed at runtime —
the plugins live in the user's DSH configuration, not in the image:

```bash
dsh plugin --profile web add github:smanx/dsh-proxy#master
```

A runtime patch is also needed so the web profile injects the web server
credentials into the client connection (workaround; see the notes for the
reference):

```bash
sed -i '
    /^const inject =/ {
        s/^const inject = \["credentials"\];/const inject = \["webServer", "credentials"\];/
        }
    ' /usr/local/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/dsh-client-connection/lib/index.js
```

Then start the web profile:

```bash
dsh web --no-open
```

The web UI runs on port 3080 inside the container and is reachable through
the proxy at `http://127.0.0.1:3081/?token=....` on the host (the token is
printed by `dsh web`).

### Filesystem side effects of the bind-mounted Podman socket

When the broker (running inside the `calycopis-dev` container) calls the
Podman API to create a container with a bind mount, the Podman service on
the host resolves the bind mount path against the **host filesystem**.

This has important consequences:

 * **Files created inside a development container are not visible to
   application containers.** For example, if you create
   `/var/calycopis/data/random.txt` inside the `calycopis-dev` container,
   that file exists only in the container's filesystem. When the broker
   launches an application container with
   `-v /var/calycopis/data/random.txt:/input:ro`, the Podman service mounts
   the file at that path on the **host** filesystem — which may be a
   completely different file, or may not exist at all.

 * **The host file and the container file can have the same path but
   different content.** If `/var/calycopis/data/random.txt` exists on both
   the host and inside the `calycopis-dev` container, the application
   container will always see the host copy. Python tests that read the file
   directly (e.g. via `open()` or `hashlib`) will see the container copy,
   leading to mismatches.

 * **Anonymous volumes have hash-named host paths.** The anonymous volumes
   owned by `calycopis-dev` are stored on the host under the Podman storage
   directory in hash-named sub-directories (e.g.
   `/home/<user>/.local/share/containers/storage/volumes/<hash>/_data/`).
   To bind-mount a file from a shared volume into an application container
   you must use this host path, discovered with:

   ```bash
   podman inspect calycopis-dev \
   | jq -r '
       .[0].Mounts.[]
       | select(.Destination == "/var/calycopis/data")
       | .Source
       '
   ```

 * **Containers launched via the Podman API can see each other's bind
   mounts.** Because both the application container and any helper
   containers (e.g. an Alpine container used to compute a reference
   checksum) are launched through the same host Podman service, they both
   see the host filesystem. Running `podman run -v /path:/input alpine
   md5sum /input` from inside `calycopis-dev` will produce the same result
   as the broker's application container, because both resolve `/path`
   against the host.

### Practical implications for testing

 * **Do not rely on local file I/O for reference values.** When a Python
   test needs to compute an expected checksum or verify file content that
   will be seen by an application container, it should compute the reference
   by running a container with the same bind mount (via `docker-py` or
   `podman run`), rather than reading the file directly from a development
   container's filesystem.

 * **Test data files must be created in a shared volume.** Test data is
   created inside a container that shares the dev container's volumes (e.g.
   `calycopis-pytest`, launched with `--volumes-from calycopis-dev`), so the
   file lands in the anonymous `/var/calycopis/data` volume. The **host
   path** of that file (the hash-named volume directory plus the file name)
   is what must be used for bind mounts into application containers.

 * **Verify test data with a helper container.** After creating test data,
   verify it is reachable by running a helper container (e.g. Alpine)
   bind-mounted at the discovered host path and comparing the checksums:

   ```bash
   podman run \
       --rm \
       --volume "${testfilehostpath:?}:/input" \
       alpine:3 sh -c '
           md5sum /input | awk "{print \$1}"
           sha256sum /input | awk "{print \$1}"
           '
   ```

## Project structure

### Directory layout

 * `agents/` - Agent configuration for AI-assisted development.
   * `agents/rules/` - Coding rules (licence header, copyright year, AIMetrics, unexpected behaviour).
   * `agents/plans/` - Plan documents for agent-driven implementation work.
 * `attic/` - A place for things that are no longer used (`ambleck`, `calycpois`, `openapi`, `pandak`, `python`, `spring-openapi`).
 * `bin/` - Shell scripts used by the build and development process.
   * `versions.sh` - Reads `config.yaml` and exports the version environment variables.
   * `container-host.sh` - Discovers the Podman socket and exports `CONTAINER_HOST` / `CONTAINER_PATH`.
 * `config/` - Configuration templates.
   * `database.yaml` - Template for the PostgreSQL datasource configuration.
   * `admin.yaml` - Template for the admin identity configuration.
 * `config.yaml` - Project configuration: schema, package, and broker versions (see [Version management](#version-management)).
 * `calycopis.env` - Environment file created during setup, defining the container, pod, and network names plus the shared directories and database details (see [Environment file](#environment-file)).
 * `demo/` - A multi-broker costs-and-metrics demonstration (three brokers with different cost/metric profiles plus a demo client).
 * `docker/` - Definitions for the Docker containers used by the project.
   * `bin/` - Shell scripts to manually build, clean, and push the Docker containers.
   * `compose/` - A docker-compose script to launch the broker service and database (superseded by the pod-based deployment described in [Docker service](#docker-service); the file is retained).
   * `developer-tools/` - The Dockerfile for the `developer-tools` container.
   * `fedora-base/` - The base RedHat Fedora image used by the `developer-tools` container.
   * `java-runtime/` - The base image used to build the `calycopis-broker` service container.
   * `python-tester/` - Support for the Python test container.
 * `docs/` - A place for documents and documentation.
   * `docs/adass/` - Presentations made at ADASS conferences (ADASS-2023, ADASS-2024).
 * `java/` - The main project source code (Spring Boot application).
   * `java/src/main/java/net/ivoa/calycopis/broker/` - The `engine/` and `spring/` package trees.
   * `java/src/main/resources/` - `application.yaml`, `log4j2.xml`.
   * `java/src/test/` - Java test sources.
   * `java/mvnw` - The Maven wrapper.
   * `java/pom.xml` - The Maven build.
   * `java/settings.xml` - Maven settings for the Nexus / GitHub package repositories.
 * `notes/` - Contemporary notes about the project development. `notes/zrq/20260914-01-podman-testing.txt` documents the current container deployment and testing process.
 * `skaha/` - The Skaha client schema (`schema/skaha-openapi.yaml`).
 * `tests/` - A set of tests for the project.
   * `tests/curl/` - A set of examples using `curl` to check the service behaviour.
   * `tests/python/` - A set of Python tests using the Python client module generated from the OpenAPI schema (organised into `any/`, `mock/`, `docker/`, and `states/` sub-directories).
 * `.github/workflows/` - GitHub Actions workflows (see [CI/CD](#cicd)).

## Database service

The broker requires a PostgreSQL database. H2 is not supported because the
application uses `GENERATE_SERIES` and other PostgreSQL-specific SQL features.

The broker listens on port **8082** (configured via `server.port` in
`application.yaml`).

#### Configuration chain

The main `application.yaml` does **not** contain database credentials directly.
Instead it imports external files:

```yaml
spring:
    config:
        import:
          - file:/etc/calycopis/admin.yaml
          - file:/etc/calycopis/database.yaml
          - optional:file:/etc/calycopis/spring.yaml
          - optional:file:/etc/calycopis/timings.yaml
```

The external file `/etc/calycopis/database.yaml` supplies the Spring datasource
properties:

```yaml
spring:
    datasource:
        url: jdbc:postgresql://calycopis-db-host:5432/calycopis-db-name
        username: <generated-username>
        password: <generated-password>
        driverClassName: org.postgresql.Driver
        initialize: true
```

This separation keeps credentials out of the version-controlled source tree.
Templates for these files live in the `config/` directory.

#### Creating the configuration files

The configuration files are created inside the `calycopis-dev` container
(which owns the `/etc/calycopis` anonymous volume), using `pwgen` (available
in the `developer-tools` container) to generate random credentials:

```bash
cat > "${CALYCOPIS_CONFIG_DIR:?}/admin.yaml" << EOF
calycopis:
    admin:
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" << EOF
spring:
    datasource:
        url: jdbc:postgresql://${CALYCOPIS_DB_HOST:?}:5432/${CALYCOPIS_DB_NAME:?}
        username: $(pwgen 32 1)
        password: $(pwgen 32 1)
        driverClassName: org.postgresql.Driver
        initialize: true
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/spring.yaml" << EOF
spring:
    profiles:
        active: docker
EOF

cat > "${CALYCOPIS_CONFIG_DIR:?}/timings.yaml" << EOF
calycopis:
  broker:
    timing:
      session:
        EXPIRED:
          timeout: 300
          polling: 5
EOF
```

The PostgreSQL container cannot read the credentials from the YAML files, so
extract them into plain files with `yq`:

```bash
yq '.spring.datasource.username' \
   "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" \
   > "${CALYCOPIS_CONFIG_DIR:?}/pgusername"

yq '.spring.datasource.password' \
   "${CALYCOPIS_CONFIG_DIR:?}/database.yaml" \
   > "${CALYCOPIS_CONFIG_DIR:?}/pgpassword"
```

TODO: this configuration generation should be moved into an initialisation
script.

#### Starting the PostgreSQL container

Start a PostgreSQL instance in the same pod, sharing the `calycopis-dev`
volumes so it can read the configuration:

```bash
podman run \
    --rm \
    --detach \
    --expose "${CALYCOPIS_DB_PORT:?}" \
    --pod "${CALYCOPIS_POD_NAME:?}" \
    --name "${CALYCOPIS_DB_HOST}" \
    --env "POSTGRES_DB=${CALYCOPIS_DB_NAME:?}" \
    --env "POSTGRES_USER_FILE=${CALYCOPIS_CONFIG_DIR:?}/pgusername" \
    --env "POSTGRES_PASSWORD_FILE=${CALYCOPIS_CONFIG_DIR:?}/pgpassword" \
    --volumes-from "${CALYCOPIS_DEV_NAME}" \
    "docker.io/library/postgres:latest"
```

Running inside the same pod means PostgreSQL is accessible at
`calycopis-db-host:5432` from within the other containers, matching the
datasource URL in the configuration file.

#### Verifying the database is ready

The `postgresql` client (installed in the `developer-tools` image) provides
`pg_isready`. Wait for the database to accept connections:

```bash
postgreswait()
    {
    for ((i = 1; i <= 4; i++))
    do
        if pg_isready \
            --host "${CALYCOPIS_DB_HOST:?}" \
            --port "${CALYCOPIS_DB_PORT:?}" \
            --dbname "${CALYCOPIS_DB_NAME:?}"
        then
            echo "[$(date)] database is ready"
            return 0
        fi
        echo "[$(date)] waiting for database to start (${i}/10)."
        sleep 10
    done
    echo "[$(date)] database is NOT ready"
        return 1
    }

postgreswait
```

#### Re-creating the database

The broker uses `spring.jpa.hibernate.ddl-auto: create`, so the schema is
recreated on every broker restart. If you need a completely fresh database
(e.g. after schema changes that cause migration errors), stop and re-create
the PostgreSQL container with the command above. The credentials are read
from `/etc/calycopis/pgusername` and `/etc/calycopis/pgpassword`, so they
remain consistent without needing to be regenerated.

Note that the configuration and data live in anonymous volumes owned by
`calycopis-dev`: removing `calycopis-dev` removes the volumes too, so a full
clean start means re-running the whole setup (configuration files, database,
and test data).

## Version management

 * All versions are defined in `config.yaml`:
   * `openapi.schema.version` - the OpenAPI schema version (currently `1.0.7`).
   * `openapi.spring.version` - the generated Spring package version (currently `1.0.7-SNAPSHOT`).
   * `openapi.python.version` - the generated Python client version (currently `1.0.7.dev5`).
   * `broker.version` - the broker package version (currently `1.0.7-SNAPSHOT`).
 * `bin/versions.sh config.yaml` reads the file and exports the corresponding
   environment variables (`CALYCOPIS_BROKER_VERSION`, `CALYCOPIS_OPENAPI_SCHEMA_VERSION`,
   `CALYCOPIS_OPENAPI_SPRING_VERSION`, `CALYCOPIS_OPENAPI_PYTHON_VERSION`).
 * The Maven build (`java/pom.xml`) takes its version and the schema package
   version from these environment variables, so `bin/versions.sh` must be run
   before any Maven build. When run inside a GitHub Actions job it also writes
   the values to `GITHUB_ENV`.

## Maven build

Build and run the broker inside the `calycopis-dev` container (see
[Task-to-container mapping](#task-to-container-mapping)). The project can be
built from the `java` directory. First initialise the versions (see
[Version management](#version-management)):

```
source bin/versions.sh config.yaml
```

Then build with:

```
pushd java
./mvnw clean compile
popd
```

The service supports two different platforms:
* A `mock` platform with simple mock implementations.
* A `docker` platform that runs containers on the local Docker/Podman service.

The default platform profile is set in `java/src/main/resources/application.yaml`
(`spring.profiles.default: docker`), and can be overridden from the command line.

The service can be run from the `java` directory using the Maven Spring Boot
plugin, selecting the platform with the `SPRING_PROFILES_ACTIVE` environment
variable:

```
SPRING_PROFILES_ACTIVE=mock ./mvnw clean spring-boot:run
```

or

```
SPRING_PROFILES_ACTIVE=docker ./mvnw clean spring-boot:run
```

The platform can also be overridden directly on the command line with the
`--spring.profiles.active` argument:

```
./mvnw clean spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=mock"
```

### Building the container image

The Maven build can package the broker as an OCI container image using the
Spring Boot build-image goal (via Paketo buildpacks):

```
./mvnw clean spring-boot:build-image
```

The image is tagged `calycopis-broker:<version>` (see the
`spring-boot-maven-plugin` configuration in `java/pom.xml`). The build
expects a Docker/Podman service to be available (e.g. via the
`CONTAINER_HOST` environment variable).

### External dependencies

 * [Spring Boot](https://spring.io/projects/spring-boot) The framework for developing web applications.
 * [PostgreSQL](https://www.postgresql.org/) provides a database to store the Java Persistence API entities
 * [Jackson FasterXML](https://github.com/FasterXML/jackson) and [Jackson annotations](https://github.com/FasterXML/jackson-annotations) to provide serialization and deserialization for JSON, YAML, and XML.
 * [ThreeTen-Extra](https://www.threeten.org/threeten-extra/) and [ThreeTen-Extra](https://github.com/ThreeTen/threeten-extra) provides additional date-time classes, particularly Interval.
 * [SLF4J logging](https://www.slf4j.org/manual.html) logging framework.
 * [Lombok](https://projectlombok.org/) is used for boilerplate reduction, primarily SLF4J logging.

## Testing

### Curl tests

The `tests/curl` directory contains a set of worked examples (numbered `001`–`007`
and `example-001`–`example-013`) that use curl to send and receive messages to the service.
Many of the tests are out of date and will not run.
Use them for reference only.

### Python tests

The `tests/python` directory contains a set of Python tests for the service,
organised by the broker platform they require:

| Sub-directory | Purpose |
|---------------|---------|
| `tests/python/any/` | Tests that work on either platform. |
| `tests/python/mock/` | Tests that require the mock platform. |
| `tests/python/docker/` | Tests that require the docker platform. |
| `tests/python/states/` | State-transition tests that verify session and component state transitions. Tests in this directory are allowed to access the broker database directly, using a Python database client (e.g. `psycopg`, with the datasource settings read from `/etc/calycopis/database.yaml`), to verify the state transitions. |

Current test files:

| Test file | Platform | Notes |
|-----------|----------|-------|
| `test_mock_validators.py` | mock only | Tests mock-specific validation rules (blacklists, resource limits) that are hardcoded in the `Mock*ValidatorImpl` classes. These tests would fail on the docker platform because the docker validators have different rules. |
| `test_mock_direct_execution.py` | mock only | Direct execution tests adapted for the mock platform. Includes basic tests, a single lifecycle completion test, a cancellation test, and an OFFERED-phase-skip test. Excludes exit-code and timed-completion tests because the mock platform does not simulate exit codes or configurable execution durations. |
| `test_mock_stress.py` | mock only | Stress test using direct execution on the mock platform. |
| `test_docker_platform.py` | docker only | Tests real Docker container execution via the Docker/Podman platform. Requires the `docker` profile, a configured `CONTAINER_HOST`, and network access to pull container images. |
| `test_docker_direct_execution.py` | docker only | Direct execution tests for the docker platform using the Heliophorus-cantliei container. |
| `test_docker_stress.py` | docker only | Stress test using the offer-set flow on the docker platform with the Heliophorus-cantliei container. |
| `test_docker_bind_mount.py` | docker only | Bind-mount behaviour tests for the docker platform. |
| `test_docker_volume_mount.py` | docker only | Volume-mount behaviour tests for the docker platform. |
| `test_docker_androcles_md5.py` | docker only | Checksum (MD5) verification test using the Heliophorus-androcles container. |
| `test_docker_session_connectors.py` | docker only | Tests the stdout/stderr session connectors advertised on Docker execution sessions: the connectors start in the PREPARING state when the session is OFFERED, become AVAILABLE (with HTTP GET locations) once the container logs are captured, and become FINISHED when execution completes. Also verifies the stdout/stderr HTTP endpoints and the 404 response for an unknown session. Uses the Heliophorus-cantliei container. |
| `test_resource_registration.py` | either | Tests cross-referencing of resources (data ↔ storage) via the offer-set API. These tests only inspect the `OfferSetResponse` and never accept any offers, so no lifecycle processing is triggered and the tests work on either platform. |
| `test_costs_and_metrics.py` | either | Tests the costs-and-metrics data advertised by the broker. |
| `test_identity_auth.py` | either | Tests local identity and authentication. |
| `test_session_expiry.py` | either | State-transition tests (in `tests/python/states/`) that verify the EXPIRED session behaviour: an unaccepted OFFERED session becomes EXPIRED at its expiry time and stays EXPIRED, while REJECTED/ACCEPTED sessions are untouched. Uses direct database access to verify the transitions (see the sub-directory table above). |

The Python tests use the Python client classes generated from the OpenAPI
schema (`calycopis_openapi_client`) to test both the service functionality and
cross-language interoperability between the Java service and a Python client.

In CI, the tests are containerised: `tests/python/Dockerfile` builds a
`calycopis/python-tester` image and `tests/python/bin/run-tests.sh` runs the
suite inside it, with the broker and test versions passed as environment
variables (`CALYCOPIS_BROKER_VERSION`, `CALYCOPIS_OPENAPI_*_VERSION`).

Locally, the test data is created and the suite runs inside the
`calycopis-pytest` container (see
[Running the Python test container](#running-the-python-test-container) and
[Task-to-container mapping](#task-to-container-mapping)), which shares the
`calycopis-dev` volumes. Before running, create the test data and write the
test-data details to `/etc/calycopis/testing.yaml` (see
[Practical implications for testing](#practical-implications-for-testing)).
The tests read their configuration directly from the YAML files in
`/etc/calycopis` via the shared `tests/python/conftest.py`:

 * `admin.yaml` - the admin credentials used to seed the test identities.
 * `testing.yaml` - the test-data files (host paths looked up by name).
 * `database.yaml` - the broker datasource settings for the state tests.

The broker URL defaults to the development container name
(`CALYCOPIS_DEV_NAME`, falling back to `calycopis-dev`) and can be
overridden with `CALYCOPIS_URL`.

```bash
pushd "/Calycopis/Calycopis-broker/Calycopis-broker-uksrc-zrq"
    source bin/versions.sh config.yaml
    pushd tests/python
        pip install -r requirements.txt
        pytest -v -s docker
    popd
popd
```

The `testing.yaml` file lists each test-data file with its name, container
path, **host path** (see [Anonymous volumes and `--volumes-from`](#anonymous-volumes-and---volumes-from)),
and expected checksums:

```yaml
calycopis:
  broker:
    testing:
      testdata:
        - name: "random.dat"
          filepath: "/var/calycopis/data/random.dat"
          hostpath: "/home/<user>/.local/share/containers/storage/volumes/<hash>/_data/random.dat"
          md5sum: "<md5>"
          sha256sum: "<sha256>"
```

For out-of-band experimentation with the Python client (scripting against
the broker from `calycopis-dev`), install the built wheel:

```
pip install /Calycopis/Calycopis-openapi/Calycopis-openapi-uksrc-zrq/codegen/python/client/target/dist/*.whl
```

The test suite itself should still be run in the `calycopis-pytest` container
(or the CI `calycopis/python-tester` container), as described above.

When running lifecycle or stress tests on the mock platform, note that the mock
processing loop processes requests serially with a configurable delay per
component phase (default 5 seconds, configurable via the
`calycopis.mock-entities.actions` section of `application.yaml`). Sessions
created by earlier tests accumulate in the processing queue and slow down
subsequent tests. For best results, run lifecycle tests with a freshly started
broker to avoid queue congestion.

## CI/CD

The GitHub Actions workflow in `.github/workflows/build-packages.yml`
(`Calycopis broker packages`) runs on pushes to `main`, pull requests, and
manual dispatch. It has a single job (`build-java-broker`) that:

1. Runs `bin/versions.sh config.yaml` to initialise the version environment variables.
2. Installs Podman with socket service and runs `bin/container-host.sh` to set `CONTAINER_HOST`.
3. Sets up Java 25 (Temurin) and installs the Maven settings from `java/settings.xml`.
4. Builds the broker jar (`./mvnw clean install`).
5. Packages the broker as an OCI container image (`./mvnw spring-boot:build-image`).
6. Builds the `calycopis/python-tester` integration-test container from `tests/python`.
7. Runs the integration tests via `tests/python/bin/run-tests.sh`.
8. Uploads the jar as a GitHub artifact.
9. On pushes to `main`, publishes the jar to the UKSRC Nexus Maven repository
   (`uksrc-profile`) when run from `uksrc/Calycopis-broker`, or to the IVOA
   GitHub Packages repository (`ivoa-profile`) when run from
   `ivoa/Calycopis-broker`.
10. On pushes to `main`, pushes the `calycopis-broker` image to the UKSRC
    Harbor registry (via `redhat-actions/push-to-registry`) when run from
    `uksrc/Calycopis-broker`.

The integration-test step (`tests/python/bin/run-tests.sh`) builds a test
pod and a temporary config directory containing `admin.yaml`,
`database.yaml`, `spring.yaml`, `timings.yaml` and `testing.yaml` (with
`pwgen`-generated credentials and the test-data checksums), starts the
broker and database containers with that directory mounted at
`/etc/calycopis`, and runs the Python suite in the
`calycopis/python-tester` container with the same config directory. The
tests read their configuration directly from these YAML files, so no
admin-credential or test-data environment variables are passed.
