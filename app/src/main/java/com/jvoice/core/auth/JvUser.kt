package com.jvoice.core.auth

/**
 * The `users/{uid}` record.
 *
 * Deliberately a plain data class with a no-arg constructor and `var` fields:
 * Firebase's `DataSnapshot.getValue(JvUser::class.java)` needs a JavaBean, and it
 * silently returns nulls for fields it cannot set. Everything is nullable for the
 * same reason — a record written by hand in the console, or by an older version of
 * the seeding script, may be missing anything, and the app must degrade rather
 * than throw inside a deserialiser.
 *
 * The one field that is *not* allowed to be missing is [role]: [JvRole.fromCode]
 * returns null for it and sign-in fails. See [JvRole].
 *
 * ### Schema
 *
 * ```
 * users/{uid}
 *   uid            string    mirrors the key, so a record read alone still knows itself
 *   loginId        string    the bare login id (also the employee id), e.g. jv01r001 - without @jvoicetelugu.com
 *   name           string    display name
 *   email          string    the expanded address actually used to authenticate
 *   phone          string    for the password-reset callback queue
 *   role           string    one of JvRole.code
 *   isLogin        boolean   launch-time kill-switch; missing == enabled
 *   forceLogoutAt  number    server timestamp; a bump ends live sessions
 *   createdAt      number    server timestamp
 *   lastLoginAt    number    server timestamp, written on each sign-in
 *   update/force   boolean   blocks the app until updated
 *   update/version number    minimum acceptable versionCode
 * ```
 */
data class JvUser(
    var uid: String? = null,
    var loginId: String? = null,
    var name: String? = null,
    var email: String? = null,
    var phone: String? = null,
    var role: String? = null,
    var avatarUrl: String? = null,
    var isLogin: Any? = null,
    var forceLogoutAt: Long? = null,
    var createdAt: Long? = null,
    var lastLoginAt: Long? = null
) {
    /** The resolved desk role, or null if the record carries no usable role. */
    val jvRole: JvRole? get() = JvRole.fromCode(role)

    /** Display name, falling back to the login id and then the uid. */
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: loginId?.takeIf { it.isNotBlank() }
            ?: uid.orEmpty()
}
