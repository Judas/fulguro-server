package com.fulgurogo.features.exam

class ExamPlayerComparator(private val specialization: ExamSpecialization? = null) : Comparator<ExamPlayer> {
    private val specList = mutableListOf(
        ExamSpecialization.TREASURE,
        ExamSpecialization.BEAST,
        ExamSpecialization.BLACKLIST,
        ExamSpecialization.GOURMET,
        ExamSpecialization.LOST,
        ExamSpecialization.RUIN,
        ExamSpecialization.INFORMATION,
        ExamSpecialization.HEAD
    )

    init {
        if (specialization != null) specList.remove(specialization)
    }

    override fun compare(h1: ExamPlayer, h2: ExamPlayer): Int {
        // Compare with param spec firsthand
        if (specialization != null) {
            val diff = specialization.pointsCallback(h2) - specialization.pointsCallback(h1)
            if (diff > 0) return 1 else if (diff < 0) return -1
        }

        // Compare with total points
        val pointsDiff = h2.totalPoints() - h1.totalPoints()
        if (pointsDiff != 0) return pointsDiff

        // Compare with each specs
        specList.forEach {
            val diff = it.pointsCallback(h2) - it.pointsCallback(h1)
            if (diff > 0) return 1 else if (diff < 0) return -1
        }

        // If perfect equality, sort by discord ids
        return (h2.discordId.toLong() - h1.discordId.toLong())
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .coerceAtLeast(Int.MIN_VALUE.toLong())
            .toInt()
    }
}
