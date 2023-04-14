# Tailormap API

Tailormap API provides the OpenAPI for Tailormap.

## Development

### Prerequisites

To fully build the project, you need to have the following installed:

- Java 11 JDK
- Maven 3.8.4 or higher
- Docker 20.10.x with buildx 0.9 or higher (this requirement may be skipped if you don't need to build
  the docker images or build release artifacts)

### Basic procedure:

1. do your thing, if possible use `aosp` styling (run `mvn com.spotify.fmt:fmt-maven-plugin:format sortpom:sort` to fix all formatting)
2. run `mvn clean install` to make sure all required formatting is applied and all tests pass
3. commit and push your branch to create a pull request
4. wait for code review to pass, possibly amend your PR and merge your PR

Make sure you have useful commit messages, any PR with `WIP` commits will need
to be squashed before merge.

see also [QA](#QA)

### Tips and Tricks

* You can skip CI execution[^1] by specifying `[skip ci]` as part of your commit.
* You can use `mvn -U org.codehaus.mojo:versions-maven-plugin:display-plugin-updates` to search for plugin updates
* You can use `mvn -U org.codehaus.mojo:versions-maven-plugin:display-dependency-updates` to search for dependency
  updates

### Maven Profiles

Some Maven (platform specific) profiles are defined:

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
      property to true (or you can activate the `dev` Spring profile)

**Note:** The platform specific profiles (`macos`,`openbsd` and `windows`) cannot be used to create
releases.

### Spring profiles

Spring profiles can be activated using `-Dspring.profiles.active=profile1,profile2` or using your 
IDE. The following profiles are available:

See also the comments in the `application-(profile).properties` files the in `src/main/resources` 
directory.

When the **default** profile is active tailormap-api behaves as deployed in production. If the 
database schema is empty the tables will be created or updated if necessary using Flyway. When an
admin account is missing it is created using a random password which is logged.

Other Spring profiles are useful during development:

* **ddl**  
 Use manually in development to create the initial Flyway migration script using the metadata from 
 the JPA entities. Use only once before going in production, after that use additional Flyway 
 migration scripts. __Warning:__ all tables in your current tailormap database will be removed!
* **ddl-update**  
 Use manually in development to create a SQL script to update the current database schema to match 
 the JPA entities. Rename the generated Flyway script to the correct version and give it a
 meaningful name. The script may need to be modified. If you need to migrate using Java code you can
 use a Flyway callback (executed before the entity manager is initialized) or add code to 
 [TailormapDatabaseMigration.java](src%2Fmain%2Fjava%2Fnl%2Fb3p%2Ftailormap%2Fapi%2Fconfiguration%2FTailormapDatabaseMigration.java) 
 if you can use the entity manager after all SQL migrations.
* **dev**  
 Use during development to:
  * Disable CSRF checking, so you can use the HAL explorer with PUT/POST/PATCH/DELETE HTTP requests
  * See stack traces in HTTP error responses
  * See SQL output
  
  Check [application-dev.properties](src%2Fmain%2Fresources%2Fapplication-dev.properties) for details.
* **populate-testdata**  
  Activate this profile to insert some test data in the tailormap database (all current data will be
  removed!). This testdata can be used to test or demo Tailormap functionality and is used in 
  integration tests. It relies on external services which may change or disappear and optionally 
  connects to spatial databases which can be started using Docker Compose, see [PopulateTestData.java](src%2Fmain%2Fjava%2Fnl%2Fb3p%2Ftailormap%2Fapi%2Fconfiguration%2Fdev%2FPopulateTestData.java)
  for details. 
* **static-only**  
  When this profile is active only the static Angular viewer and admin frontends are served and all
  other functionality is disabled. This is used for continuous deployment of frontend pull requests.

## QA

Some quick points of attention:

* AOSP formatting is enforced
* PMD checks are enforced
* The ErrorProne compiler is used
* A current release of Maven is required
* Java 11
* jUnit 4 is forbidden
* code is reviewed before merge to the main branch
* Javadoc must be valid

## Running

You can run this application in various ways:

- using **spring-boot-maven-plugin**
  see https://docs.spring.io/spring-boot/docs/2.6.1/maven-plugin/reference/htmlsingle/#goals
- using the starter in **IntelliJ IDEA**
- using the runnable jar
- running the docker image as a container
  ```shell
  # using the defaults
  docker run --rm -it --name tailormap-api -h tailormap-api --network host ghcr.io/b3partners/tailormap-api:snapshot
  
  # using a different database URL
  docker run --rm -it --name tailormap-api -h tailormap-api -e "SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/tailormaps" --network host ghcr.io/b3partners/tailormap-api:snapshot
  ￼
  ```
  You can then point your browser at eg. `http://localhost:8080/api/version`
  or `http://localhost:8080/api/actuator/health`

Note that you need to have a configured database that at least has the tailormap schema; running the Tailormap Admin
once should take care of that.

## Releasing

Use the regular Maven release cycle of `mvn release: prepare` followed by `mvn release:perform`. Please make sure that
you use `tailormap-api-<VERSION>` as a tag so that the release notes are automatically created.
For example:

```shell
mvn clean release:prepare -DreleaseVersion=0.1 -Dtag=tailormap-api-0.1 -DdevelopmentVersion=0.2-SNAPSHOT -T1
mvn release:perform -T1
```

[^1]: https://github.blog/changelog/2021-02-08-github-actions-skip-pull-request-and-push-workflows-with-skip-ci/