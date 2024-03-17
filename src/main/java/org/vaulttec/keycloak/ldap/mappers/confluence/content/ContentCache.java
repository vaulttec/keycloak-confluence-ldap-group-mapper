package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record ContentCache(List<ConfluencePage> pages, Map<String, ConfluencePage> pagesMap,
                           List<ConfluencePageProperty> pageProperties) {
    public static ContentCache of(ConfluenceContentProvider contentProvider) {
        List<ConfluencePage> pages = contentProvider.getPages();
        Map<String, ConfluencePage> pagesMap = new HashMap<>();
        pages.forEach(p -> ConfluencePage.mapChildren(p, pagesMap));
        List<ConfluencePageProperty> pageProperties = contentProvider.getPageProperties();
        return new ContentCache(pages, pagesMap, pageProperties);
    }
}
