
# Fascicolo Sanitario 2.0

# _it-fse-gtw-garbage_

Il microservizio in oggetto ha la responsabilità di gestire la Data Retention su MongoDB, effettuando la pulizia dei record correttamente inviati o obsoleti.

<br/>

## Repos
- [*it-fse-support*](https://github.com/ministero-salute/it-fse-support) - Pagina di supporto FSE 2.0
- [*it-fse-landing*](https://github.com/ministero-salute/it-fse-landing) - Landing page dei repository FSE 2.0

<br/>

## Isolation tests

Run with Java 21, without MongoDB, a running config service, or other infrastructure:

```sh
mvn -PISOLATION clean test
```

Tests in `src/test/java/it/finanze/sanita/fse2/ms/gtw/garbage/isolation/` use
`@Tag("ISOLATION")`: excluded by default, selected only by the Maven profile.
Context tests also use `@ActiveProfiles("ISOLATION")`, which activates Spring's
profile but does not select tests. Narrow `@SpringJUnitConfig` contexts load real
services, repositories, controllers and schedulers; `@MockitoBean` replaces
external dependencies. The HTTP client uses `MockRestServiceServer`, not a network.
Do not extend `AbstractConfig` or start the full application for these tests.

The suite checks retention ordering and failure handling, Mongo query predicates,
cutoffs/projections/batch limits, config cache refresh and fallback, MVC endpoints,
tracing, scheduler failures, profile-dependent collection names and startup validation.
JaCoCo reports are generated during `test`: `target/site/jacoco/index.html`
and `target/site/jacoco/jacoco.xml`.
Use `clean` to avoid stale reports. Full application startup, MongoDB wiring
and global OpenTelemetry initialization remain outside this isolated suite.
Existing tests are unchanged and remain separately selectable with `mvn test`.

## License

[![License: AGPL v3](https://img.shields.io/badge/License-AGPL_v3-blue.svg)](https://www.gnu.org/licenses/agpl-3.0)
