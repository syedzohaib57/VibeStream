package com.example.streamingappzb.domain.usecase

import com.example.streamingappzb.domain.model.Credit
import com.example.streamingappzb.domain.model.MediaType
import com.example.streamingappzb.domain.model.PersonProfile
import com.example.streamingappzb.domain.repository.MediaRepository

/**
 * A person and their filmography, grouped the way the screen lists it.
 *
 * The grouping is the reason this is a use case: TMDB returns one flat list per department,
 * and what a viewer wants is "their films" and "their series" — which is a different cut.
 */
class GetPersonUseCase(
    private val media: MediaRepository,
) {

    data class Result(
        val person: PersonProfile,
        val films: List<Credit>,
        val series: List<Credit>,
    ) {
        /** Nothing to list — a real case for crew with a single uncredited entry. */
        val hasCredits: Boolean get() = films.isNotEmpty() || series.isNotEmpty()
    }

    suspend operator fun invoke(id: Int): Result? {
        val person = media.person(id) ?: return null
        val credits = person.primaryCredits

        return Result(
            person = person,
            films = credits.filter { it.item.type == MediaType.Movie },
            series = credits.filter { it.item.type.isSeries },
        )
    }
}
