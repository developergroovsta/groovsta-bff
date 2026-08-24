package com.groovsta.content.service.impl

import com.groovsta.content.model.ContentStatus
import com.groovsta.content.repository.PostRepository
import com.groovsta.shared.profile.ProfileContentStats
import com.groovsta.shared.profile.ProfileContentStatsProvider
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class PostProfileContentStatsProvider(
    private val postRepository: PostRepository
) : ProfileContentStatsProvider {

    override fun statsFor(accountId: UUID): ProfileContentStats =
        ProfileContentStats(postRepository.countByOwnerAccountIdAndStatus(accountId, ContentStatus.PUBLISHED))
}
