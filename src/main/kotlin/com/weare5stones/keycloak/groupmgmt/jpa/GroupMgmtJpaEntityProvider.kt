package com.weare5stones.keycloak.groupmgmt.jpa

import com.weare5stones.keycloak.groupmgmt.entity.GroupInvitationEntity
import com.weare5stones.keycloak.groupmgmt.entity.GroupMemberRoleEntity
import org.keycloak.connections.jpa.entityprovider.JpaEntityProvider

class GroupMgmtJpaEntityProvider : JpaEntityProvider {

    override fun getEntities(): List<Class<*>> {
        return listOf(
            GroupInvitationEntity::class.java,
            GroupMemberRoleEntity::class.java
        )
    }

    override fun getChangelogLocation(): String {
        return "META-INF/group-mgmt-changelog.xml"
    }

    override fun getFactoryId(): String {
        return GroupMgmtJpaEntityProviderFactory.PROVIDER_ID
    }

    override fun close() {}
}
