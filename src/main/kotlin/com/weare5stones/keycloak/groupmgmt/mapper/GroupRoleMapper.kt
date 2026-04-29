package com.weare5stones.keycloak.groupmgmt.mapper

import com.weare5stones.keycloak.groupmgmt.service.GroupRoleService
import org.keycloak.models.ClientSessionContext
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ProtocolMapperModel
import org.keycloak.models.UserSessionModel
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper
import org.keycloak.provider.ProviderConfigProperty
import org.keycloak.representations.IDToken

private const val PROVIDER_ID = "group-mgmt-group-role-mapper"
private const val INCLUDE_GROUP_NAME = "include.group.name"

private fun buildConfigProperties(): List<ProviderConfigProperty> {
    val props = mutableListOf<ProviderConfigProperty>()

    props.add(ProviderConfigProperty().apply {
        name = OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME
        label = OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME_LABEL
        helpText = OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME_TOOLTIP
        type = ProviderConfigProperty.STRING_TYPE
        defaultValue = "group_roles"
    })

    props.add(ProviderConfigProperty().apply {
        name = INCLUDE_GROUP_NAME
        label = "Include Group Name"
        helpText = "Include the group name in each entry. If disabled, only the group ID and roles are included."
        type = ProviderConfigProperty.BOOLEAN_TYPE
        defaultValue = "true"
    })

    props.add(ProviderConfigProperty().apply {
        name = OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN
        label = OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN_LABEL
        helpText = OIDCAttributeMapperHelper.INCLUDE_IN_ID_TOKEN_HELP_TEXT
        type = ProviderConfigProperty.BOOLEAN_TYPE
        defaultValue = "true"
    })

    props.add(ProviderConfigProperty().apply {
        name = OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN
        label = OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN_LABEL
        helpText = OIDCAttributeMapperHelper.INCLUDE_IN_ACCESS_TOKEN_HELP_TEXT
        type = ProviderConfigProperty.BOOLEAN_TYPE
        defaultValue = "true"
    })

    props.add(ProviderConfigProperty().apply {
        name = OIDCAttributeMapperHelper.INCLUDE_IN_USERINFO
        label = OIDCAttributeMapperHelper.INCLUDE_IN_USERINFO_LABEL
        helpText = OIDCAttributeMapperHelper.INCLUDE_IN_USERINFO_HELP_TEXT
        type = ProviderConfigProperty.BOOLEAN_TYPE
        defaultValue = "true"
    })

    return props
}

private val CONFIG_PROPERTIES = buildConfigProperties()

class GroupRoleMapper : AbstractOIDCProtocolMapper(),
    OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    override fun getId(): String = PROVIDER_ID

    override fun getDisplayCategory(): String = TOKEN_MAPPER_CATEGORY

    override fun getDisplayType(): String = "Group Management Role"

    override fun getHelpText(): String =
        "Maps the user's group memberships and the roles they hold within each group to a token claim."

    override fun getConfigProperties(): List<ProviderConfigProperty> = CONFIG_PROPERTIES

    override fun setClaim(token: IDToken, mappingModel: ProtocolMapperModel, userSession: UserSessionModel,
                          session: KeycloakSession, clientSessionCtx: ClientSessionContext) {
        val user = userSession.user
        val realm = userSession.realm
        val includeGroupName = mappingModel.config?.get(INCLUDE_GROUP_NAME)?.toBoolean() ?: true

        val groups = user.getGroupsStream().toList()
        val rolesByGroup = GroupRoleService.getRolesForUserInGroups(
            session, realm.id, user.id, groups.map { it.id }
        )

        val groupRoles = groups.map { group ->
            val entry = mutableMapOf<String, Any>(
                "id" to group.id,
                "roles" to (rolesByGroup[group.id]?.sorted() ?: emptyList<String>())
            )
            if (includeGroupName) {
                entry["name"] = group.name
            }
            entry
        }

        val claimName = mappingModel.config?.get(OIDCAttributeMapperHelper.TOKEN_CLAIM_NAME) ?: "group_roles"
        token.otherClaims[claimName] = groupRoles
    }
}
