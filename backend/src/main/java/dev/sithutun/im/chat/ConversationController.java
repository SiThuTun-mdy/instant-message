package dev.sithutun.im.chat;

import dev.sithutun.im.auth.JwtService;
import dev.sithutun.im.user.UserDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    private static final int MAX_LIMIT = 100;

    private final ChatService chatService;

    public ConversationController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** People the caller has already exchanged messages with. */
    @GetMapping
    public List<UserDto> peers(@AuthenticationPrincipal Jwt jwt) {
        return chatService.peers(UUID.fromString(jwt.getSubject()));
    }

    /** History with one person, newest first; pass the oldest sentAt as before to page back. */
    @GetMapping("/{accountName}/messages")
    public List<MessageDto> messages(@PathVariable("accountName") String accountName,
            @RequestParam(name = "before", required = false) Instant before,
            @RequestParam(name = "limit", defaultValue = "50") int limit,
            @AuthenticationPrincipal Jwt jwt) {
        return chatService.history(
                        UUID.fromString(jwt.getSubject()),
                        jwt.getClaimAsString(JwtService.ACCOUNT_NAME_CLAIM),
                        accountName,
                        before != null ? before : Instant.now(),
                        Math.clamp(limit, 1, MAX_LIMIT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
    }
}
