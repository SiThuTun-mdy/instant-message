package dev.sithutun.im.contact;

import dev.sithutun.im.user.User;
import dev.sithutun.im.user.UserDto;
import dev.sithutun.im.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/contacts")
public class ContactController {

    public record AddContactRequest(@NotBlank String accountName) {
    }

    private final ContactRepository contacts;
    private final UserRepository users;

    public ContactController(ContactRepository contacts, UserRepository users) {
        this.contacts = contacts;
        this.users = users;
    }

    @GetMapping
    public List<UserDto> list(@AuthenticationPrincipal Jwt jwt) {
        return contacts.findContactsOf(UUID.fromString(jwt.getSubject())).stream()
                .map(UserDto::from)
                .toList();
    }

    /** Idempotent: adding someone who is already a contact succeeds and changes nothing. */
    @PostMapping
    public UserDto add(@Valid @RequestBody AddContactRequest request, @AuthenticationPrincipal Jwt jwt) {
        UUID me = UUID.fromString(jwt.getSubject());
        User target = users.findByAccountName(request.accountName().trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No such user"));
        if (target.getId().equals(me)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot add yourself");
        }
        if (!contacts.existsByOwnerIdAndContactId(me, target.getId())) {
            try {
                contacts.save(new Contact(me, target.getId()));
            } catch (DataIntegrityViolationException e) {
                // A concurrent request added the same contact first; the outcome is the same
            }
        }
        return UserDto.from(target);
    }
}
