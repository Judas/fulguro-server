package com.fulgurogo.league

import com.fulgurogo.common.logger.log
import com.fulgurogo.league.LeagueModule.TAG
import com.fulgurogo.league.db.model.ExemptionReason
import com.fulgurogo.league.db.model.LeagueCandidate
import kotlin.math.abs
import kotlin.random.Random

/** One pairing, colours already drawn. Carries the candidates themselves, so the caller has the houses and ratings. */
data class Pairing(val black: LeagueCandidate, val white: LeagueCandidate, val score: Double)

/** One candidate the draw could not pair, and why. */
data class Exemption(val discordId: String, val reason: ExemptionReason)

/**
 * The outcome of a draw: who plays whom, and who sits out.
 *
 * Both halves matter, and the second one especially: the draw is the only thing that knows **why** a player is on the
 * bench, and that is what makes the perfect-attendance bonus fair. Deducing it later by comparing candidates to written
 * matches would give the right list with the wrong reason — and would assume we still knew who was a candidate, which
 * nothing records.
 */
data class Draw(val pairings: List<Pairing>, val exemptions: List<Exemption>)

/**
 * The draw. A pure function: no database, no clock, and its randomness is injected so a test can pin it.
 *
 * Two constraints and one objective. Never two members of the same house — enforced by the pairs of a same house simply
 * not existing in the graph, rather than by a weight so large that some edge case could step over it. Everyone who can
 * be paired is paired. And among the matchings that satisfy both, prefer the one minimising the total score.
 *
 * ⚠ **Two deviations from step 6 of `doc/plan-ligue.md`, both because its algorithm cannot deliver its own rules:**
 *
 * 1. The plan's signature takes only the candidates and the history, but its bench rule is "set aside among the
 *    candidates with the fewest exemptions this season, drawing lots to break ties". That needs the season's exemption
 *    counts, so they are a third parameter. Without them the rule is unimplementable, and the bench would silently fall
 *    on whoever the algorithm happened to strand — which the plan itself names as the thing to avoid, since it would be
 *    the same extreme-rated player every session.
 * 2. Greedy-then-2-opt does not pair everyone it could. The valid pairs form a complete multipartite graph, on which a
 *    greedy maximal matching is **not** maximum: with houses {A,B}, {C}, {D}, picking C-D first strands A and B, who
 *    then get exemptions although a perfect matching existed. 2-opt cannot repair it, because swapping partners never
 *    creates a pair. So the bench is chosen **first**, deliberately, and the matching of the rest is repaired up to
 *    maximum. An exemption then always means "nobody was available", never "the algorithm gave up".
 */
object LeaguePairing {
    /**
     * What meeting the same opponent again costs, in rating points — the same unit as the other term.
     *
     * 400 is **two tiers of `gold_tiers`**, which are 200 apart each, so the setting reads as a sentence: playing someone
     * already met costs as much as facing someone two tiers away. Novelty therefore beats balance by a wide margin, which
     * is the right trade over 16 sessions — someone who plays the same person three times in a year has not played a
     * league.
     */
    const val REPEAT_PENALTY = 400.0

    /**
     * Draws one session.
     *
     * [history] is how many times each pair has already met this season, keyed by [opponentKey]. [exemptions] is how many
     * sessions each player has already been benched, and drives who sits out when there is a choice.
     */
    fun draw(
        candidates: List<LeagueCandidate>,
        history: Map<Pair<String, String>, Int>,
        exemptions: Map<String, Int> = mapOf(),
        random: Random = Random.Default
    ): Draw {
        val (bench, playing) = selectBench(candidates, exemptions, random)
        val pairs = match(playing, history)

        val pairings = pairs.map { (one, other) ->
            // Colours drawn per match. Not by rating, not alternated over the season: two lines, and fair in expectation.
            val blackFirst = random.nextBoolean()
            Pairing(
                black = if (blackFirst) one else other,
                white = if (blackFirst) other else one,
                score = score(one, other, history)
            )
        }

        // The invariant that matters most, checked rather than assumed: a candidate who is neither paired nor exempted is
        // a player who loses their bonus in May without anything ever having said so.
        val accounted = pairings.size * 2 + bench.size
        if (accounted != candidates.size)
            log(TAG, "draw INVARIANT BROKEN ${candidates.size} candidates but $accounted accounted for")

        return Draw(pairings, bench)
    }

    /** The canonical key for a pair of players: the two ids in a fixed order, so colour cannot change it. */
    fun opponentKey(one: String, other: String): Pair<String, String> =
        if (one <= other) one to other else other to one

