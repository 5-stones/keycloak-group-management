package com.weare5stones.keycloak.groupmgmt.service

import org.jboss.logging.Logger
import org.keycloak.email.EmailTemplateProvider
import org.keycloak.models.KeycloakSession
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import java.lang.reflect.Proxy
import java.util.stream.Stream

class InvitationEmailService(private val session: KeycloakSession) {

    companion object {
        private val logger = Logger.getLogger(InvitationEmailService::class.java)
    }

    fun sendInvitationEmail(
        realm: RealmModel,
        recipientEmail: String,
        inviterName: String,
        groupName: String,
        acceptUrl: String,
        expiresAt: String
    ) {
        val smtpConfig = realm.smtpConfig
        if (smtpConfig.isNullOrEmpty()) {
            logger.warn("SMTP not configured for realm '${realm.name}', logging email instead")
            logger.info("===== GROUP INVITATION EMAIL =====")
            logger.info("To: $recipientEmail")
            logger.info("Accept URL: $acceptUrl")
            logger.info("Expires: $expiresAt")
            logger.info("==================================")
            return
        }

        // Find existing user by email, or create a lightweight proxy
        val recipientUser = session.users().searchForUserByUserAttributeStream(realm, "email", recipientEmail)
            .findFirst()
            .orElseGet { createEmailOnlyUser(recipientEmail) }

        val attributes = mapOf<String, Any>(
            "inviterName" to inviterName,
            "groupName" to groupName,
            "acceptUrl" to acceptUrl,
            "expiresAt" to expiresAt,
            "realmName" to realm.displayName.let { if (it.isNullOrBlank()) realm.name else it }
        )

        val emailTemplate = session.getProvider(EmailTemplateProvider::class.java)
        emailTemplate.setRealm(realm)
        emailTemplate.setUser(recipientUser)
        emailTemplate.send(
            "groupInvitationSubject",
            listOf(groupName),
            "group-invitation.ftl",
            attributes
        )
    }

    /**
     * Creates a minimal UserModel proxy that only provides an email address.
     * Used when sending invitations to users who don't yet exist in Keycloak.
     */
    private fun createEmailOnlyUser(email: String): UserModel {
        return Proxy.newProxyInstance(
            UserModel::class.java.classLoader,
            arrayOf(UserModel::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getEmail" -> email
                "getUsername" -> email
                "getId" -> "invitation-recipient"
                "getFirstName" -> null
                "getLastName" -> null
                "isEnabled" -> true
                "getCreatedTimestamp" -> 0L
                "getAttributes" -> emptyMap<String, List<String>>()
                "getRequiredActionsStream" -> Stream.empty<String>()
                "getGroupsStream" -> Stream.empty<Any>()
                "getRoleMappingsStream" -> Stream.empty<Any>()
                "getRealmRoleMappingsStream" -> Stream.empty<Any>()
                "toString" -> "EmailOnlyUser($email)"
                "hashCode" -> email.hashCode()
                "equals" -> false
                else -> null
            }
        } as UserModel
    }
}
