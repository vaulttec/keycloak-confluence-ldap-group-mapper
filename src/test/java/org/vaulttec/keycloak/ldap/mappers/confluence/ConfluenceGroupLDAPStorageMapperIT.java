package org.vaulttec.keycloak.ldap.mappers.confluence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ComponentRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.SynchronizationResultRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.storage.UserStorageProvider;

import java.util.List;

import static dasniko.testcontainers.keycloak.ExtendableKeycloakContainer.MASTER_REALM;
import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

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
        // Retrieve JSON representation of realm definition and extract account service URL
        String accountServiceUrl = given()
                .when()
                    .get(KEYCLOAK_ENVIRONMENT.getAuthServerUrl() + "/realms/" + realm)
                .then()
                    .statusCode(200).body("realm", equalTo(realm))
                .extract()
                    .path("account-service");
        // Access account service
        given()
                .when()
                    .get(accountServiceUrl)
                .then()
                    .statusCode(200);
    }

    @ParameterizedTest(name="username {0} has email {1}@ns-mail8.com")
    @CsvSource({"dooj1,johndoo", "dooj2,janedoo", "dooj3,jimdoo"})
    public void testUsersWithGroups(String username, String email) {
        Keycloak kcAdmin = KEYCLOAK_ENVIRONMENT.getAdminClient();
        UsersResource usersResource = kcAdmin.realm(REALM).users();
        // Get user representation for given username
        List<UserRepresentation> users = usersResource.search(username);
        assertThat(users, is(not(empty())));
        String userId = users.get(0).getId();
        // Retrieve user details and check user's email address
        UserResource userResource = usersResource.get(userId);
        assertThat(userResource.toRepresentation().getEmail(), is(email + "@ns-mail8.com"));
        // Retrieve user's groups and check group membership
        List<GroupRepresentation> groups = userResource.groups();
        assertEquals(2, groups.size());
        assertTrue(groups.stream().allMatch(group -> "Page 1.1".equals(group.getName()) || "Page 1.2".equals(group.getName())));
    }

    @Test
    public void testFullSync() {
        Keycloak kcAdmin = KEYCLOAK_ENVIRONMENT.getAdminClient();
        RealmResource realm = kcAdmin.realm(REALM);
        // Retrieve ID of LDAP user federation provider
        List<ComponentRepresentation> storageProviders = realm.components().query(realm.toRepresentation().getId(), UserStorageProvider.class.getName());
        assertThat(storageProviders, is(not(empty())));
        String storageProviderId = storageProviders.get(0).getId();
        // Trigger full sync of users and groups
        SynchronizationResultRepresentation syncResult = realm.userStorage().syncUsers(storageProviderId, "triggerFullSync");
        assertNotNull(syncResult);
        // Check groups hierarchy synced from Confluence
        List<GroupRepresentation> groups = realm.groups().query("Page", true);
        assertEquals(2, groups.size());
        assertEquals(2, groups.get(0).getSubGroupCount());
    }
}