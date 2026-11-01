package com.groovsta.trustadmin.repository

import com.groovsta.trustadmin.model.ContentReport
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentReportRepository : JpaRepository<ContentReport, UUID> {
    fun findByContentIdAndReporterAccountId(contentId: UUID, reporterAccountId: UUID): ContentReport?
    fun findByStoryIdAndReporterAccountId(storyId: UUID, reporterAccountId: UUID): ContentReport?
    fun findByStatusOrderByCreatedAt(status: String, pageable: Pageable): List<ContentReport>
    fun findAllByContentIdAndStatus(contentId: UUID, status: String): List<ContentReport>
    fun findAllByStoryIdAndStatus(storyId: UUID, status: String): List<ContentReport>
}