    /**
     * Chooses who sits out, before any pairing, and answers the bench and the players left.
     *
     * Removing players one at a time and re-testing is what keeps it correct: each removal changes the house
     * distribution, so the next decision cannot be taken from the original counts.
     *
     * The two reasons come from the two situations, and never overlap:
     *
     * - **A house holds more than half the players.** Its surplus cannot be paired with anyone — every possible opponent
     *   is a housemate — so the bench *must* come from that house, and the reason is [ExemptionReason.NO_RIVAL]. This is
     *   tested first, which is also how the plan's rule "keep NO_RIVAL, the more specific cause" is honoured.
     * - **An odd headcount, houses otherwise balanced.** Exactly one player is left over, it can be anyone, and the
     *   reason is [ExemptionReason.ODD].
     *
     * Within the eligible pool the pick is [pickFairly]: fewest exemptions so far, lots drawn between equals. That is
     * what stops the bench falling on the same person all season.
     *
     * When it returns, the remaining set has an even size and no house holding more than half of it, which is exactly the
     * condition for a perfect matching to exist on a complete multipartite graph.
     */
    private fun selectBench(
        candidates: List<LeagueCandidate>,
        exemptions: Map<String, Int>,
        random: Random
    ): Pair<List<Exemption>, List<LeagueCandidate>> {
        val remaining = candidates.toMutableList()
        val bench = mutableListOf<Exemption>()

        while (remaining.isNotEmpty()) {
            // The last one left needs its reason decided against the *original* field, not the arithmetic below. Someone
            // alone because everybody else is a housemate has no rival, and calling that an odd headcount would answer
            // the wrong question to the player who asks in May why they were never drawn.
            if (remaining.size == 1) {
                val last = remaining.removeAt(0)
                val hadAnyRival = candidates.any { it.houseId != last.houseId }
                bench += Exemption(
                    last.discordId,
                    if (hadAnyRival) ExemptionReason.ODD else ExemptionReason.NO_RIVAL
                )
                break
            }

            val largest = remaining.groupBy { it.houseId }.maxByOrNull { it.value.size }!!.value
            val poolAndReason = when {
                largest.size * 2 > remaining.size -> largest to ExemptionReason.NO_RIVAL
                remaining.size % 2 == 1 -> remaining.toList() to ExemptionReason.ODD
                else -> null
            } ?: break

            val (pool, reason) = poolAndReason
            val picked = pickFairly(pool, exemptions, random)
            remaining.remove(picked)
            bench += Exemption(picked.discordId, reason)
        }

        return bench to remaining
    }

    /**
     * The candidate to bench: among those exempted the fewest times this season, drawn by lot.
     *
     * Fewest, not most, and it is worth being sure of the direction: an exemption is a session without a match, so it
     * costs 7 points. Benching whoever has been benched least is what spreads that loss instead of concentrating it.
     */
    private fun pickFairly(
        pool: List<LeagueCandidate>,
        exemptions: Map<String, Int>,
        random: Random
    ): LeagueCandidate {
        val fewest = pool.minOf { exemptions[it.discordId] ?: 0 }
        return pool.filter { (exemptions[it.discordId] ?: 0) == fewest }.random(random)
    }

    /** Pairs everyone: greedy by score, repaired up to a maximum matching, then improved by 2-opt. */
    private fun match(
        players: List<LeagueCandidate>,
        history: Map<Pair<String, String>, Int>
    ): List<Pair<LeagueCandidate, LeagueCandidate>> {
        val pairs = greedy(players, history)
        val paired = pairs.flatMap { listOf(it.first.discordId, it.second.discordId) }.toSet()
        repair(pairs, players.filterNot { it.discordId in paired }.toMutableList())
        improve(pairs, history)
        return pairs
    }

    /**
     * Every valid pair by increasing score, taking each whose players are both still free.
     *
     * Ties break on the ids, so two runs on the same input give the same draw. Left to the sort's own stability the
     * result would depend on the order the database happened to return the candidates in, which is not a decision anyone
     * made.
     */
    private fun greedy(
        players: List<LeagueCandidate>,
        history: Map<Pair<String, String>, Int>
    ): MutableList<Pair<LeagueCandidate, LeagueCandidate>> {
        val scored = mutableListOf<Triple<LeagueCandidate, LeagueCandidate, Double>>()
        for (i in players.indices)
            for (j in i + 1 until players.size) {
                val one = players[i]
                val other = players[j]
                // Same-house pairs are not scored — they are not edges of the graph at all.
                if (one.houseId == other.houseId) continue
                scored += Triple(one, other, score(one, other, history))
            }

        val taken = mutableSetOf<String>()
        val pairs = mutableListOf<Pair<LeagueCandidate, LeagueCandidate>>()
        scored
            .sortedWith(compareBy({ it.third }, { it.first.discordId }, { it.second.discordId }))
            .forEach { (one, other, _) ->
                if (one.discordId !in taken && other.discordId !in taken) {
                    pairs += one to other
                    taken += one.discordId
                    taken += other.discordId
                }
            }
        return pairs
    }

