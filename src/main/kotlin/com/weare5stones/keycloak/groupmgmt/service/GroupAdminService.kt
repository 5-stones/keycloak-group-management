package com.weare5stones.keycloak.groupmgmt.service

import jakarta.ws.rs.ForbiddenException
import jakarta.ws.rs.NotFoundException
import org.keycloak.models.GroupModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel

object GroupAdminService {

    private const val GROUP_ADMINS_ATTRIBUTE = "group-admins"

    fun requireGroupAdmin(session: KeycloakSession, realm: RealmModel, groupId: String, user: UserModel): GroupModel {
        val group = session.groups().getGroupById(realm, groupId)
            ?: throw NotFoundException("Group not found")
        if (!isGroupAdmin(session, realm, group, user)) {
            throw ForbiddenException("You are not an admin of this group")
        }
        return group
    }

    fun isGroupAdmin(session: KeycloakSession, realm: RealmModel, group: GroupModel, user: UserModel): Boolean {
        if (isRealmAdmin(session, realm, user)) return true
        return getAdminIds(group).contains(user.id)
    }

    fun isRealmAdmin(session: KeycloakSession, realm: RealmModel, user: UserModel): Boolean {
        val adminRole = realm.getRole("admin")
        if (adminRole != null && user.hasRole(adminRole)) return true

        val realmManagementClient = realm.getClientByClientId("realm-management")
        if (realmManagementClient != null) {
            val manageUsersRole = realmManagementClient.getRole("manage-users")
            if (manageUsersRole != null && user.hasRole(manageUsersRole)) return true
        }

        val masterRealmClient = realm.getClientByClientId("${realm.name}-realm")
        if (masterRealmClient != null) {
            val manageUsersRole = masterRealmClient.getRole("manage-users")
            if (manageUsersRole != null && user.hasRole(manageUsersRole)) return true
        }

        return false
    }

    fun promoteToAdmin(group: GroupModel, userId: String) {
        val admins = getAdminIds(group).toMutableList()
        if (!admins.contains(userId)) {
            admins.add(userId)
            group.setAttribute(GROUP_ADMINS_ATTRIBUTE, admins)
        }
    }

    fun demoteFromAdmin(group: GroupModel, userId: String) {
        val admins = getAdminIds(group).toMutableList()
        if (admins.remove(userId)) {
            group.setAttribute(GROUP_ADMINS_ATTRIBUTE, admins)
        }
    }

    fun getAdminIds(group: GroupModel): List<String> {
        return group.getAttributeStream(GROUP_ADMINS_ATTRIBUTE).toList()
    }
}
