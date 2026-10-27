package com.pritimitra.backend.community;
import com.pritimitra.backend.community.CommunityDtos.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/community")
class CommunityController {
    private final CommunityService community;
    CommunityController(CommunityService community) { this.community = community; }
    @GetMapping("/groups") GroupList groups(@AuthenticationPrincipal Jwt jwt) { return community.groups(jwt.getSubject()); }
    @PutMapping("/groups/{groupPublicId}/membership")
    MembershipReceipt join(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupPublicId) {
        return community.join(jwt.getSubject(), groupPublicId);
    }
    @DeleteMapping("/groups/{groupPublicId}/membership") @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupPublicId) { community.leave(jwt.getSubject(), groupPublicId); }
    @GetMapping("/groups/{groupPublicId}/posts")
    PostPage posts(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupPublicId,
            @RequestParam(name = "cursor", required = false) String cursor,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return community.posts(jwt.getSubject(), groupPublicId, cursor, size);
    }
    @PostMapping("/groups/{groupPublicId}/posts") @ResponseStatus(HttpStatus.CREATED)
    PostReceipt createPost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID groupPublicId, @RequestBody CreatePostRequest request) {
        return community.createPost(jwt.getSubject(), groupPublicId, request);
    }
    @DeleteMapping("/posts/{postPublicId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removePost(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postPublicId) { community.removePost(jwt.getSubject(), postPublicId); }
    @PutMapping("/posts/{postPublicId}/reaction")
    ReactionReceipt react(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postPublicId, @RequestBody ReactionRequest request) {
        return community.react(jwt.getSubject(), postPublicId, request);
    }
    @DeleteMapping("/posts/{postPublicId}/reaction") @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeReaction(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID postPublicId) { community.removeReaction(jwt.getSubject(), postPublicId); }
}