    /**
     * Brings the matching up to maximum, which greedy does not reach on its own.
     *
     * Two moves, and together they are enough on this graph:
     *
     * 1. Any two unpaired players from different houses can simply be paired. Repeat until no such couple is left — at
     *    which point every remaining unpaired player is in the **same** house, by construction.
     * 2. Two unpaired housemates plus an existing pair with no member of their house become **two** pairs: break the
     *    pair, give each of its members one of the two. One pair becomes two, so the matching grows.
     *
     * When neither move applies, every remaining pair holds a member of the leftover house and every non-member is
     * already paired, which is precisely the maximum for a complete multipartite graph. Ignoring scores here is
     * deliberate: being paired at all beats being paired well, and [improve] runs afterwards to recover the difference.
     */
    private fun repair(
        pairs: MutableList<Pair<LeagueCandidate, LeagueCandidate>>,
        unpaired: MutableList<LeagueCandidate>
    ) {
        var moved = true
        while (moved) {
            moved = false
            outer@ for (i in unpaired.indices)
                for (j in i + 1 until unpaired.size) {
                    if (unpaired[i].houseId != unpaired[j].houseId) {
                        val one = unpaired[i]
                        val other = unpaired[j]
                        pairs += one to other
                        unpaired.remove(one)
                        unpaired.remove(other)
                        moved = true
                        break@outer
                    }
                }
        }

        while (unpaired.size >= 2) {
            val house = unpaired[0].houseId
            val index = pairs.indexOfFirst { it.first.houseId != house && it.second.houseId != house }
            if (index < 0) break

            val (one, other) = pairs.removeAt(index)
            pairs += unpaired.removeAt(0) to one
            pairs += unpaired.removeAt(0) to other
        }
    }

    /**
     * 2-opt: swap the opponents of two pairs whenever the total drops, until nothing moves.
     *
     * Greedy alone is not enough, and the reason is worth holding in mind before touching this. Without the penalty the
     * score is a plain distance on a line — the ratings — and the optimal matching pairs neighbours in sorted order,
     * which greedy finds naturally. It is the repeat penalty and the house exclusion that break that property: the first
     * adds a term obeying no triangle inequality, the second puts holes in the graph. And at 400 the penalty weighs
     * heavily, so the stronger it is, the more greedy alone can be wrong:
     *
     *     A-B =  20 (never met)   A-C = 210
     *     C-D = 420 (already met) B-D = 210
     *     greedy: A-B then C-D = 440    2-opt: A-C and B-D = 420
     *
     * Two invariants, both easy to break while rereading. The new total must be **strictly** lower, or the loop need not
     * terminate — there are finitely many matchings and each move strictly decreases the total, which is the whole of the
     * termination argument. And a swap must never produce a same-house pair, so the constraint is rechecked **here** and
     * not only at construction.
     *
     * A successful swap restarts the scan rather than continuing it, because the two pairs it just rewrote are the ones
     * the inner loop still holds in local variables. At twenty-odd players, restarting costs nothing.
     *
     * Not an exact matching (Blossom), on purpose: this recovers nearly all of those cases in twenty lines, stays
     * deterministic, and is tested by a trivial invariant — the total can only go down. Optimality is not guaranteed, and
     * that is accepted.
     */
    private fun improve(
        pairs: MutableList<Pair<LeagueCandidate, LeagueCandidate>>,
        history: Map<Pair<String, String>, Int>
    ) {
        var improved = true
        while (improved) {
            improved = false
            scan@ for (i in pairs.indices)
                for (j in i + 1 until pairs.size) {
                    val (a, b) = pairs[i]
                    val (c, d) = pairs[j]
                    val current = score(a, b, history) + score(c, d, history)

                    if (a.houseId != c.houseId && b.houseId != d.houseId &&
                        score(a, c, history) + score(b, d, history) < current
                    ) {
                        pairs[i] = a to c
                        pairs[j] = b to d
                        improved = true
                        break@scan
                    }

                    if (a.houseId != d.houseId && b.houseId != c.houseId &&
                        score(a, d, history) + score(b, c, history) < current
                    ) {
                        pairs[i] = a to d
                        pairs[j] = b to c
                        improved = true
                        break@scan
                    }
                }
        }
    }

    /** `|Δrating| + 400 × meetings already played`, to be minimised. Both terms in rating points. */
    private fun score(
        one: LeagueCandidate,
        other: LeagueCandidate,
        history: Map<Pair<String, String>, Int>
    ): Double = abs(one.rating - other.rating) +
            REPEAT_PENALTY * (history[opponentKey(one.discordId, other.discordId)] ?: 0)
}
