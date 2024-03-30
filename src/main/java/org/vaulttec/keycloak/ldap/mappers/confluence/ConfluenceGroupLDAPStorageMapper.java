package org.vaulttec.keycloak.ldap.mappers.confluence;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.GroupModel;
import org.keycloak.models.ModelException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.UserModelDelegate;
import org.keycloak.storage.ldap.LDAPStorageProvider;
import org.keycloak.storage.ldap.idm.model.LDAPObject;
import org.keycloak.storage.ldap.idm.query.internal.LDAPQuery;
import org.keycloak.storage.ldap.mappers.AbstractLDAPStorageMapper;
import org.keycloak.storage.user.SynchronizationResult;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentCache;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluenceContentConfig;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluencePage;
import org.vaulttec.keycloak.ldap.mappers.confluence.content.ConfluencePageProperty;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class ConfluenceGroupLDAPStorageMapper extends AbstractLDAPStorageMapper {
    private static final Logger LOG = Logger.getLogger(ConfluenceGroupLDAPStorageMapper.class);
    public static final String ATTRIBUTE_CONFLUENCE_PAGE_URL = "confluencePageURL";
    private final ConfluenceGroupMapperConfig mapperConfig;
    private final ConfluenceContentConfig contentConfig;
    private final ConfluenceGroupLDAPStorageMapperFactory factory;
    // Flag to avoid syncing multiple times per transaction
    private boolean syncFromConfluencePerformedInThisTransaction = false;

    public ConfluenceGroupLDAPStorageMapper(ComponentModel model, LDAPStorageProvider provider, ConfluenceGroupLDAPStorageMapperFactory factory) {
        super(model, provider);
        this.mapperConfig = new ConfluenceGroupMapperConfig(model);
        this.contentConfig = new ConfluenceContentConfig(model);
        this.factory = factory;
    }

    @Override
    public SynchronizationResult syncDataFromFederationProviderToKeycloak(RealmModel realm) {
        SynchronizationResult syncResult = new SynchronizationResult() {

            @Override
            public String getStatus() {
                return String.format("%d imported groups, %d updated groups, %d removed groups", getAdded(), getUpdated(), getRemoved());
            }

        };
        LOG.debugf("Importing groups from Confluence into Keycloak DB. Mapper is [%s], LDAP provider is [%s]", mapperModel.getName(), ldapProvider.getModel().getName());
        ConfluenceContentCache contentCache = factory.getContentCache(true);
        updateKeycloakGroups(realm, contentCache, syncResult);
        syncFromConfluencePerformedInThisTransaction = true;
        return syncResult;
    }

    private void updateKeycloakGroups(RealmModel realm, ConfluenceContentCache contentCache, SynchronizationResult syncResult) {
        Set<String> visitedGroupIds = new HashSet<>();
        for (ConfluencePage page : contentCache.pages()) {
            updateKeycloakGroup(realm, page, null, syncResult, visitedGroupIds);
        }
        if (mapperConfig.isDropNonExistingGroupsDuringSync()) {
            dropNonExistingKcGroups(realm, syncResult, visitedGroupIds);
        }
    }

    private void updateKeycloakGroup(RealmModel realm, ConfluencePage page, GroupModel kcParent, SynchronizationResult syncResult, Set<String> visitedGroupIds) {
        String groupName = page.getTitle();

        // Check if group already exists
        GroupModel kcGroup = getKcSubGroups(realm, kcParent)
                .filter(g -> Objects.equals(g.getName(), groupName)).findFirst().orElse(null);
        if (kcGroup != null) {
            LOG.debugf("Updated Keycloak group '%s' from Confluence", kcGroup.getName());
            syncResult.increaseUpdated();
        } else {
            kcGroup = createKcGroup(realm, groupName, kcParent);
            if (kcGroup.getParent() == null) {
                LOG.debugf("Imported top-level group '%s' from Confluence", kcGroup.getName());
            } else {
                LOG.debugf("Imported group '%s' from Confluence as child of group '%s'", kcGroup.getName(), kcGroup.getParent().getName());
            }
            syncResult.increaseAdded();
        }
        updateAttributesOfKCGroup(kcGroup, page);
        visitedGroupIds.add(kcGroup.getId());

        if (page.hasChildren()) {
            for (ConfluencePage child : page.getChildren()) {
                updateKeycloakGroup(realm, child, kcGroup, syncResult, visitedGroupIds);
            }
        }
    }

    private void updateAttributesOfKCGroup(GroupModel kcGroup, ConfluencePage page) {
        kcGroup.setSingleAttribute(ATTRIBUTE_CONFLUENCE_PAGE_URL, contentConfig.getBaseUrl() + page.getRelativeUrl());
    }

    private void dropNonExistingKcGroups(RealmModel realm, SynchronizationResult syncResult, Set<String> visitedGroupIds) {
        GroupModel parent = getKcGroupsPathGroup(realm);
        getAllKcGroups(realm, parent)
                .filter(kcGroup -> !visitedGroupIds.contains(kcGroup.getId()))
                .forEach(kcGroup -> {
                    LOG.debugf("Removing Keycloak group '%s', which doesn't exist in Confluence", kcGroup.getName());
                    realm.removeGroup(kcGroup);
                    syncResult.increaseRemoved();
                });
    }

    @Override
    public UserModel proxy(LDAPObject ldapUser, UserModel delegate, RealmModel realm) {
        return new UserModelDelegate(delegate) {

            @Override
            public void leaveGroup(GroupModel group) {
                if (group.getFirstAttribute(ATTRIBUTE_CONFLUENCE_PAGE_URL) != null) {
                    throw new ModelException("Not possible to leave group maintained by Confluence mapper");
                } else {
                    super.leaveGroup(group);
                }
            }
        };
    }

    @Override
    public void onImportUserFromLDAP(LDAPObject ldapUser, UserModel user, RealmModel realm, boolean isCreate) {
        String lastName = ldapUser.getAttributeAsString("sn");
        if (lastName != null) {
            String firstName = getFirstName(ldapUser, lastName);
            if (firstName != null) {
                List<ConfluencePage> mappedPages = getMappedPages(firstName, lastName);
                if (!mappedPages.isEmpty()) {
                    GroupModel parent = getKcGroupsPathGroup(realm);
                    for (ConfluencePage mappedPage : mappedPages) {
                        GroupModel mappedGroup = findKcGroupOrSyncFromConfluence(realm, mappedPage, parent);
                        if (mappedGroup != null && !user.isMemberOf(mappedGroup)) {
                            LOG.debugf("User '%s' joins Confluence group '%s' during import from LDAP", user.getUsername(), mappedGroup.getName());
                            user.joinGroup(mappedGroup);
                        }
                    }
                }
            }
        }
    }

    private static String getFirstName(LDAPObject ldapUser, String lastName) {
        String firstName = ldapUser.getAttributeAsString("givenName");
        if (firstName == null) {
            // Retrieve first name from common name
            String commonName = ldapUser.getAttributeAsString("cn");
            if (commonName != null) {
                if (commonName.startsWith(lastName + ",")) {
                    firstName = commonName.substring(lastName.length() + 1).trim();
                } else if (commonName.endsWith(lastName)) {
                    firstName = commonName.substring(0, commonName.length() - lastName.length()).trim();
                }
            }
        }
        return firstName;
    }

    private GroupModel findKcGroupOrSyncFromConfluence(RealmModel realm, ConfluencePage page, GroupModel parent) {
        GroupModel group = getGroupByConfluencePage(realm, page, parent);
        if (group == null && !syncFromConfluencePerformedInThisTransaction) {
            syncDataFromFederationProviderToKeycloak(realm);
            group = getGroupByConfluencePage(realm, page, parent);
        }
        return group;
    }

    private GroupModel getGroupByConfluencePage(RealmModel realm, ConfluencePage page, GroupModel parent) {
        return getAllKcGroups(realm, parent)
                .filter(g -> g.getName().equals(page.getTitle())).findFirst().orElse(null);
    }

    private List<ConfluencePage> getMappedPages(String firstName, String lastName) {
        List<ConfluencePage> mappingPages = new ArrayList<>();
        ConfluenceContentCache contentCache = factory.getContentCache(false);
        List<ConfluencePageProperty> userPageProperties = contentCache.pageProperties().stream()
                .filter(p -> p.getValues().stream()
                        .map(normalizeUsername())
                        .anyMatch(username -> username.startsWith(firstName) && username.endsWith(lastName))).toList();
        if (!userPageProperties.isEmpty()) {
            for (ConfluencePageProperty userPageProperty : userPageProperties) {
                ConfluencePage page = contentCache.pagesMap().get(userPageProperty.getId());
                if (page != null) {
                    mappingPages.add(page);
                }
            }
        }
        return mappingPages;
    }

    /**
     * If username specified as "<last name>, <first name>" then convert to "<first name> <last name>".
     */
    public static Function<String, String> normalizeUsername() {
        return username -> {
            int delimiterPos = username.indexOf(",");
            if (delimiterPos >= 0) {
                return username.substring(delimiterPos + 1).trim() + " " + username.substring(0, delimiterPos).trim();
            }
            return username;
        };
    }

    @Override
    public void onRegisterUserToLDAP(LDAPObject ldapUser, UserModel localUser, RealmModel realm) {
    }

    @Override
    public void beforeLDAPQuery(LDAPQuery query) {
    }

    // Confluence groups path operations - copied from org.keycloak.storage.ldap.mappers.membership.group.GroupLDAPStorageMapper

    /**
     * Provides KC group defined as groups path or null (top-level group) if corresponding group is not available.
     */
    private GroupModel getKcGroupsPathGroup(RealmModel realm) {
        return mapperConfig.isTopLevelGroupsPath() ? null : KeycloakModelUtils.findGroupByPath(session, realm, mapperConfig.getGroupsPath());
    }

    /**
     * Creates a new KC group from given Confluence group name in given KC parent group or the groups path.
     */
    private GroupModel createKcGroup(RealmModel realm, String confluenceGroupName, GroupModel parentGroup) {

        // If no parent group given then use groups path
        if (parentGroup == null) {
            parentGroup = getKcGroupsPathGroup(realm);
        }
        return realm.createGroup(confluenceGroupName, parentGroup);
    }

    /**
     * Provides a list of all KC sub groups from given parent group or from groups path.
     */
    private Stream<GroupModel> getKcSubGroups(RealmModel realm, GroupModel parentGroup) {

        // If no parent group given then use groups path
        if (parentGroup == null) {
            parentGroup = getKcGroupsPathGroup(realm);
        }
        return parentGroup == null ? session.groups().getTopLevelGroupsStream(realm) :
                parentGroup.getSubGroupsStream();
    }

    /**
     * Provides a stream of all KC groups (with their sub groups) from groups path configured by the "Groups Path" configuration property.
     */
    private Stream<GroupModel> getAllKcGroups(RealmModel realm, GroupModel topParentGroup) {
        Stream<GroupModel> allGroups = realm.getGroupsStream();
        if (topParentGroup == null) return allGroups;

        return allGroups.filter(group -> {
            // Check if group is descendant of the topParentGroup (which is group configured by "Groups Path")
            GroupModel parent = group.getParent();
            while (parent != null) {
                if (parent.getId().equals(topParentGroup.getId())) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        });
    }
}
