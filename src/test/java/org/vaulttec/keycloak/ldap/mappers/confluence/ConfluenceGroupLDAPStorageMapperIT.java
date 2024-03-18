package org.vaulttec.keycloak.ldap.mappers.confluence;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

import static dasniko.testcontainers.keycloak.ExtendableKeycloakContainer.MASTER_REALM;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ConfluenceGroupLDAPStorageMapperIT {
    public static final String REALM = "acme";
    public static final KeycloakEnvironment KEYCLOAK_ENVIRONMENT = new KeycloakEnvironment();

    @BeforeAll
    public static void beforeAll() {
        KEYCLOAK_ENVIRONMENT.start();
    }

    @AfterAll
    public static void afterAll() {
        KEYCLOAK_ENVIRONMENT.stop();
    }

    @ParameterizedTest
    @ValueSource(strings = {MASTER_REALM, REALM})
    public void testRealms(String realm) {
        String accountServiceUrl = given().when().get(KEYCLOAK_ENVIRONMENT.getAuthServerUrl() + "/realms/" + realm)
                .then().statusCode(200).body("realm", equalTo(realm))
                .extract().path("account-service");

        given().when().get(accountServiceUrl).then().statusCode(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"dooj1:johndoo", "dooj2:janedoo", "dooj3:jimdoo"})
    public void testAccessingUsersAsAdmin(String usernameAndMailname) {
        String[] splittedParameter = usernameAndMailname.split(":");
        Keycloak kcAdmin = KEYCLOAK_ENVIRONMENT.getAdminClient();
        UsersResource usersResource = kcAdmin.realm(REALM).users();
        List<UserRepresentation> users = usersResource.search(splittedParameter[0]);
        assertThat(users, is(not(empty())));

        String userId = users.get(0).getId();
        UserResource userResource = usersResource.get(userId);
        assertThat(userResource.toRepresentation().getEmail(), is(splittedParameter[1] + "@ns-mail8.com"));

        List<GroupRepresentation> groups = userResource.groups();
        assertEquals(2, groups.size());
        assertTrue(groups.stream().allMatch(group -> "Page 1.1".equals(group.getName()) || "Page 1.2".equals(group.getName())));
    }
}