# Keycloak Confluence LDAP Group Mapper
[![CI build](https://github.com/vaulttec/keycloak-confluence-ldap-group-mapper/actions/workflows/ci-build.yml/badge.svg)](hhttps://github.com/vaulttec/keycloak-confluence-ldap-group-mapper/actions/workflows/ci-build.yml)
[![Release](https://img.shields.io/github/release/vaulttec/keycloak-confluence-ldap-group-mapper.svg)](https://github.com/vaulttec/keycloak-confluence-ldap-group-mapper/releases/latest)![](https://img.shields.io/github/license/vaulttec/keycloak-confluence-ldap-group-mapper?label=License)
![](https://img.shields.io/badge/Keycloak-23.0-blue)

Custom [Keycloak](https://www.keycloak.org) LDAP Group Mapper which creates groups and group memberships retrieved from [Confluence](https://www.atlassian.com/software/confluence) pages (representing the group hierarchy) and page properties (providing a HTML table with a group member column) via [Confluence's REST API](https://developer.atlassian.com/server/confluence/confluence-server-rest-api/‚).

## Credit
This project uses ideas or artifacts from other projects, e.g.
* The [peanuts-userprovider](https://github.com/dasniko/keycloak-extensions-demo/tree/main/peanuts-userprovider) from Niko Köbler's [keycloak-extensions-demo](https://github.com/dasniko/keycloak-extensions-demo) project.
* The test support [KeycloakEnvironment](https://github.com/thomasdarimont/keycloak-project-example/blob/main/keycloak/extensions/src/test/java/com/github/thomasdarimont/keycloak/custom/KeycloakEnvironment.java) and [OpenLDAP support](https://github.com/thomasdarimont/keycloak-project-example/blob/main/deployments/local/dev/docker-compose-openldap.yml) from Thomas Darimont's [keycloak-project-example](https://github.com/thomasdarimont/keycloak-project-example)

Kudos to Niko and Thomas for their great work!

## Demo Docker Compose Environment
There's a `docker-compose.yml` definition to use with Docker Compose. It uses the same configuration as the integration tests.

Build and run all the stuff with:
```shell
./mvnw clean package -DskipTests && docker compose up --force-recreate
```

## How to install?

Download a release (*.jar file) that works with your Keycloak version from the [list of releases](https://github.com/vaulttec/keycloak-confluence-ldap-group-mapper/releases).
Follow the below instructions depending on your distribution and runtime environment.

### Standalone (without container)

Copy the jar to the `providers` folder and execute the following command:

```shell
${kc.home.dir}/bin/kc.sh build
```

### Container image (Docker)

For Docker-based setups mount or copy the jar to `/opt/keycloak/providers`.

You may want to check [docker-compose.yml](docker-compose.yml) as an example.

