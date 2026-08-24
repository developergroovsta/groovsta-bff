package com.groovsta.content.service.impl

import com.groovsta.content.config.ContentProperties
import com.groovsta.content.dto.CompletePostUploadRequest
import com.groovsta.content.dto.CreatePostUploadRequest
import com.groovsta.content.dto.PostMediaResponse
import com.groovsta.content.dto.PostMediaUploadResponse
import com.groovsta.content.dto.PostResponse
import com.groovsta.content.dto.PostUploadResponse
import com.groovsta.content.model.ContentItemTag
import com.groovsta.content.model.Post
import com.groovsta.content.model.PostMedia
import com.groovsta.content.model.ContentStatus
import com.groovsta.content.repository.ContentItemTagRepository
import com.groovsta.content.repository.PostMediaRepository
import com.groovsta.content.repository.PostRepository
import com.groovsta.content.repository.ContentLikeRepository
import com.groovsta.content.repository.ContentCommentRepository
import com.groovsta.content.repository.SavedContentRepository
import com.groovsta.content.service.PostService
import com.groovsta.content.service.PublishedPostQueryService
import com.groovsta.identity.dto.response.ProfileSummaryResponse
import com.groovsta.identity.repository.ProfileRepository
import com.groovsta.identity.service.ProfileService
import com.groovsta.shared.notification.NotificationCommand
import com.groovsta.shared.notification.NotificationPublisher
import com.groovsta.shared.notification.NotificationTargetType
import com.groovsta.shared.notification.NotificationType
import com.groovsta.shared.storage.ObjectStorageService
import com.groovsta.shared.storage.S3StorageProperties
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@Service
class PostServiceImpl(
    private val postRepository: PostRepository,
    private val mediaRepository: PostMediaRepository,
    private val tagRepository: ContentItemTagRepository,
    private val likeRepository: ContentLikeRepository,
    private val commentRepository: ContentCommentRepository,
    private val savedContentRepository: SavedContentRepository,
    private val profileRepository: ProfileRepository,
    private val profileService: ProfileService,
    private val notificationPublisher: NotificationPublisher,
    private val storage: ObjectStorageService,
    private val storageProperties: S3StorageProperties,
    private val contentProperties: ContentProperties
) : PostService, PublishedPostQueryService {

    @Transactional
    override fun createUpload(accountId: UUID, request: CreatePostUploadRequest): PostUploadResponse {
        request.media.forEach {
            require(it.sizeBytes <= contentProperties.maxPostImageBytes) {
                "Post image exceeds the configured upload limit"
            }
        }
        val taggedProfiles = validateTaggedAccounts(request.taggedAccountIds)

        val post = postRepository.save(Post(
            ownerAccountId = accountId,
            caption = request.caption?.trim()?.takeIf(String::isNotEmpty)
        ))
        saveTags(post.id, request.taggedAccountIds, taggedProfiles)
        val media = request.media.mapIndexed { position, item ->
            val extension = item.fileName.substringAfterLast('.', "bin").lowercase().take(8)
            PostMedia(
                postId = post.id,
                objectKey = "$postPrefix/${post.id}/$position-${UUID.randomUUID()}.$extension",
                contentType = item.contentType,
                sizeBytes = item.sizeBytes,
                position = position
            )
        }
        mediaRepository.saveAll(media)

        return PostUploadResponse(
            postId = post.id,
            uploads = media.map {
                val upload = storage.createUpload(it.objectKey, it.contentType, storageProperties.uploadUrlTtl)
                PostMediaUploadResponse(
                    mediaId = it.id,
                    position = it.position,
                    objectKey = upload.objectKey(),
                    uploadUrl = upload.uploadUrl(),
                    expiresAt = upload.expiresAt()
                )
            }
        )
    }

    @Transactional
    override fun completeUpload(
        accountId: UUID,
        postId: UUID,
        request: CompletePostUploadRequest
    ): PostResponse {
        val post = owned(postId, accountId)
        require(post.status == ContentStatus.UPLOADING) { "Post upload is not awaiting completion" }
        val media = mediaRepository.findByPostIdOrderByPositionAsc(post.id)
        val expectedKeys = media.map(PostMedia::objectKey)
        require(request.objectKeys.size == expectedKeys.size && request.objectKeys.toSet() == expectedKeys.toSet()) {
            "Object keys do not match this post"
        }
        expectedKeys.forEach { require(storage.exists(it)) { "Uploaded post image was not found in S3" } }

        post.status = ContentStatus.PUBLISHED
        post.publishedAt = Instant.now()
        post.updatedAt = Instant.now()
        publishTagNotifications(post)
        return response(post, media, saved = false, liked = false, canShareToStory = true)
    }

    @Transactional(readOnly = true)
    override fun get(accountId: UUID?, postId: UUID): PostResponse {
        val post = find(postId)
        if (post.status != ContentStatus.PUBLISHED && post.ownerAccountId != accountId) notFound()
        return response(
            post,
            saved = savedState(accountId, post.id),
            liked = likedState(accountId, post.id),
            taggedAccounts = taggedAccounts(post.id),
            canShareToStory = canShareToStory(accountId, post.ownerAccountId)
        )
    }

    @Transactional(readOnly = true)
    override fun getAll(accountId: UUID?, postIds: Collection<UUID>): Map<UUID, PostResponse> {
        if (postIds.isEmpty()) return emptyMap()
        val posts = postRepository.findAllById(postIds)
        val savedIds = savedContentIds(accountId, postIds)
        val likedIds = likedContentIds(accountId, postIds)
        val publicOwnerIds = publicOwnerIds(posts.map(Post::ownerAccountId).toSet())
        val taggedAccounts = taggedAccountsByContentId(posts.map(Post::id))
        return posts.associate { post ->
            post.id to response(
                post,
                saved = accountId?.let { post.id in savedIds },
                liked = post.id in likedIds,
                taggedAccounts = taggedAccounts[post.id].orEmpty(),
                canShareToStory = canShareToStory(accountId, post.ownerAccountId, publicOwnerIds)
            )
        }
    }

    @Transactional(readOnly = true)
    override fun latestPosts(viewerAccountId: UUID?, before: Instant, limit: Int): List<PostResponse> {
        val posts = postRepository.findByStatusAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
            ContentStatus.PUBLISHED,
            before,
            PageRequest.of(0, limit.coerceIn(1, 50))
        )
        return responses(posts, viewerAccountId)
    }

    @Transactional(readOnly = true)
    override fun latestPostsByOwners(
        viewerAccountId: UUID?,
        ownerIds: Set<UUID>,
        before: Instant,
        limit: Int
    ): List<PostResponse> {
        if (ownerIds.isEmpty()) return emptyList()
        val posts = postRepository.findByStatusAndOwnerAccountIdInAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
            ContentStatus.PUBLISHED,
            ownerIds,
            before,
            PageRequest.of(0, limit.coerceIn(1, 50))
        )
        return responses(posts, viewerAccountId)
    }

    private fun owned(postId: UUID, accountId: UUID): Post = find(postId).also {
        if (it.ownerAccountId != accountId) notFound()
    }

    private fun find(postId: UUID): Post = postRepository.findById(postId).orElseThrow { notFound() }

    private fun response(post: Post, saved: Boolean?, liked: Boolean): PostResponse =
        response(
            post,
            mediaRepository.findByPostIdOrderByPositionAsc(post.id),
            saved,
            liked,
            taggedAccounts(post.id),
            canShareToStory = canShareToStory(null, post.ownerAccountId)
        )

    private fun response(
        post: Post,
        saved: Boolean?,
        liked: Boolean,
        taggedAccounts: List<ProfileSummaryResponse>,
        canShareToStory: Boolean
    ): PostResponse =
        response(post, mediaRepository.findByPostIdOrderByPositionAsc(post.id), saved, liked, taggedAccounts, canShareToStory)

    private fun response(
        post: Post,
        media: List<PostMedia>,
        saved: Boolean?,
        liked: Boolean,
        taggedAccounts: List<ProfileSummaryResponse> = taggedAccounts(post.id),
        canShareToStory: Boolean = canShareToStory(null, post.ownerAccountId)
    ) = PostResponse(
        id = post.id,
        ownerAccountId = post.ownerAccountId,
        caption = post.caption,
        status = post.status,
        media = media.map {
            PostMediaResponse(
                id = it.id,
                position = it.position,
                contentType = it.contentType,
                imageUrl = storage.createDownloadUrl(it.objectKey, storageProperties.downloadUrlTtl)
            )
        },
        taggedAccounts = taggedAccounts,
        canShareToStory = canShareToStory,
        likeCount = likeRepository.countByContentId(post.id),
        liked = liked,
        commentCount = commentRepository.countByContentId(post.id),
        publishedAt = post.publishedAt,
        createdAt = post.createdAt,
        saved = saved
    )

    private fun responses(posts: List<Post>, viewerAccountId: UUID?): List<PostResponse> {
        val savedIds = savedContentIds(viewerAccountId, posts.map(Post::id))
        val likedIds = likedContentIds(viewerAccountId, posts.map(Post::id))
        val taggedAccounts = taggedAccountsByContentId(posts.map(Post::id))
        val publicOwnerIds = publicOwnerIds(posts.map(Post::ownerAccountId).toSet())
        return posts.map { post ->
            response(
                post,
                saved = viewerAccountId?.let { post.id in savedIds },
                liked = post.id in likedIds,
                taggedAccounts = taggedAccounts[post.id].orEmpty(),
                canShareToStory = canShareToStory(viewerAccountId, post.ownerAccountId, publicOwnerIds)
            )
        }
    }

    private fun canShareToStory(viewerAccountId: UUID?, ownerAccountId: UUID): Boolean =
        viewerAccountId != null && (viewerAccountId == ownerAccountId ||
            profileRepository.findByAccountId(ownerAccountId).orElse(null)?.isPublic == true)

    private fun canShareToStory(
        viewerAccountId: UUID?,
        ownerAccountId: UUID,
        publicOwnerIds: Set<UUID>
    ): Boolean = viewerAccountId != null && (viewerAccountId == ownerAccountId || ownerAccountId in publicOwnerIds)

    private fun publicOwnerIds(ownerAccountIds: Set<UUID>): Set<UUID> =
        if (ownerAccountIds.isEmpty()) emptySet()
        else profileRepository.findByAccountIdIn(ownerAccountIds)
            .filter { it.isPublic }
            .mapTo(mutableSetOf()) { it.accountId }

    private fun validateTaggedAccounts(taggedAccountIds: List<UUID>): Map<UUID, ProfileSummaryResponse> {
        require(taggedAccountIds.distinct().size == taggedAccountIds.size) {
            "A tagged account may appear only once"
        }
        if (taggedAccountIds.isEmpty()) return emptyMap()
        val profiles = profileService.mentionableSummaries(taggedAccountIds.toSet())
        require(profiles.size == taggedAccountIds.size) {
            "Every tagged account must exist and be active"
        }
        return profiles
    }

    private fun saveTags(
        contentId: UUID,
        taggedAccountIds: List<UUID>,
        profiles: Map<UUID, ProfileSummaryResponse>
    ) {
        if (taggedAccountIds.isEmpty()) return
        tagRepository.saveAll(taggedAccountIds.mapIndexed { position, accountId ->
            ContentItemTag(
                contentId = contentId,
                taggedAccountId = accountId,
                handle = requireNotNull(profiles[accountId]).handle,
                position = position
            )
        })
    }

    private fun publishTagNotifications(post: Post) {
        tagRepository.findByContentIdOrderByPositionAsc(post.id).forEach { tag ->
            notificationPublisher.publish(
                NotificationCommand(
                    tag.taggedAccountId,
                    post.ownerAccountId,
                    NotificationType.CONTENT_TAGGED,
                    NotificationTargetType.CONTENT,
                    post.id,
                    tag.id,
                    "Tagged you in a post"
                )
            )
        }
    }

    private fun taggedAccounts(contentId: UUID): List<ProfileSummaryResponse> =
        taggedAccountsByContentId(listOf(contentId))[contentId].orEmpty()

    private fun taggedAccountsByContentId(contentIds: Collection<UUID>): Map<UUID, List<ProfileSummaryResponse>> {
        if (contentIds.isEmpty()) return emptyMap()
        val tags = tagRepository.findByContentIdInOrderByContentIdAscPositionAsc(contentIds)
        val profiles = profileService.summaries(tags.mapTo(mutableSetOf(), ContentItemTag::taggedAccountId))
        return tags.groupBy(ContentItemTag::contentId).mapValues { (_, contentTags) ->
            contentTags.mapNotNull { profiles[it.taggedAccountId] }
        }
    }

    private fun likedState(accountId: UUID?, contentId: UUID): Boolean =
        accountId != null && likeRepository.existsByContentIdAndAccountId(contentId, accountId)

    private fun likedContentIds(accountId: UUID?, contentIds: Collection<UUID>): Set<UUID> =
        if (accountId == null || contentIds.isEmpty()) emptySet()
        else likeRepository.findLikedContentIds(accountId, contentIds)

    private fun savedState(accountId: UUID?, contentId: UUID): Boolean? =
        accountId?.let { savedContentRepository.findByAccountIdAndContentId(it, contentId) != null }

    private fun savedContentIds(accountId: UUID?, contentIds: Collection<UUID>): Set<UUID> =
        if (accountId == null || contentIds.isEmpty()) emptySet()
        else savedContentRepository.findByAccountIdAndContentIdIn(accountId, contentIds)
            .mapTo(mutableSetOf()) { it.contentId }

    private val postPrefix: String
        get() = "${storageProperties.imagePrefix.trim('/')}/posts"

    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Post was not found")
}
