# Tailormap API

Tailormap API provides the webserver and backend API for the Tailormap frontend. The frontend for 
the Tailormap viewer and admin interfaces are written in Angular and developed in a separate
repository.

If you want to run Tailormap the best starting point is the [tailormap-viewer](https://github.com/B3Partners/tailormap-viewer/) 
repository with the Angular frontends where you will find a Docker Compose stack which will run the 
frontends, backend and PostgreSQL configuration database.

This repository builds a `tailormap-api` Docker image with only the backend which is used as a base image by
tailormap-viewer which adds the static Angular frontend bundle to it to create the final 
`tailormap` image with both the frontend and backend in it which only requires a PostgreSQL database
to store configuration.

## Development

To develop the backend this Spring Boot application can be run locally.

### Prerequisites

To build and run the project, you need to have the following installed:

- Java 11 JDK
- Maven 3.9.5 or higher
- PostgreSQL configuration database
- Docker 20.10.x with buildx 0.9 or higher (this requirement may be skipped if you don't need to build
  the docker images or build release artifacts)

The quickest way to start the PostgreSQL database is to check out the Docker Compose stack in [tailormap-viewer](https://github.com/B3Partners/tailormap-viewer/)
and start the `db` container. This opens a port on localhost:5432. If you already have your own 
database running locally, create a database, user and password all set to `tailormap` to use that.
Beware that Tailormap is only developed and supported with the PostgreSQL version from the Docker 
Compose stack.

### Running

You can run this application in various ways:

- Using **spring-boot-maven-plugin** ([documentation](https://docs.spring.io/spring-boot/docs/current/maven-plugin/reference/htmlsingle/#goals))
  
  Examples:
 ```shell
  # Set active Maven profiles
  mvn -Pdeveloping,hal-explorer spring-boot:run
  # Set active Spring profile
  mvn -Pdeveloping,hal-explorer spring-boot:run -Dspring-boot.run.profiles=dev,populate-testdata
  # Set Spring property
  mvn -Pdeveloping,hal-explorer spring-boot:run -Dspring-boot.run.profiles=dev,populate-testdata -Dspring-boot.run.arguments=--spatial.dbs.connect=true
  ```
- Using an IDE such as **IntelliJ IDEA**
- Using the runnable jar
- Running the docker image as a container
  ```shell
  # using the defaults
  docker run --rm -it --name tailormap-api -h tailormap-api --network host ghcr.io/b3partners/tailormap-api:snapshot  
  # using a different database URL
  docker run --rm -it --name tailormap-api -h tailormap-api -e "SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/tailormaps" --network host ghcr.io/b3partners/tailormap-api:snapshot
  ```

You can then point your browser at http://localhost:8080/swagger-ui/index.html to see the frontend 
API as described by the OpenAPI specification and use the admin backend enabled by Spring Data REST
at http://localhost:8080/api/admin/ if you have activated the `hal-explorer` Maven profile.

The easiest way to see the API in action is to run the [tailormap-viewer](https://github.com/B3Partners/tailormap-viewer/)
frontends locally. These can be run with a webpack dev server which will reverse proxy a locally 
running `tailormap-api` if you start the frontends with the `PROXY_USE_LOCALHOST=true` environment
variable. Using the frontends you can also use the login form to use the admin APIs, open
http://localhost:4201/api/admin/ after logging in as admin to see the HAL explorer (run with the 
`hal-explorer` Maven profile).

### Basic development procedure

1. Write your code using the [Google Java style](https://google.github.io/styleguide/javaguide.html)
2. Commit and push your branch to create a pull request
3. Wait for continuous integration and code review to pass, possibly amend your PR and merge your PR

Make sure you have useful commit messages, any PR with `WIP` commits will need to be squashed before
merge.

See also [QA](#QA).

### Maven Profiles

Some Maven profiles are defined:

* **macos**
    - Sets specific properties for MacOS such as skipping the docker part of the build
    - Activated automagically
* **openbsd**
    - Sets specific properties for OpenBSD such as skipping the docker part of the build
    - Activated automagically
* **windows**
    - Sets specific properties for Windows such as skipping the docker part of the build
    - Activated automagically
* **release**
    - Release specific configuration
    - Activated automagically during Maven release procedure
* **developing**
    - Sets specific properties for use in the IDE such as disabling docker build and QA
    - Activated using the flag `-Pdeveloping` or checking the option in your IDE
* **qa-skip**
    - Will skip any "QA" Maven plugins defined with a defined `skip` element
    - Activated using flag `-DskipQA=true`
* **hal-explorer**
    - Activated using the flag `-Phal-explorer`
    - Includes the HAL Explorer dependency, available at `/api/admin/` after logging in. The HAL
      Explorer does not support CSRF tokens, so set the `tailormap-api.security.disable-csrf` 
      property to true (or you can activate the `dev` Spring profile which sets this property)

### Spring profiles

When the **default** profile is active tailormap-api behaves as deployed in production. If the
database schema is empty tables will be created or the existing schema will be updated if necessary
by Flyway. When an admin account is missing it is created using a random password which is logged.

See also the comments in the `application-(profile).properties` files in the [resources](src/main/resources)
directory.

Other Spring profiles are useful during development:

* **[ddl](src%2Fmain%2Fresources%2Fapplication-ddl.properties)**  
 Use manually in development to create the initial Flyway migration script using the metadata from 
 the JPA entities. Use only once before going in production, after that use additional Flyway 
 migration scripts. __Warning:__ all tables in your current tailormap database will be removed!
* **[ddl-update](src%2Fmain%2Fresources%2Fapplication-ddl-update.properties)**  
 Use manually in development to create a SQL script to update the current database schema to match 
 the JPA entities. Rename the generated Flyway script to the correct version and give it a
 meaningful name. The script may need to be modified. If you need to migrate using Java code you can
 use a Flyway callback (executed before the entity manager is initialized) or add code to 
 [TailormapDatabaseMigration.java](src%2Fmain%2Fjava%2Fnl%2Fb3p%2Ftailormap%2Fapi%2Fconfiguration%2FTailormapDatabaseMigration.java) 
 if you can use the entity manager after all SQL migrations.
* **[dev](src%2Fmain%2Fresources%2Fapplication-dev.properties)**  
 Use during development to:
  * Disable CSRF checking, so you can use the HAL explorer with PUT/POST/PATCH/DELETE HTTP requests
  * See stack traces in HTTP error responses
  * See SQL output
  
  Check [application-dev.properties](src%2Fmain%2Fresources%2Fapplication-dev.properties) for details.
* **[populate-testdata](src%2Fmain%2Fresources%2Fapplication-populate-testdata.properties)**  
  Activate this profile to insert some test data in the tailormap database (all current data will be
  removed!). This testdata can be used to test or demo Tailormap functionality and is used in 
  integration tests. It relies on external services which may change or disappear and optionally 
  connects to spatial databases which can be started using Docker Compose (see
  [Spatial database stack](#spatial-database-stack)).  
  The class which loads the testdata is 
  [PopulateTestData.java](src%2Fmain%2Fjava%2Fnl%2Fb3p%2Ftailormap%2Fapi%2Fconfiguration%2Fdev%2FPopulateTestData.java).
  The default admin username and password is `tm-admin` instead of randomly generated 
  as in the default profile, but you can change it if you expose your environment publicly by 
  specifying a property.
* **[static-only](src%2Fmain%2Fresources%2Fapplication-static-only.properties)**  
  When this profile is active only the static Angular viewer and admin frontends are served and all
  other functionality is disabled. This is used for continuous deployment of frontend pull requests.

### Spatial database stack

This repository comes with a spatial database [Docker Compose stack](build/ci/docker-compose.yml).
It starts the PostGIS, Oracle Spatial and MS SQL Server spatial databases and loads some spatial 
data. This stack creates a `tailormap-data` Docker network, but also exposes the 
databases' listening ports on the host. Note that especially the Oracle container is quite large.

Please make sure you agree to the license terms of the proprietary databases before running them.

When loading testdata with the `populate-testdata` Spring profile running the spatial database stack
is optional and only connected to when the `spatial.dbs.connect` property is true. If this is not 
set the JDBC feature sources are still saved in the database (not attached as feature type for
layers).

### QA

Some quick points of attention:

* [Google Java Style](https://google.github.io/styleguide/javaguide.html) formatting is enforced. This is the style of the Android Open Source Project 
  (AOSP) with 2-space indent and different import ordering.  
  Run the following command to reformat your code (an IntelliJ plugin is also available):
  ```
  mvn com.spotify.fmt:fmt-maven-plugin:format
  ```
* PMD checks are enforced. Run `mvn pmd:check` to verify your code.  
* The ErrorProne compiler is used
* A current release of Maven is required
* Java 11
* JUnit 4 is forbidden
* Code is reviewed before merge to the main branch
* Javadoc must be valid

To run unit tests which do not need integration with the entity manager and database, run: 
```
mvn -Pdeveloping test
```

Unit tests which use the PostgreSQL configuration database _and_ the spatial database stack need 
their class name to end with `PostgresIntegrationTest`. 

Run the following command to run the integration tests. The application is started with the
`populate-testdata` profile automatically, so if you've modified your configuration it will be 
lost!
```
mvn -Pdeveloping,postgresql verify
```

### Tips and Tricks

* You can skip CI execution[^1] by specifying `[skip ci]` as part of your commit.
* You can use `mvn -U org.codehaus.mojo:versions-maven-plugin:display-plugin-updates` to search for plugin updates
* You can use `mvn -U org.codehaus.mojo:versions-maven-plugin:display-dependency-updates` to search for dependency
  updates

## Releasing

Use the regular Maven release cycle of `mvn release:prepare` followed by `mvn release:perform`. Please make sure that
you use `tailormap-api-<VERSION>` as a tag so that the release notes are automatically created.
For example:

```shell
mvn clean release:prepare -DreleaseVersion=0.1 -Dtag=tailormap-api-0.1 -DdevelopmentVersion=0.2-SNAPSHOT -T1
mvn release:perform -T1
```

[^1]: https://github.blog/changelog/2021-02-08-github-actions-skip-pull-request-and-push-workflows-with-skip-ci/