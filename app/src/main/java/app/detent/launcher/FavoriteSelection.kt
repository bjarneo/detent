package app.detent.launcher

internal object FavoriteSelection {
    const val LIMIT = 5

    fun toggle(keys: List<String>, key: String): List<String> = when {
        key in keys -> keys.filterNot { it == key }
        keys.size < LIMIT -> keys + key
        else -> keys
    }
}
