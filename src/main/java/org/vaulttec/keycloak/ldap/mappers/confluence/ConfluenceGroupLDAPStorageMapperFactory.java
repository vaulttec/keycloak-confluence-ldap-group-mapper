package org.vaulttec.keycloak.ldap.mappers.confluence;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.keycloak.storage.ldap.mappers.AbstractLDAPStorageMapperFactory;
import org.keycloak.storage.ldap.mappers.LDAPStorageMapper;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentCache;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentConfig;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.vaulttec.keycloak.ldap.mappers.confluence.ConfluenceGroupMapperConfig.*;
import static org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentConfig.*;

public class ConfluenceGroupLDAPStorageMapperFactory extends AbstractLDAPStorageMapperFactory {
    private static final Logger LOG = Logger.getLogger(ConfluenceGroupLDAPStorageMapperFactory.class);
    public static final String PROVIDER_ID = "confluence-group-ldap-mapper";
    private ConfluenceContentProvider contentProvider;
    private final AtomicReference<ConfluenceContentCache> contentCache = new AtomicReference<>();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getHelpText() {
        return "Used to map groups and group memberships from Confluence pages and page properties";
    }

    @Override
    public LDAPStorageMapper create(KeycloakSession session, ComponentModel model) {
        this.contentProvider = new ConfluenceContentProvider(session, model);
        return super.create(session, model);
    }

    @Override
    public void onCreate(KeycloakSession session, RealmModel realm, ComponentModel model) {
        this.contentProvider = new ConfluenceContentProvider(session, model);
        getContentCache(true);
    }

    @Override
    public void onUpdate(KeycloakSession session, RealmModel realm, ComponentModel oldModel, ComponentModel newModel) {
        this.contentProvider = new ConfluenceContentProvider(session, newModel);
        getContentCache(true);
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property().name(BASE_URL).label("Base URL").helpText("Base URL of the Confluence Server (with context path - if any)").type(ProviderConfigProperty.STRING_TYPE).required(true).add()
                .property().name(AUTH_TOKEN).label("Bearer Token").helpText("Personal access token for API authentication").type(ProviderConfigProperty.PASSWORD).secret(true).required(true).add()
                .property().name(PARENT_PAGE_ID).label("Parent Page ID").helpText("ID of parent page the child pages are retrieved from").type(ProviderConfigProperty.STRING_TYPE).required(true).add()
                .property().name(SPACE_KEY).label("Space Key").helpText("Key of Confluence space the pages are belonging to").type(ProviderConfigProperty.STRING_TYPE).required(true).add()
                .property().name(PAGE_LABELS).label("Page Label(s)").helpText("Comma-separated list of labels required for the pages").type(ProviderConfigProperty.STRING_TYPE).add()
                .property().name(PAGE_PROPERTY_NAME).label("Page Property Name").helpText("Name of page property with the table holding the group members ").type(ProviderConfigProperty.STRING_TYPE).required(true).add()
                .property().name(MEMBER_COLUMN_INDEX).label("Member Column Index").helpText("Index of table column with group members").type(ProviderConfigProperty.STRING_TYPE).defaultValue("1").required(true).add()
                .property().name(DROP_NON_EXISTING_GROUPS_DURING_SYNC).label("Drop non-existing Groups").helpText("During sync of groups from Confluence to Keycloak, we will keep just those Keycloak groups, which still exists in Confluence. Rest will be deleted.").type(ProviderConfigProperty.BOOLEAN_TYPE).defaultValue(true).add()
                .property().name(GROUPS_PATH).label("Groups Path").helpText("Keycloak group path the Confluence groups are added to").type(ProviderConfigProperty.STRING_TYPE).defaultValue(DEFAULT_GROUPS_PATH).required(true).add()
                .build();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config) throws ComponentValidationException {
        checkMandatoryConfigAttribute(BASE_URL, "Base URL", config);
        checkMandatoryConfigAttribute(AUTH_TOKEN, "Bearer Token", config);
        checkMandatoryConfigAttribute(PARENT_PAGE_ID, "Parent Page ID", config);
        checkMandatoryConfigAttribute(SPACE_KEY, "Space Key", config);
        checkMandatoryConfigAttribute(PAGE_PROPERTY_NAME, "Page Property Name", config);
        checkMandatoryConfigAttribute(MEMBER_COLUMN_INDEX, "Member Column Index", config);
        checkMandatoryConfigAttribute(GROUPS_PATH, "Groups Path", config);

        String baseUrl = new ConfluenceContentConfig(config).getBaseUrl();
        if (baseUrl.trim().endsWith("/")) {
            throw new ComponentValidationException("No trailing slash in Base URL allowed");
        }
        int memberColumnIndex = new ConfluenceContentConfig(config).getMemberColumnIndex();
        if (memberColumnIndex <= 0 || memberColumnIndex > 99) {
            throw new ComponentValidationException("Invalid member column index - must be > 0 and < 100");
        }
        String groupsPath = new ConfluenceGroupMapperConfig(config).getGroupsPath();
        if (!DEFAULT_GROUPS_PATH.equals(groupsPath) && KeycloakModelUtils.findGroupByPath(session, realm, groupsPath) == null) {
            throw new ComponentValidationException("ldapErrorMissingGroupsPathGroup");
        }
    }

    @Override
    public ConfluenceGroupLDAPStorageMapper createMapper(ComponentModel model, LDAPStorageProvider provider) {
        return new ConfluenceGroupLDAPStorageMapper(model, provider, this);
    }

    /* package */ ConfluenceContentCache getContentCache(boolean refresh) {
        if (refresh) {
            LOG.debug("Refreshing content cache");
            contentCache.set(ConfluenceContentCache.of(contentProvider));
        }
        return contentCache.get();
    }
}
