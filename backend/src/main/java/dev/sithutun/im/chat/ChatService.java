package dev.sithutun.im.chat;

import dev.sithutun.im.user.User;
import dev.sithutun.im.user.UserDto;
import dev.sithutun.im.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    public static final int MAX_BODY_LENGTH = 2000;

    /** duplicate is true when the id was already stored, i.e. the client retried a send. */
    public record Sent(MessageDto message, UUID recipientId, boolean duplicate) {
    }

    private final MessageRepository messages;
    private final UserRepository users;

    public ChatService(MessageRepository messages, UserRepository users) {
        this.messages = messages;
        this.users = users;
    }

    @Transactional
    public Sent send(UUID senderId, String senderName, UUID messageId, String to, String body) {
        if (messageId == null || to == null || body == null || body.isBlank()) {
            throw new ChatException("bad_request");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new ChatException("body_too_long");
        }
        User recipient = users.findByAccountName(to.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ChatException("recipient_not_found"));
        if (recipient.getId().equals(senderId)) {
            throw new ChatException("invalid_recipient");
        }

        Optional<Message> existing = messages.findById(messageId);
        if (existing.isPresent()) {
            if (!existing.get().getSenderId().equals(senderId)) {
                throw new ChatException("duplicate_id");
            }
            return new Sent(toDto(existing.get(), senderName, recipient.getAccountName()),
                    existing.get().getRecipientId(), true);
        }

        // Microseconds match PostgreSQL's precision, so the ack and later history agree
        Instant sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Message saved = messages.save(new Message(messageId, senderId, recipient.getId(), body, sentAt));
        return new Sent(toDto(saved, senderName, recipient.getAccountName()), recipient.getId(), false);
    }

    @Transactional(readOnly = true)
    public Optional<List<MessageDto>> history(UUID me, String myName, String otherName, Instant before, int limit) {
        return users.findByAccountName(otherName.trim().toLowerCase(Locale.ROOT))
                .map(other -> messages.findConversation(me, other.getId(), before, Limit.of(limit)).stream()
                        .map(m -> m.getSenderId().equals(me)
                                ? toDto(m, myName, other.getAccountName())
                                : toDto(m, other.getAccountName(), myName))
                        .toList());
    }

    @Transactional(readOnly = true)
    public List<UserDto> peers(UUID me) {
        return users.findChatPeers(me).stream().map(UserDto::from).toList();
    }

    private static MessageDto toDto(Message m, String from, String to) {
        return new MessageDto(m.getId(), from, to, m.getBody(), m.getSentAt());
    }
}
