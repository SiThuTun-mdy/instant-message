package dev.sithutun.im.user;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository users;

    public UserController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/search")
    public List<UserDto> search(@RequestParam("q") String q, @AuthenticationPrincipal Jwt jwt) {
        String prefix = q.trim().toLowerCase(Locale.ROOT);
        if (prefix.isEmpty()) {
            return List.of();
        }
        UUID me = UUID.fromString(jwt.getSubject());
        return users.findTop20ByAccountNameStartingWithAndIdNotOrderByAccountName(prefix, me).stream()
                .map(UserDto::from)
                .toList();
    }
}
