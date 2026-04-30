package com.weare5stones.keycloak.groupmgmt.entity

import com.weare5stones.keycloak.groupmgmt.util.toIsoString
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "fs_group_invitation",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_fs_group_invitation_realm_group_email",
            columnNames = ["realm_id", "group_id", "email"]
        )
    ],
    indexes = [
        Index(name = "idx_fs_group_invitation_realm_group", columnList = "realm_id, group_id"),
        Index(name = "idx_fs_group_invitation_token", columnList = "token", unique = true)
    ]
)
class GroupInvitationEntity {

    @Id
    @Column(name = "id", length = 36)
    var id: String = UUID.randomUUID().toString()

    @Column(name = "realm_id", nullable = false, length = 36)
    var realmId: String = ""

    @Column(name = "group_id", nullable = false, length = 36)
    var groupId: String = ""

    @Column(name = "email", nullable = false, length = 255)
    var email: String = ""

    @Column(name = "inviter_user_id", nullable = false, length = 36)
    var inviterUserId: String = ""

    @Column(name = "token", nullable = false, unique = true, length = 128)
    var token: String = ""

    /** Canonical comma-delimited role list (lowercase, deduped, sorted). Empty string = no roles. */
    @Column(name = "roles", nullable = false, length = 255)
    var roles: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis()

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Long = System.currentTimeMillis()

    fun getRolesList(): List<String> =
        if (roles.isBlank()) emptyList() else roles.split(',').filter { it.isNotBlank() }

    fun setRolesList(input: Collection<String>) {
        roles = input
            .map { it.lowercase().trim() }
            .filter { it.isNotEmpty() }
            .toSortedSet()
            .joinToString(",")
    }

    fun toMap(groupName: String): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "realmId" to realmId,
            "groupId" to groupId,
            "groupName" to groupName,
            "email" to email,
            "roles" to getRolesList(),
            "inviterUserId" to inviterUserId,
            "createdAt" to createdAt.toIsoString(),
            "expiresAt" to expiresAt.toIsoString()
        )
    }
}
