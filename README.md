<p align="center"><img src="docs/site/assets/vectis-mark.svg" width="80" alt="Vectis lever V logo"></p>

# Vectis Studio

An in-app operations workspace for Spring Boot teams, by YavonaLabs.

Help operators find records, review changes and run configured Java actions without asking an engineer to write a query for each recurring task.

**Early preview:** Java 17, Spring Boot 3 and JPA. The current source version is `0.1.0-SNAPSHOT`; no Maven Central release is claimed. Evaluate permissions and mutation behavior for your application before production use.

## What you can do today

- Search records, combine typed filters and browse related records in bounded pages.
- Use labelled create/edit forms with validation feedback.
- Expose Java methods as named operations with explicit previews where configured.
- Configure access through Spring Security roles and an entity/action permission evaluator.
- Review permitted activity history, including the operator and reason for a change.
- Preserve list context while moving between records and recover from expired sessions.

A useful first workflow is a repeated record lookup or a narrowly scoped support operation. Developers still own authentication, validation, business rules and the operations they expose. Generic CRUD does not automatically invoke every domain service or external integration.

## Run the sample

From a checkout of this repository, with Java 17 and Maven installed:

```sh
mvn clean install
java -jar vectis-sample-app/target/vectis-sample-app-0.1.0-SNAPSHOT.jar --spring.profiles.active=demo
```

Open `http://localhost:8080/`.

| Access | Username | Password |
| --- | --- | --- |
| Try sample changes | `admin` | `admin` |
| Read-only browsing | `user` | `password` |
| Team members only (restricted relationships) | `restricted` | `password` |

The sample contains fictional records in an in-memory H2 database. Records and activity reset on restart. These public sample accounts are not production authentication.

## Add it to an application

After installing the source modules locally, add:

```xml
<dependency>
    <groupId>io.github.yavonalabs</groupId>
    <artifactId>vectis-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Mark selected JPA entities with `@AdminEntity`, use `@AdminField` for presentation, and expose specific operations with `@AdminAction`. See the [working sample entities](vectis-sample-app/src/main/java/com/example/demo/entity) and [access and preview configuration](docs/SAFE_ACTIONS.md).

```yaml
vectis:
  enabled: true
  path: /admin
  title: Support Console
  environment: Staging
  roles: [ROLE_ADMIN]
  read-only-roles: [ROLE_SUPPORT]
  # Optional: your host application's CSRF-protected POST logout endpoint.
  logout-path: /logout
```

Supply the host application's authentication and security filter chain. The default permission policy is not a substitute for your entity-specific access rules. Restrict exposed entities and fields to the intended workflow.

## Status and limits

- The Render sample deployment is undergoing [hosted release verification](docs/HOSTED_DEMO_VERIFICATION.md); it is not a production-readiness certification.
- Saved views, export, unsaved-edit protection and additional mutation-lifecycle work remain on the [roadmap](VECTIS_IMPROVEMENT_PLAN.md).
- Runtime UI assets currently include CDN dependencies.
- No enterprise edition, SSO product, approval workflow or commercial pricing is announced by this repository.

## Development and contributions

Run `mvn verify` from the repository root. Read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull request and [SECURITY.md](SECURITY.md) before reporting a vulnerability.

Useful feedback describes a real recurring support task, the smallest reproduction, and the expected outcome. Do not include customer records, credentials or private logs in public issues.

## License

[Apache License 2.0](LICENSE). Copyright 2026 Vectis Contributors (YavonaLabs).
