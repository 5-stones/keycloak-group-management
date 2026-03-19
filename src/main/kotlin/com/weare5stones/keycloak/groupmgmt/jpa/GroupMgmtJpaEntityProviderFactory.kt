package com.weare5stones.keycloak.groupmgmt.jpa

import org.keycloak.Config
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider
import org.keycloak.connections.jpa.entityprovider.JpaEntityProviderFactory
import org.keycloak.models.KeycloakSession
import org.keycloak.models.KeycloakSessionFactory

class GroupMgmtJpaEntityProviderFactory : JpaEntityProviderFactory {

    companion object {
        const val PROVIDER_ID = "group-mgmt-entity-provider"
    }

    override fun create(session: KeycloakSession): JpaEntityProvider {
        return GroupMgmtJpaEntityProvider()
    }

    override fun getId(): String = PROVIDER_ID

    override fun init(config: Config.Scope) {}

    override fun postInit(factory: KeycloakSessionFactory) {}

    override fun close() {}
}
