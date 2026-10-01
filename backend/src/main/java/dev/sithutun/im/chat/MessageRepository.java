package dev.sithutun.im.chat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    @Query("""
            select m from Message m
            where ((m.senderId = :a and m.recipientId = :b) or (m.senderId = :b and m.recipientId = :a))
              and m.sentAt < :before
            order by m.sentAt desc
            """)
    List<Message> findConversation(UUID a, UUID b, Instant before, Limit limit);
}
