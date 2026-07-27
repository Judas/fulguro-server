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

    /** Whether both players are tracked players, i.e. whether the game is worth announcing. */
    fun isGoldGame(game: G): Boolean
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
) = games.forEach { game -> reconcileGame(game, store, server, checkAge) }

/** Single-game form of [reconcileGames], for the OGS web socket which is handed one game at a time. */
fun <G : NotifiableGame> reconcileGame(
    game: G,
    store: GameStore<G>,
    server: String,
    checkAge: Boolean = true
) {
    val stored = store.storedGame(game)
    val wrote = when {
        stored == null -> store.addGame(game)
        game.isFinished() && !stored.isFinished() -> store.finishGame(game)
        else -> false
    }
    if (wrote && store.isGoldGame(game)) GameNotifier.notify(game, server, checkAge)
}
