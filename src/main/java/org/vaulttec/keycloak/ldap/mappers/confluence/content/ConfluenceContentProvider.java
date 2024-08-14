package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.component.ComponentModel;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.models.KeycloakSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConfluenceContentProvider {

    private static final Logger LOG = Logger.getLogger(ConfluenceContentProvider.class);

    private final CloseableHttpClient httpClient;
    private final ConfluenceContentConfig config;

    public ConfluenceContentProvider(KeycloakSession session, ComponentModel model) {
        this.config = new ConfluenceContentConfig(model);
        this.httpClient = session.getProvider(HttpClientProvider.class).getHttpClient();
    }

    public List<ConfluencePage> getChildPages() {
        return getChildPages(config.getParentPageId());
    }

    private List<ConfluencePage> getChildPages(String pageId) {
        SimpleHttp simpleHttp = SimpleHttp.doGet(config.getBaseUrl() + "/rest/api/content/" + pageId + "/child/page", httpClient)
                .auth(config.getAuthToken())
                .param("expand", "children.page")
                .param("limit", "100");
        try (SimpleHttp.Response response = simpleHttp.asResponse()) {
            if (response.getStatus() == 200) {
                List<ConfluencePage> children = ConfluencePage.of(response.asJson());
                LOG.debugf("Retrieved %s child pages from parent page %s", children.size(), pageId);
                // Recursively retrieve grand-grand child pages from grand child pages
                for (ConfluencePage child : children) {
                    for (ConfluencePage grandChild : child.getChildren()) {
                        grandChild.setChildren(getChildPages(grandChild.getId()));
                    }
                }
                return children;
            } else {
                throw new IOException(response.asJson().toString());
            }
        } catch (IOException e) {
            LOG.errorf(e, "Retrieving child pages of %s from %s failed: %s", pageId, config.getBaseUrl(), e.getMessage());
        }
        return Collections.emptyList();
    }

    public List<ConfluencePageProperty> getPageProperties() {
        List<ConfluencePageProperty> pageProperties = new ArrayList<>();
        for (int pageIndex = 0, totalPages = 1; totalPages > pageIndex; pageIndex++) {
            SimpleHttp simpleHttp = SimpleHttp.doGet(config.getBaseUrl() + "/rest/masterdetail/1.0/detailssummary/lines", httpClient)
                    .auth(config.getAuthToken())
                    .param("spaceKey", config.getSpaceKey())
                    .param("cql", "type=page AND " + Arrays.stream(config.getPageLabels().split(","))
                            .map(label -> "label='" + label.trim() + "'").collect(Collectors.joining(" AND ")))
                    .param("headings", config.getPagePropertyName())
                    .param("pageIndex", String.valueOf(pageIndex))
                    .param("pageSize", "500");
            try (SimpleHttp.Response response = simpleHttp.asResponse()) {
                if (response.getStatus() == 200) {
                    JsonNode node = response.asJson();
                    if (node.has("totalPages")) {
                        totalPages = node.get("totalPages").asInt();
                        pageProperties.addAll(ConfluencePageProperty.of(node));
                    }
                } else {
                    throw new IOException(response.asJson().toString());
                }
            } catch (IOException e) {
                LOG.errorf(e, "Retrieving page properties from %s failed: %s", config.getBaseUrl(), e.getMessage());
                return Collections.emptyList();
            }
        }
        for (ConfluencePageProperty pageProperty : pageProperties) {
            pageProperty.setValues(config.getMemberColumnIndex());
        }
        LOG.debugf("Retrieved %s page properties from space %s", pageProperties.size(), config.getSpaceKey());
        return pageProperties;
    }
}
