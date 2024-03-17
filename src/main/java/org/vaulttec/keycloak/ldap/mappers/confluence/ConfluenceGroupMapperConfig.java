package org.vaulttec.keycloak.ldap.mappers.confluence;

import org.keycloak.common.util.ObjectUtil;
import org.keycloak.component.ComponentModel;

import static org.keycloak.models.utils.KeycloakModelUtils.GROUP_PATH_SEPARATOR;

public class ConfluenceGroupMapperConfig {
    // During sync of groups from Confluence to Keycloak, we will keep just those Keycloak groups, which still exists in Confluence. Rest will be deleted
    public static final String DROP_NON_EXISTING_GROUPS_DURING_SYNC = "confluenceGroups.dropNonExistingGroups";

    // Keycloak group path the Confluence groups are added to (default: top level "/")
    public static final String GROUPS_PATH = "confluenceGroups.groupsPath";
    public static final String DEFAULT_GROUPS_PATH = GROUP_PATH_SEPARATOR;

    private final ComponentModel model;

    public ConfluenceGroupMapperConfig(ComponentModel model) {
        this.model = model;
    }

    public boolean isDropNonExistingGroupsDuringSync() {
        return model.get(DROP_NON_EXISTING_GROUPS_DURING_SYNC, false);
    }

    public String getGroupsPath() {
        String groupsPath = model.get(GROUPS_PATH);
        return ObjectUtil.isBlank(groupsPath) ? DEFAULT_GROUPS_PATH : groupsPath.trim();
    }

    public boolean isTopLevelGroupsPath() {
        return GROUP_PATH_SEPARATOR.equals(getGroupsPath());
    }
}
