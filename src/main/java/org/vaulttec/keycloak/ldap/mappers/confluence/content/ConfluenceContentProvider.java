package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ConfluenceContentProvider {

    private static final Logger LOG = Logger.getLogger(ConfluenceContentProvider.class);

    private final CloseableHttpClient httpClient;
    private final ConfluenceContentConfig config;

    public ConfluenceContentProvider(KeycloakSession session, ComponentModel model) {
        this.config = new ConfluenceContentConfig(model);
        this.httpClient = session.getProvider(HttpClientProvider.class).getHttpClient();
    }

    public List<ConfluencePage> getPages() {
        try {
            SimpleHttp simpleHttp = SimpleHttp.doGet(config.getBaseUrl() + "/rest/api/content/" + config.getParentPageId() + "/child", httpClient)
                    .auth(config.getAuthToken())
                    .param("expand", "page" + ".child.page".repeat(config.getPageNesting() - 1));
            List<ConfluencePage> children = ConfluencePage.getChildren(simpleHttp.asJson());
            LOG.debugf("Retrieved %s child pages from parent page %s", children.size(), config.getParentPageId());
            return children;
        } catch (IOException e) {
            LOG.errorf(e, "Retrieving child page from %s failed", config.getBaseUrl());
        }
        return Collections.emptyList();
    }

    public List<ConfluencePageProperty> getPageProperties() {
        List<ConfluencePageProperty> pageProperties = new ArrayList<>();
        try {
            for (int pageIndex = 0, totalPages = 1; totalPages > pageIndex; pageIndex++) {
                SimpleHttp simpleHttp = SimpleHttp.doGet(config.getBaseUrl() + "/rest/masterdetail/1.0/detailssummary/lines", httpClient)
                        .auth(config.getAuthToken())
                        .param("spaceKey", config.getSpaceKey())
                        .param("cql", "type=page AND " + Arrays.stream(config.getPageLabels().split(","))
                                .map(label -> "label='" + label.trim() + "'").collect(Collectors.joining(" AND ")))
                        .param("headings", config.getPagePropertyName()).param("pageIndex", String.valueOf(pageIndex))
                        .param("pageSize", "500");
                JsonNode node = simpleHttp.asJson();
                if (node.has("totalPages") && node.has("detailLines")) {
                    totalPages = node.get("totalPages").asInt();
                    pageProperties.addAll(ConfluencePageProperty.getPageProperties(node));
                }
            }
            for (ConfluencePageProperty pageProperty : pageProperties) {
                pageProperty.setValues(config.getMemberColumnIndex());
            }
            LOG.debugf("Retrieved %s page properties from space %s", pageProperties.size(), config.getSpaceKey());
        } catch (IOException e) {
            LOG.errorf(e, "Retrieving page properties from %s failed", config.getBaseUrl());
        }
        return pageProperties;
    }
}
