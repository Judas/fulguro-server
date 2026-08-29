package com.fulgurogo.api.admin

import com.fulgurogo.common.config.Config

object AdminAccess {
    private const val ROLE_IDS_KEY = "gold.admin.role.ids"

    fun configuredRoleIds(): Set<String> = parseRoleIds(Config.getOrNull(ROLE_IDS_KEY))

    fun parseRoleIds(value: String?): Set<String> = value
        ?.split(',')
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.toSet()
        .orEmpty()

    fun isAllowed(memberRoleIds: Collection<String>, allowedRoleIds: Set<String>): Boolean =
        memberRoleIds.any(allowedRoleIds::contains)
}
