<#import "template.ftl" as layout>
<@layout.emailLayout>
${kcSanitize(msg("groupInviteBodyHtml", acceptUrl, expiresAt, realmName, groupName, inviterName))?no_esc}
</@layout.emailLayout>
