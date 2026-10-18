# TaskFlow refactoring notes

## Changes made

- Kept the root folder named `taskflow`, as requested.
- Changed Maven artifact ID from `taskflow-workflow-engine` to `taskflow`.
- Fixed YAML-style `spring:`, `application:`, and `name:` lines inside
  application.properties. The correct setting is now
  `spring.application.name=taskflow`.
- Updated README branding, project identity and compatibility guidance.
- Excluded generated target output and machine-specific IntelliJ .idea settings.
- Preserved all 43 Java source/test files: their package names, imports, main
  class and test paths already consistently use com.suyog.taskflow.
- Preserved dependency versions, API contracts, security behaviour, retry
  configuration, tests, database credentials placeholders and Flyway migration.

## Intentionally retained identifiers

MySQL: flowforge / flowforge_test and their existing application users.
Environment: FLOWFORGE_DB_PASSWORD / FLOWFORGE_TEST_DB_PASSWORD.
Keycloak: realm flowforge, client flowforge-postman, audience flowforge-api.
Configuration keys: flowforge.retry.* and flowforge.security.audience.

Keeping these identifiers preserves the existing setup and stored workflows.
No global search-and-replace, database recreation or Keycloak recreation is needed.
The GitHub repository may remain taskflow-workflow-engine; its name does not
need to match the local folder or Maven artifact.

## Validation performed on this copy

- Parsed Maven XML and verified the artifact ID.
- Verified all 43 Java package declarations match their source directory paths.
- Verified no stale com.suyog.flowforge reference exists in Java source/tests.
- Verified application/test properties use key=value and have no duplicate keys.
- Verified configuration keys referenced through @Value exist in application.properties.
- Compared all Java files and the migration byte-for-byte with the input.
- Verified ZIP integrity after packaging.

A Maven compile was attempted but could not resolve the Spring Boot parent
because Maven Central DNS resolution failed in the execution environment.
The environment has Java 17; this project requires Java 21. No successful
compilation, automated test rerun or live MySQL/Keycloak test is claimed for this
copy. The earlier 64 passing tests documented in README are historical.

## Install in IntelliJ on Windows

1. Stop the application and save/commit existing work.
2. Extract taskflow.zip into a separate temporary directory first.
3. To preserve the existing Git repository, copy the contents of the extracted
   taskflow folder into your working project, replacing matching files. Do not
   delete or overwrite your existing .git directory. The ZIP contains no .git.
4. Open pom.xml as a Maven project in IntelliJ; reload Maven.
5. Set both project SDK and Maven runner JDK to Java 21.
6. Set main class to com.suyog.taskflow.TaskflowApplication. Retain your existing
   password environment variables in the run configuration and Maven terminal.
7. Keep MySQL running with the existing application and test databases/users.
8. In PowerShell at the project root, run:

```powershell
java -version
.\mvnw.cmd -version
.\mvnw.cmd clean test
```

The unchanged test suite previously passed 64 tests. Confirm zero failures and
errors on this renamed copy before committing it.

9. Start your existing standalone Keycloak on port 8081, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

10. Verify health at http://localhost:8082/actuator/health. Obtain fresh tokens
    using the existing Keycloak realm and client. Check /api/me, writer creation
    (201), same-tenant reader retrieval (200), reader creation (403), foreign-
    tenant retrieval (404), and unauthenticated retrieval (401). Retrieve a
    workflow created before the rename to confirm data continuity.

After packaging, the default executable JAR name will be
`target/taskflow-0.0.1-SNAPSHOT.jar`.

## Suggested commit

```text
refactor: complete TaskFlow naming and fix application configuration
```

Review git diff before staging. Changes to the GitHub repository name or remote
URL must be made in your own repository; this archive does not change them.
