package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluencePage {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @JsonProperty
    private String id;
    @JsonProperty
    private String title;
    private String relativeUrl;
    private List<ConfluencePage> children;

    @JsonProperty("_links")
    protected void unnestLinks(JsonNode node) {
        if (node.has("tinyui")) {
            relativeUrl = node.get("tinyui").textValue();
        }
    }

    @JsonProperty("children")
    protected void unnestChildren(JsonNode node) {
        children = getChildren(node);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getRelativeUrl() {
        return relativeUrl;
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    public List<ConfluencePage> getChildren() {
        return children;
    }

    protected void setChildren(List<ConfluencePage> children) {
        this.children = children;
    }

    /* package */ static List<ConfluencePage> getChildren(JsonNode node) {
        if (node.has("page") && node.get("page").has("results")) {
            return MAPPER.convertValue(node.get("page").get("results"), new TypeReference<>() {});
        }
        return Collections.emptyList();
    }

    public static void mapChildren(ConfluencePage page, Map<String, ConfluencePage> pagesMap) {
        pagesMap.put(page.getId(), page);
        if (page.hasChildren()) {
            for (ConfluencePage child : page.getChildren()) {
                mapChildren(child, pagesMap);
            }
        }
    }
}
