package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.slf4j.event.Level;

import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.mock.OpenAPIExpectation.openAPIExpectation;
import static org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentConfig.*;

public class ConfluenceContentProviderIT {
    private static ClientAndServer mockServer;
    private static ConfluenceContentProvider provider;

    @BeforeAll
    static void setup() throws Exception {
        Configuration config = new Configuration().logLevel(Level.WARN);
        mockServer = ClientAndServer.startClientAndServer(config);
        mockServer.upsert(openAPIExpectation("config/mock/confluence-openapi.yaml"));
        URL mockEndpoint = new URIBuilder("http://localhost").setPort(mockServer.getPort()).setPath("confluence").build().toURL();

        HttpClientProvider clientProvider = mock(HttpClientProvider.class);
        when(clientProvider.getHttpClient()).thenReturn(HttpClientBuilder.create().build());
        KeycloakSession session = mock(KeycloakSession.class);
        when(session.getProvider(HttpClientProvider.class)).thenReturn(clientProvider);

        ComponentModel model = mock(ComponentModel.class);
        when(model.get(BASE_URL)).thenReturn(mockEndpoint.toString());
        when(model.get(AUTH_TOKEN)).thenReturn("token");
        when(model.get(PARENT_PAGE_ID)).thenReturn("1234");
        when(model.get(PAGE_NESTING, DEFAULT_PAGE_NESTING)).thenReturn(4);
        when(model.get(SPACE_KEY)).thenReturn("TEST");
        when(model.get(PAGE_LABELS)).thenReturn("label1 , label2 , lbl-test");
        when(model.get(PAGE_PROPERTY_NAME)).thenReturn("Team");
        when(model.get(MEMBER_COLUMN_INDEX, DEFAULT_MEMBER_COLUMN_INDEX)).thenReturn(1);
        provider = new ConfluenceContentProvider(session, model);
    }

    @AfterAll
    static void shutdown() {
        mockServer.stop();
    }

    @Test
    public void testGetPagesWithTitle() {
        List<ConfluencePage> pages = provider.getPages();
        assertNotNull(pages);
        assertEquals(2, pages.size());
        assertEquals("Page 1", pages.get(0).getTitle());
        assertEquals(2, pages.get(0).getChildren().size());
        assertEquals("Page 1.1", pages.get(0).getChildren().get(0).getTitle());
        assertEquals("Page 1.2", pages.get(0).getChildren().get(1).getTitle());
        assertEquals("Page 2", pages.get(1).getTitle());
    }

    @Test
    public void testGetPagePropertiesWithValues() {
        List<ConfluencePageProperty> properties = provider.getPageProperties();
        assertNotNull(properties);
        assertEquals(2, properties.size());
        assertEquals(3, properties.get(0).getValues().size());
        assertEquals("John Doo", properties.get(0).getValues().get(0));
        assertEquals("Doo, Jim", properties.get(0).getValues().get(1));
        assertEquals("Jane Doo", properties.get(0).getValues().get(2));
        assertEquals(3, properties.get(1).getValues().size());
        assertEquals("Jane Doo", properties.get(1).getValues().get(0));
        assertEquals("Doo, John", properties.get(1).getValues().get(1));
        assertEquals("Jim Doo", properties.get(1).getValues().get(2));
    }
}