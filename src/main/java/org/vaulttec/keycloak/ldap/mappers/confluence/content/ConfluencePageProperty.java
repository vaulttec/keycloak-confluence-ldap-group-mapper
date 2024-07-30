package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfluencePageProperty {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    @JsonProperty
    private String id;
    @JsonProperty
    private List<String> details;
    private List<String> values;

    public String getId() {
        return id;
    }

    protected void setValues(int columnIndex) {
        values = new ArrayList<>();
        for (String detail : details) {
            Elements elements = Jsoup.parse(detail).selectXpath("//tr/td[position()=" + columnIndex + "]");
            elements.stream().filter(Element::hasText).forEach(e -> values.add(e.text().trim()));
        }
    }

    public List<String> getValues() {
        return values;
    }

    public static List<ConfluencePageProperty> of(JsonNode node) {
        if (node.has("detailLines")) {
            return MAPPER.convertValue(node.get("detailLines"), new TypeReference<>() {});
        }
        return Collections.emptyList();
    }
}
