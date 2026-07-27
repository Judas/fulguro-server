package com.fulgurogo.discord

/**
 * The slice of a platform's game table that [reconcileGames] needs.
 *
 * Both writes must be idempotent and must report whether *this* call was the one that changed the row. That is what
 * makes "announce a game exactly once" a property of the database rather than an assumption about how many writers
 * there are — an assumption OGS already broke once it gained a second writer alongside the REST poller.
 */
interface GameStore<G : NotifiableGame> {
    /** The stored game with the same `gold_id` as [game], or null when it has never been seen. */
    fun storedGame(game: G): G?

    /** Inserts [game] only if absent. @return true if this call created the row. */
    fun addGame(game: G): Boolean

    /** Stamps the final result on a game still stored as unfinished. @return true if this call finished the row. */
    fun finishGame(game: G): Boolean

    /**
     * Every player id this platform tracks, as strings. One query, reused across a whole batch — this replaced two
     * per-game `user(id)` lookups.
     */
    fun trackedPlayerIds(): Set<String>

    /** [game]'s black and white ids, in the same form as [trackedPlayerIds]. */
    fun playerIds(game: G): Pair<String, String>
}

/**
 * Stores each of [games] and announces the ones this caller actually wrote.
 *
 * Games are inserted the first time they are seen, including while still unfinished, so a game in progress can be
 * announced; the result is stamped later by [GameStore.finishGame]. Only the caller whose write won gets to notify,
 * which is why both store methods return a flag instead of Unit.
 *
 * @param server platform name for the notification title, e.g. `"OGS"`.
 * @param checkAge see [GameNotifier.notify].
 */
fun <G : NotifiableGame> reconcileGames(
    games: List<G>,
    store: GameStore<G>,
    server: String,
    checkAge: Boolean = true
) {
    // Loaded at most once for the batch, and not at all on a tick where every game is already known and unchanged.
    // NONE is safe: the value never escapes this call.
    val trackedPlayerIds = lazy(LazyThreadSafetyMode.NONE) { store.trackedPlayerIds() }
    games.forEach { game -> reconcile(game, store, server, checkAge, trackedPlayerIds) }
}

/** Single-game form of [reconcileGames], for the OGS web socket which is handed one game at a time. */
fun <G : NotifiableGame> reconcileGame(
    game: G,
    store: GameStore<G>,
    server: String,
    checkAge: Boolean = true
) = reconcile(game, store, server, checkAge, lazy(LazyThreadSafetyMode.NONE) { store.trackedPlayerIds() })

private fun <G : NotifiableGame> reconcile(
    game: G,
    store: GameStore<G>,
    server: String,
    checkAge: Boolean,
    trackedPlayerIds: Lazy<Set<String>>
) {
    val stored = store.storedGame(game)
    val wrote = when {
        stored == null -> store.addGame(game)
        game.isFinished() && !stored.isFinished() -> store.finishGame(game)
        else -> false
    }
    if (!wrote) return

    // Only games between two tracked players are announced
    val (black, white) = store.playerIds(game)
    val tracked = trackedPlayerIds.value
    if (black in tracked && white in tracked) GameNotifier.notify(game, server, checkAge)
}
