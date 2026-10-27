package com.pritimitra.backend.community;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
public final class CommunityDtos {
    private CommunityDtos() {}
    public record GroupSummary(UUID publicId, String slug, String name, String description, boolean joined, long memberCount) {}
    public record GroupList(List<GroupSummary> items) {}
    public record MembershipReceipt(UUID groupPublicId, boolean joined) {}
    public record CreatePostRequest(String body) {}
    public record PostReceipt(UUID postPublicId, String body, OffsetDateTime createdAt) {}
    public record ReactionRequest(String reaction) {}
    public record ReactionReceipt(UUID postPublicId, String reaction) {}
    public record PostSummary(UUID postPublicId, UUID groupPublicId, String displayName, String body,
            long likeCount, long heartCount, long supportCount, String myReaction, OffsetDateTime createdAt, boolean mine) {}
    public record PostPage(List<PostSummary> items, String nextCursor) {}
}
