package com.weare5stones.keycloak.groupmgmt.rest

import org.keycloak.models.KeycloakSession
import org.keycloak.services.resource.RealmResourceProvider

class GroupMgmtResourceProvider(private val session: KeycloakSession) : RealmResourceProvider {

    override fun getResource(): Any {
        return GroupMgmtResource(session)
    }

    override fun close() {}
}
