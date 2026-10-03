package com.anos.myscreenoff.service

/**
 * Reads and edits the colon-separated component list Android keeps in
 * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, e.g. "pkg/pkg.Service:other/.Service".
 */
object EnabledServices {

    fun contains(list: String?, component: String): Boolean =
        entries(list).any { normalize(it) == normalize(component) }

    /** [list] with [component] added or removed, leaving every other service as it was. */
    fun with(list: String?, component: String, enabled: Boolean): String {
        val others = entries(list).filterNot { normalize(it) == normalize(component) }
        return (if (enabled) others + component else others).joinToString(":")
    }

    private fun entries(list: String?): List<String> = list.orEmpty().split(':').filter { it.isNotBlank() }

    /** Expands the short form "pkg/.Service" to "pkg/pkg.Service", as ComponentName does. */
    private fun normalize(entry: String): String {
        val slash = entry.indexOf('/')
        if (slash < 0 || entry.getOrNull(slash + 1) != '.') return entry
        val pkg = entry.substring(0, slash)
        return "$pkg/$pkg${entry.substring(slash + 1)}"
    }
}
