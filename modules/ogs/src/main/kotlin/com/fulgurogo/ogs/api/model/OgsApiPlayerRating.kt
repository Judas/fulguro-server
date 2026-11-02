package com.fulgurogo.ogs.api.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor
import com.fulgurogo.common.utilities.ratingToRank
import com.google.gson.annotations.SerializedName

/**
 * `termination-api/player/{id}`.
 *
 * ⚠ [ranking] is the **overall** rank, which blends correspondence, 9x9 and 13x13 into the figure. The ladder is about
 * live 19x19, so [rank] reads that category first and only falls back to [ranking] when it has nothing to say.
 */
@GenerateNoArgConstructor
data class OgsApiPlayerRating(
    val id: Long,
    val username: String,
    val ranking: Double,
    val ratings: OgsApiRatings? = null
) {
    /** The live 19x19 rank when OGS has one for this player, the overall rank otherwise. */
    fun rank(): Double = ratings?.live19x19?.takeUnless { it.isDefault() }?.rating?.ratingToRank() ?: ranking
}

/**
 * Only the category the ladder reads. The block cannot be a `Map<String, OgsApiGlicko>`: it also carries
 * `"version": 5`, a bare number Gson would refuse to read as a rating.
 */
@GenerateNoArgConstructor
data class OgsApiRatings(
    @SerializedName("live-19x19") val live19x19: OgsApiGlicko? = null
)

@GenerateNoArgConstructor
data class OgsApiGlicko(
    val rating: Double = DEFAULT_RATING,
    val deviation: Double = DEFAULT_DEVIATION
) {
    /** The value OGS gives a category nobody has played in yet, which says nothing about the player. */
    fun isDefault(): Boolean = rating == DEFAULT_RATING && deviation == DEFAULT_DEVIATION

    companion object {
        private const val DEFAULT_RATING = 1500.0
        private const val DEFAULT_DEVIATION = 350.0
    }
}
