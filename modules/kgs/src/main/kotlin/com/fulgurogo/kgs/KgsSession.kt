package com.fulgurogo.kgs

import com.fulgurogo.common.config.Config
import com.fulgurogo.common.logger.log
import com.fulgurogo.common.utilities.postForm
import com.fulgurogo.common.utilities.scrapResponse
import com.fulgurogo.kgs.KgsModule.TAG
import org.jsoup.nodes.Document

/**
 * The logged-in KGS web session the archive pages are read through.
 *
 * gokgs.com put `gameArchives.jsp` behind a login: a servlet form login on a `JSESSIONID` session, `user` and
 * `password` posted to `login.jsp`. One login covers every later archive page, whichever player and whichever month,
 * and KGS keeps the same `JSESSIONID` across it — logging in flips a flag on the session already in hand rather than
 * issuing a new one — so the cookies only ever need keeping, never re-reading.
 *
 * None of this reports itself in a status code. The wall answers **200** with a login page, and so does a rejected
 * password, which is why [isLoginPage] reads the body: unnoticed, an expired session parses as an archive page with no
 * games table at all, and the tick "succeeds" having imported nothing and set every rank back to "?".
 *
 * Held as an `object` rather than per-call state because the session is worth spanning ticks — one login serves until
 * KGS drops it. Not synchronized: the only caller is [KgsService], whose ticks cannot overlap.
 */
object KgsSession {
    private var cookies: Map<String, String> = mapOf()

    /**
     * Scraps [url], logging in first if KGS asks, and once more if the session turned out to be stale.
     *
     * Re-authenticating on detection rather than on a timer means the session lifetime never has to be known or
     * guessed: an expired session costs one wasted request and fixes itself.
     */
    suspend fun scrap(url: String, throttle: suspend () -> Unit): Document {
        val page = fetch(url)
        if (!page.isLoginPage()) return page

        log(TAG, "KGS session expired or absent, logging in")
        throttle()
        login()

        throttle()
        val retry = fetch(url)
        if (retry.isLoginPage()) throw Exception("KGS login refused: check kgs.login.user / kgs.login.password")
        return retry
    }

    private fun fetch(url: String): Document = scrapResponse(url, cookies).also { keep(it.cookies()) }.parse()

    private fun login() {
        val response = postForm(
            Config.get("kgs.login.url"),
            mapOf(
                "user" to Config.get("kgs.login.user"),
                "password" to Config.get("kgs.login.password")
            ),
            cookies
        )
        keep(response.cookies())

        // The login answers 200 whether it worked or not, and on success it serves the page the session was heading
        // for -- so its own body proves nothing when there was no such page. The next archive fetch is the real test.
    }

    /**
     * KGS only sets `JSESSIONID`, and only on the first request of a session, so a response with no cookies at all
     * means "keep what you have" rather than "you have been logged out".
     */
    private fun keep(fresh: Map<String, String>) {
        if (fresh.isNotEmpty()) cookies = cookies + fresh
    }

    /**
     * Whether this is the login wall rather than the page asked for.
     *
     * Keyed on the form's own action, not on the title, so it does not turn on wording KGS is free to change.
     */
    private fun Document.isLoginPage(): Boolean = select("form[action*=login.jsp]").isNotEmpty()
}
