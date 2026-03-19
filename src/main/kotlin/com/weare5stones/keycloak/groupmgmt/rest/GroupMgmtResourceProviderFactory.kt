package com.weare5stones.keycloak.groupmgmt.rest

import org.keycloak.Config
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory
import org.keycloak.services.resource.RealmResourceProvider
import org.keycloak.services.resource.RealmResourceProviderFactory

class GroupMgmtResourceProviderFactory : RealmResourceProviderFactory {

    companion object {
        const val PROVIDER_ID = "group-mgmt"
    }

    override fun create(session: KeycloakSession): RealmResourceProvider {
        return GroupMgmtResourceProvider(session)
    }

    override fun getId(): String = PROVIDER_ID

    override fun init(config: Config.Scope) {}

    override fun postInit(factory: KeycloakSessionFactory) {}

    override fun close() {}
}
