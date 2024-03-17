package org.vaulttec.keycloak.ldap.mappers.confluence.content;

import org.keycloak.component.ComponentModel;

public class ConfluenceContentConfig {
    public static final String BASE_URL = "confluenceContent.baseUrl";
    public static final String AUTH_TOKEN = "confluenceContent.authToken";
    public static final String PARENT_PAGE_ID = "confluenceContent.parentPageId";
    public static final String PAGE_NESTING = "confluenceContent.pageNesting";
    public static final int DEFAULT_PAGE_NESTING = 3;
    public static final String SPACE_KEY = "confluenceContent.spaceKey";
    public static final String PAGE_PROPERTY_NAME = "confluenceContent.pagePropertyName";
    public static final String PAGE_LABELS = "confluenceContent.pageLabels";
    public static final String MEMBER_COLUMN_INDEX = "confluenceContent.memberColumnIndex";
    public static final int DEFAULT_MEMBER_COLUMN_INDEX = 1;
    private final ComponentModel model;

    public ConfluenceContentConfig(ComponentModel model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return model.get(BASE_URL).trim();
    }

    public String getAuthToken() {
        return model.get(AUTH_TOKEN).trim();
    }

    public String getParentPageId() {
        return model.get(PARENT_PAGE_ID).trim();
    }

    public int getPageNesting() {
        return model.get(PAGE_NESTING, DEFAULT_PAGE_NESTING);
    }

    public String getSpaceKey() {
        return model.get(SPACE_KEY).trim();
    }

    public String getPagePropertyName() {
        return model.get(PAGE_PROPERTY_NAME).trim();
    }

    public String getPageLabels() {
        return model.get(PAGE_LABELS).trim();
    }

    public int getMemberColumnIndex() {
        return model.get(MEMBER_COLUMN_INDEX, DEFAULT_MEMBER_COLUMN_INDEX);
    }
}
