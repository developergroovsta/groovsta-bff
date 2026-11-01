package com.groovsta.identity.repository

import com.groovsta.identity.model.CloseFriend
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CloseFriendRepository : JpaRepository<CloseFriend, UUID> {
    fun existsByOwnerAccountIdAndFriendAccountId(ownerAccountId: UUID, friendAccountId: UUID): Boolean
    fun deleteByOwnerAccountIdAndFriendAccountId(ownerAccountId: UUID, friendAccountId: UUID)
    fun findByOwnerAccountIdOrderByCreatedAtDesc(ownerAccountId: UUID): List<CloseFriend>
}
