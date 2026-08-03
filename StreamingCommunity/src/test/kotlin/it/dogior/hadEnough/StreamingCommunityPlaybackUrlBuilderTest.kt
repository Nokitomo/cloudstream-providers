package it.dogior.hadEnough

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingCommunityPlaybackUrlBuilderTest {
    @Test
    fun buildsCanonicalMovieAndEpisodeQueries() {
        assertEquals("https://streamingunity.cc/it/iframe/7?canPlayFHD=1", StreamingCommunityPlaybackUrlBuilder.movie("https://streamingunity.cc/it", 7))
        assertEquals("https://streamingunity.cc/it/iframe/7?episode_id=9&next_episode=1&canPlayFHD=1", StreamingCommunityPlaybackUrlBuilder.episode("https://streamingunity.cc/it", 7, 9))
    }
}
