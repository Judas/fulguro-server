package com.fulgurogo.house.db.model

import com.fulgurogo.common.utilities.GenerateNoArgConstructor

/**
 * One of the four houses, with the RP the website displays.
 *
 * [slug] is the machine key: it is what the API exposes and what the website builds the crest filename from, so it is
 * stable. [name] is display-only and can change without breaking anything.
 */
@GenerateNoArgConstructor
data class House(
    val id: Int,
    val slug: String,
    val name: String,
    val tagline: String,
    /** Hex colour including the leading `#`. */
    val color: String,
    val description: String
)
