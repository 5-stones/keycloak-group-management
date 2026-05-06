package com.weare5stones.keycloak.groupmgmt.service

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * Minimal test-only entity that maps to Keycloak's `KEYCLOAK_GROUP` table. Only
 * includes the columns the [GroupHierarchy] helper queries; no associations or
 * extra columns. Aliased via `@Entity(name = "GroupEntity")` so HQL written
 * against Keycloak's real `org.keycloak.models.jpa.entities.GroupEntity` (also
 * named `GroupEntity` by default) resolves to this stub during unit tests.
 *
 * In production, Keycloak's own `GroupEntity` is the only `GroupEntity` mapping
 * in the persistence unit, so the same HQL targets the real schema.
 */
@Entity(name = "GroupEntity")
@Table(name = "KEYCLOAK_GROUP")
class KeycloakGroupTestEntity {

    @Id
    @Column(name = "ID", length = 36)
    var id: String = ""

    @Column(name = "NAME")
    var name: String = ""

    @Column(name = "PARENT_GROUP")
    var parentId: String = " "

    @Column(name = "REALM_ID")
    var realm: String = ""
}
