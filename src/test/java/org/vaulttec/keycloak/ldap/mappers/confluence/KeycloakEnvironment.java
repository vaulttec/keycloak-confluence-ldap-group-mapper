package org.vaulttec.keycloak.ldap.mappers.confluence;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.keycloak.admin.client.Keycloak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

public class KeycloakEnvironment {
    private static final Logger LOG = LoggerFactory.getLogger(KeycloakEnvironment.class);
    public static final String GLOBAL_ENV_PATH = "config/global.env";
    public static final String KEYCLOAK_IMAGE = "keycloak/keycloak:23.0.7";
    public static final String KEYCLOAK_CONFIG_CLI_IMAGE = "adorsys/keycloak-config-cli:5.10.0-23.0.1";
    public static final String OPENLDAP_IMAGE = "osixia/openldap:1.5.0";
    public static final String MOCK_SERVER_IMAGE = "mockserver/mockserver:5.15.0";

    private Network network;
    private KeycloakContainer keycloak;
    private GenericContainer<?> keycloakConfigCli;
    private GenericContainer<?> openLDAP;
    private GenericContainer<?> mockServer;

    public KeycloakContainer getKeycloak() {
        return keycloak;
    }

    public GenericContainer<?> getKeycloakConfigCli() {
        return keycloakConfigCli;
    }

    public GenericContainer<?> getOpenLDAP() {
        return openLDAP;
    }

    public GenericContainer<?> getMockServer() {
        return mockServer;
    }

    public Keycloak getAdminClient() {
        return keycloak.getKeycloakAdminClient();
    }

    public String getAuthServerUrl() {
        return keycloak.getAuthServerUrl();
    }

    public String getAdminUsername() {
        return keycloak.getAdminUsername();
    }

    public String getAdminPassword() {
        return keycloak.getAdminPassword();
    }

    public void start() {
        network = Network.newNetwork();

        openLDAP = createOpenLDAPContainer().withNetwork(network);
        openLDAP.start();
        openLDAP.followOutput(new Slf4jLogConsumer(LOG));

        mockServer = createMockServerContainer().withNetwork(network);
        mockServer.start();
        mockServer.followOutput(new Slf4jLogConsumer(LOG));

        keycloak = createKeycloakContainer().withNetwork(network);
        keycloak.start();
        keycloak.followOutput(new Slf4jLogConsumer(LOG));

        keycloakConfigCli = createKeycloakConfigCliContainer().withNetwork(network);
        keycloakConfigCli.start();
        keycloakConfigCli.followOutput(new Slf4jLogConsumer(LOG));
    }

    public void stop() {
        if (keycloak != null) {
            keycloak.stop();
        }
        if (keycloakConfigCli != null) {
            keycloakConfigCli.stop();
        }
        if (mockServer != null) {
            mockServer.stop();
        }
        if (openLDAP != null) {
            openLDAP.stop();
        }
        if (network != null) {
            network.close();
        }
    }

    public static KeycloakContainer createKeycloakContainer() {
        return createKeycloakContainer(null);
    }

    public static KeycloakContainer createKeycloakContainer(String realmImportFileName) {
        return createKeycloakContainer(KEYCLOAK_IMAGE, realmImportFileName);
    }

    public static KeycloakContainer createKeycloakContainer(String imageName, String realmImportFileName) {
        List<File> dependencies = Maven.resolver()
                .loadPomFromFile("./pom.xml")
                .resolve("org.jsoup:jsoup")
                .withoutTransitivity().asList(File.class);

        KeycloakContainer keycloakContainer;
        if (imageName != null) {
            keycloakContainer = new KeycloakContainer(imageName);
        } else {
            // building custom Keycloak docker image with additional libraries
            String customDockerFileName = "Dockerfile-Keycloak";
            ImageFromDockerfile imageFromDockerfile = new ImageFromDockerfile();
            imageFromDockerfile.withDockerfile(Paths.get(customDockerFileName));
            keycloakContainer = new KeycloakContainer();
            keycloakContainer.setImage(imageFromDockerfile);
        }
        if (realmImportFileName != null) {
            keycloakContainer.withRealmImportFile(realmImportFileName);
        }
        Map<String, String> globalEnv = readEnvFile(GLOBAL_ENV_PATH);
        keycloakContainer.withEnv(globalEnv)
                .withNetworkAliases("keycloak")
                .withEnv("DEBUG", "true")
                .withEnv("DEBUG_PORT", "*:8787")
                .withContextPath(globalEnv.getOrDefault("KC_HTTP_RELATIVE_PATH", "/"))
                .withProviderLibsFrom(dependencies)
                .withProviderClassesFrom("target/classes");
        return keycloakContainer;
    }

    public static GenericContainer<?> createKeycloakConfigCliContainer() {
        return new GenericContainer<>(KEYCLOAK_CONFIG_CLI_IMAGE)
                .withNetworkAliases("keycloak-provisioning")
                .withEnv(readEnvFile(GLOBAL_ENV_PATH))
                .withFileSystemBind("config/realms", "/config", BindMode.READ_ONLY)
                .waitingFor(Wait.forLogMessage(".*keycloak-config-cli running in.*", 1));
    }

    public static GenericContainer<?> createOpenLDAPContainer() {
        return new GenericContainer<>(OPENLDAP_IMAGE)
                .withNetworkAliases("openldap")
                .withEnv(readEnvFile(GLOBAL_ENV_PATH))
                .withFileSystemBind("./config/ldap/acme.ldif", "/tmp/ldif/acme.ldif", BindMode.READ_ONLY)
                .withCommand( "--copy-service --loglevel warning");
    }

    public static GenericContainer<?> createMockServerContainer() {
        return new GenericContainer<>(MOCK_SERVER_IMAGE)
                .withNetworkAliases("mockserver")
                .withEnv(readEnvFile(GLOBAL_ENV_PATH))
                .withFileSystemBind("./config/mock", "/config", BindMode.READ_ONLY)
                .withExposedPorts(1080);
    }

    public static Map<String, String> readEnvFile(String filePath) {
        Map<String, String> map = new HashMap<>();
        try (FileInputStream inputStream = new FileInputStream(filePath)) {
            Properties properties = new Properties();
            properties.load(inputStream);
            map.putAll(properties.entrySet()
                    .stream()
                    .collect(Collectors.toMap(e -> e.getKey().toString(),
                            e -> e.getValue().toString())));
        } catch (IOException e) {
            LOG.error("Error reading env file '" + filePath + "'", e);
        }
        return map;
    }
}
