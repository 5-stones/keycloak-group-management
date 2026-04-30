package com.weare5stones.keycloak.groupmgmt.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.util.UUID

@Entity
@Table(
    name = "fs_group_member_role",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_fs_group_member_role_realm_group_user_role",
            columnNames = ["realm_id", "group_id", "user_id", "role"]
        )
    ],
    indexes = [
        Index(name = "idx_fs_group_member_role_realm_user_group", columnList = "realm_id, user_id, group_id"),
        Index(name = "idx_fs_group_member_role_realm_group_role", columnList = "realm_id, group_id, role")
    ]
)
class GroupMemberRoleEntity {

    @Id
    @Column(name = "id", length = 36)
    var id: String = UUID.randomUUID().toString()

    @Column(name = "realm_id", nullable = false, length = 36)
    var realmId: String = ""

    @Column(name = "group_id", nullable = false, length = 36)
    var groupId: String = ""

    @Column(name = "user_id", nullable = false, length = 36)
    var userId: String = ""

    @Column(name = "role", nullable = false, length = 64)
    var role: String = ""

    @Column(name = "created_at", nullable = false)
    var createdAt: Long = System.currentTimeMillis()
}
