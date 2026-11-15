package com.example.firebase

object SecurityConstants {
    /**
     * Exact initially trusted Super Admin Firebase Authentication UID.
     * Enforced both in client UI routing and server-side Firestore Security Rules.
     */
    const val TRUSTED_SUPER_ADMIN_UID = "yyskhmPvvfeNl572FnVhZgYAaQx2"

    fun isTrustedSuperAdmin(uid: String?): Boolean {
        return uid != null && uid == TRUSTED_SUPER_ADMIN_UID
    }
}
