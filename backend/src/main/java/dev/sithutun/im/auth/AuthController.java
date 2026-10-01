package dev.sithutun.im.auth;

import dev.sithutun.im.user.User;
import dev.sithutun.im.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record SignupRequest(
            @NotBlank @Pattern(regexp = "(?i)^\\s*[a-z0-9._%+-]+@gmail\\.com\\s*$",
                    message = "must be a @gmail.com address") String email,
            @NotBlank @Pattern(regexp = "(?i)^\\s*[a-z0-9_]{3,20}\\s*$",
                    message = "must be 3-20 characters: letters, digits or underscore") String accountName,
            @NotBlank @Size(max = 50) String displayName,
            // bcrypt only uses the first 72 bytes
            @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken, long expiresIn, String accountName, String displayName) {
    }

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest request) {
        String email = normalize(request.email());
        String accountName = normalize(request.accountName());
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already registered");
        }
        if (users.existsByAccountName(accountName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account name is taken");
        }
        try {
            users.save(new User(email, accountName, request.displayName().trim(),
                    passwordEncoder.encode(request.password())));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent sign-up for the same email or account name
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email or account name is taken");
        }
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String identifier = normalize(request.identifier());
        User user = (identifier.contains("@")
                ? users.findByEmail(identifier)
                : users.findByAccountName(identifier))
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return new LoginResponse(jwtService.issue(user), jwtService.ttl().toSeconds(),
                user.getAccountName(), user.getDisplayName());
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
