package com.pritimitra.backend.community;

import com.pritimitra.backend.common.ApiException;
import com.pritimitra.backend.common.PublicIdCursor;
import com.pritimitra.backend.community.CommunityDtos.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CommunityService {
    static final int MAX_PAGE_SIZE = 50;
    private final JdbcClient jdbc;
    CommunityService(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    GroupList groups(String sub) {
        UUID viewer = activeViewerId(sub);
        List<GroupSummary> items = jdbc.sql("""
                SELECT g.public_id, g.slug, g.name, g.description,
                  EXISTS (SELECT 1 FROM community_group_member m WHERE m.group_id=g.id AND m.user_id=:viewer AND m.status='ACTIVE') AS joined,
                  (SELECT count(*) FROM community_group_member m WHERE m.group_id=g.id AND m.status='ACTIVE') AS member_count
                FROM community_group g WHERE g.is_active ORDER BY g.slug
                """).param("viewer", viewer)
                .query((rs,n) -> new GroupSummary(rs.getObject("public_id",UUID.class),rs.getString("slug"),
                    rs.getString("name"),rs.getString("description"),rs.getBoolean("joined"),rs.getLong("member_count"))).list();
        return new GroupList(items);
    }

    @Transactional
    MembershipReceipt join(String sub, UUID groupPublicId) {
        UUID viewer=activeViewerId(sub), group=groupId(groupPublicId);
        jdbc.sql("""
                INSERT INTO community_group_member(group_id,user_id,status,joined_at) VALUES(:group,:viewer,'ACTIVE',now())
                ON CONFLICT(group_id,user_id) DO UPDATE SET status='ACTIVE',joined_at=now()
                """).param("group",group).param("viewer",viewer).update();
        return new MembershipReceipt(groupPublicId,true);
    }

    @Transactional
    void leave(String sub, UUID groupPublicId) {
        UUID viewer=activeViewerId(sub), group=groupIdOrNull(groupPublicId);
        if(group==null)return;
        jdbc.sql("UPDATE community_group_member SET status='LEFT' WHERE group_id=:group AND user_id=:viewer AND status='ACTIVE'")
            .param("group",group).param("viewer",viewer).update();
    }

    @Transactional(readOnly = true)
    PostPage posts(String sub, UUID groupPublicId, String cursor, int size) {
        UUID viewer=activeViewerId(sub);
        if(size<1||size>MAX_PAGE_SIZE)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_SIZE","Page size must be between 1 and 50");
        UUID group=groupId(groupPublicId); requireMember(viewer,group);
        UUID after=PublicIdCursor.decode(cursor);
        if(after!=null) {
            boolean exists=jdbc.sql("SELECT EXISTS(SELECT 1 FROM community_post WHERE public_id=:cursor AND group_id=:group)")
                .param("cursor",after).param("group",group).query(Boolean.class).single();
            if(!exists)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_CURSOR","Pagination cursor is invalid");
        }
        List<PostSummary> rows=jdbc.sql("""
                SELECT p.public_id AS post_public_id,g.public_id AS group_public_id,
                  COALESCE(profile.display_name,'Former member') AS display_name,p.body,p.created_at,
                  COALESCE(p.author_id=:viewer,false) AS mine,
                  count(r.post_id) FILTER(WHERE r.reaction='LIKE') AS like_count,
                  count(r.post_id) FILTER(WHERE r.reaction='HEART') AS heart_count,
                  count(r.post_id) FILTER(WHERE r.reaction='SUPPORT') AS support_count,
                  max(r.reaction) FILTER(WHERE r.user_id=:viewer) AS my_reaction
                FROM community_post p JOIN community_group g ON g.id=p.group_id
                LEFT JOIN onboarding_profile profile ON profile.user_id=p.author_id
                LEFT JOIN community_post_reaction r ON r.post_id=p.id
                WHERE p.group_id=:group AND p.status='PUBLISHED'
                  AND (p.author_id IS NULL OR EXISTS(SELECT 1 FROM app_user author
                    WHERE author.id=p.author_id AND author.status='ACTIVE'))
                  AND (p.author_id IS NULL OR NOT EXISTS(SELECT 1 FROM user_block b
                    WHERE (b.blocker_id=:viewer AND b.blocked_id=p.author_id)
                       OR (b.blocker_id=p.author_id AND b.blocked_id=:viewer)))
                  AND (CAST(:cursor AS uuid) IS NULL OR (p.created_at,p.public_id) <
                      (SELECT c.created_at,c.public_id FROM community_post c
                       WHERE c.public_id=CAST(:cursor AS uuid) AND c.group_id=:group))
                GROUP BY p.id,g.public_id,profile.display_name,p.author_id
                ORDER BY p.created_at DESC,p.public_id DESC LIMIT :limit
                """).param("viewer",viewer).param("group",group).param("cursor",after,Types.OTHER).param("limit",size+1)
                .query((rs,n)->postSummary(rs)).list();
        if(rows.size()<=size)return new PostPage(rows,null);
        List<PostSummary> page=rows.subList(0,size);
        return new PostPage(page,PublicIdCursor.encode(page.get(page.size()-1).postPublicId()));
    }

    @Transactional
    PostReceipt createPost(String sub, UUID groupPublicId, CreatePostRequest request) {
        UUID viewer=activeViewerId(sub), group=groupId(groupPublicId); requireMember(viewer,group);
        String body=normalizeBody(request==null?null:request.body());
        return jdbc.sql("INSERT INTO community_post(group_id,author_id,body) VALUES(:group,:viewer,:body) RETURNING public_id,body,created_at")
            .param("group",group).param("viewer",viewer).param("body",body)
            .query((rs,n)->new PostReceipt(rs.getObject("public_id",UUID.class),rs.getString("body"),rs.getObject("created_at",java.time.OffsetDateTime.class))).single();
    }

    @Transactional
    void removePost(String sub, UUID publicId) {
        UUID viewer=activeViewerId(sub);
        jdbc.sql("UPDATE community_post SET status='REMOVED',updated_at=now() WHERE public_id=:id AND author_id=:viewer AND status<>'REMOVED'")
            .param("id",publicId).param("viewer",viewer).update();
        jdbc.sql("DELETE FROM community_post_reaction r USING community_post p WHERE r.post_id=p.id AND p.public_id=:id AND p.status='REMOVED'")
            .param("id",publicId).update();
    }

    @Transactional
    ReactionReceipt react(String sub, UUID publicId, ReactionRequest request) {
        UUID viewer=activeViewerId(sub);
        String reaction=normalizeReaction(request==null?null:request.reaction());
        PostTarget post=visiblePost(viewer,publicId);
        jdbc.sql("""
                INSERT INTO community_post_reaction(post_id,user_id,reaction) VALUES(:post,:viewer,:reaction)
                ON CONFLICT(post_id,user_id) DO UPDATE SET reaction=EXCLUDED.reaction,created_at=now()
                """).param("post",post.id()).param("viewer",viewer).param("reaction",reaction).update();
        return new ReactionReceipt(publicId,reaction);
    }

    @Transactional
    void removeReaction(String sub, UUID publicId) {
        UUID viewer=activeViewerId(sub); PostTarget post=visiblePost(viewer,publicId);
        jdbc.sql("DELETE FROM community_post_reaction WHERE post_id=:post AND user_id=:viewer")
            .param("post",post.id()).param("viewer",viewer).update();
    }

    PostTarget visiblePost(UUID viewer, UUID publicId) {
        return jdbc.sql("""
                SELECT p.id,p.group_id,p.author_id FROM community_post p
                JOIN community_group_member gm ON gm.group_id=p.group_id AND gm.user_id=:viewer AND gm.status='ACTIVE'
                WHERE p.public_id=:id AND p.status='PUBLISHED'
                  AND (p.author_id IS NULL OR EXISTS(SELECT 1 FROM app_user author
                    WHERE author.id=p.author_id AND author.status='ACTIVE'))
                  AND (p.author_id IS NULL OR NOT EXISTS(SELECT 1 FROM user_block b
                    WHERE (b.blocker_id=:viewer AND b.blocked_id=p.author_id)
                       OR (b.blocker_id=p.author_id AND b.blocked_id=:viewer)))
                """).param("viewer",viewer).param("id",publicId)
            .query((rs,n)->new PostTarget(rs.getObject("id",UUID.class),rs.getObject("group_id",UUID.class),rs.getObject("author_id",UUID.class)))
            .optional().orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"POST_NOT_FOUND","Post not found"));
    }

    private UUID groupId(UUID publicId) {
        UUID id=groupIdOrNull(publicId);
        if(id==null)throw new ApiException(HttpStatus.NOT_FOUND,"COMMUNITY_NOT_FOUND","Community not found");
        return id;
    }
    private UUID groupIdOrNull(UUID publicId) {
        return jdbc.sql("SELECT id FROM community_group WHERE public_id=:id AND is_active").param("id",publicId).query(UUID.class).optional().orElse(null);
    }
    private void requireMember(UUID viewer, UUID group) {
        boolean member=jdbc.sql("SELECT EXISTS(SELECT 1 FROM community_group_member WHERE group_id=:group AND user_id=:viewer AND status='ACTIVE')")
            .param("group",group).param("viewer",viewer).query(Boolean.class).single();
        if(!member)throw new ApiException(HttpStatus.NOT_FOUND,"COMMUNITY_NOT_FOUND","Community not found");
    }
    private UUID activeViewerId(String sub) {
        return jdbc.sql("SELECT id FROM app_user WHERE cognito_sub=:sub AND status='ACTIVE'").param("sub",sub).query(UUID.class).optional()
            .orElseThrow(()->new ApiException(HttpStatus.FORBIDDEN,"ACCOUNT_NOT_ELIGIBLE","Community is available only to active accounts"));
    }
    private static String normalizeBody(String raw) {
        if(raw==null)throw invalidPost("Post body is required");
        String body=raw.trim();
        if(body.isEmpty()||body.codePointCount(0,body.length())>2000)throw invalidPost("Post body must contain between 1 and 2000 characters");
        if(body.codePoints().anyMatch(cp->cp==0||(Character.isISOControl(cp)&&cp!='\n'&&cp!='\t')))throw invalidPost("Post body contains unsupported control characters");
        return body;
    }
    private static String normalizeReaction(String raw) {
        if(raw==null)throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REACTION","Reaction is required");
        String reaction=raw.trim().toUpperCase(Locale.ROOT);
        if(!Set.of("LIKE","HEART","SUPPORT").contains(reaction))throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REACTION","Reaction is not supported");
        return reaction;
    }
    private static PostSummary postSummary(ResultSet rs)throws SQLException {
        return new PostSummary(rs.getObject("post_public_id",UUID.class),rs.getObject("group_public_id",UUID.class),
            rs.getString("display_name"),rs.getString("body"),rs.getLong("like_count"),rs.getLong("heart_count"),
            rs.getLong("support_count"),rs.getString("my_reaction"),rs.getObject("created_at",java.time.OffsetDateTime.class),rs.getBoolean("mine"));
    }
    private static ApiException invalidPost(String text){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_COMMUNITY_POST",text);}
    record PostTarget(UUID id,UUID groupId,UUID authorId){}
}
