# Tailormap API

Tailormap API provides the OpenAPI for Tailormap.

## Development

Basic procedure:
1. do your thing
2. run `mvn clean install`
3. commit and push your branch to create a pull request
4. wait for code review to pass, possibly amend your PR and merge your PR

Make sure you have useful commit messages, any PR with `WIP` commits will need 
to be squashed before merge.

### Skipping 

You can skip CI execution[^1] by specifying `[skip ci]` as part of your commit

### Profiles

Some Maven profiles are defined:

* **macos**
    - Sets specific properties for MacOS such as skipping the docker part of the build
    - Activated automagically
* **windows**
    - Sets specific properties for Windows such as skipping the docker part of the build
    - Activated automagically
* **release**
    - Release specific configuration
    - Activated automagically
* **developing**
    - Sets specific properties for use in the IDE such as disabling docker build and QA
    - Activated using the flag `-Pdeveloping` or checking the option in your IDE
* **qa-skip**
    - Will skip any "QA" Maven plugins defined with a defined `skip` element
    - Activated using flag `-DskipQA=true`

## Releasing

Use the regular Maven release cycle of `mvn release: prepare` followed by `mvn release:perform`. Please make sure that
you use `tailormap-api-<VERSION>` as a tag so that the release notes are automatically created.

[^1]: https://github.blog/changelog/2021-02-08-github-actions-skip-pull-request-and-push-workflows-with-skip-ci/