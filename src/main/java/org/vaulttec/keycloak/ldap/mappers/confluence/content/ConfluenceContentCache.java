package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ConfluenceContentCache(List<ConfluencePage> pages, Map<String, ConfluencePage> pagesMap,
                                     List<ConfluencePageProperty> pageProperties) {
    public static ConfluenceContentCache of(ConfluenceContentProvider contentProvider) {
        List<ConfluencePage> pages = contentProvider.getChildPages();
        Map<String, ConfluencePage> pagesMap = new HashMap<>();
        pages.forEach(p -> ConfluencePage.mapChildren(p, pagesMap));
        List<ConfluencePageProperty> pageProperties = contentProvider.getPageProperties();
        return new ConfluenceContentCache(pages, pagesMap, pageProperties);
    }
}
