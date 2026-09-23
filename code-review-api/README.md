# code-review-api

Spring Boot API for authentication, personal rules and rule sets, GitHub retrieval, asynchronous reviews, findings, and feedback. It uses H2 file storage by default and runs on port `8080`.

From the repository root, package with `mvn -pl code-review-api -am package -DskipTests`, then run `java -jar code-review-api/target/code-review-api-0.0.1-SNAPSHOT.jar`. Configuration and input limits are in `src/main/resources/application.yml`. See the root README for environment variables and API routes.
