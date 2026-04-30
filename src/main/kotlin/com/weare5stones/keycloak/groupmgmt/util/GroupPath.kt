package com.weare5stones.keycloak.groupmgmt.util

import org.keycloak.models.GroupModel

/**
 * Builds the slash-delimited path for a group by walking parent links to the root.
 * For a top-level group named "Engineering" returns "/Engineering";
 * for "Engineering" → "Backend" returns "/Engineering/Backend".
 */
fun GroupModel.fullPath(): String {
    val parts = mutableListOf(name)
    var parent = parent
    while (parent != null) {
        parts.add(0, parent.name)
        parent = parent.parent
    }
    return "/" + parts.joinToString("/")
}
