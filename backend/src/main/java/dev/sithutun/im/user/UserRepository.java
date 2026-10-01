package dev.sithutun.im.user;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByAccountName(String accountName);

    Optional<User> findByEmail(String email);

    boolean existsByAccountName(String accountName);

    boolean existsByEmail(String email);

    List<User> findTop20ByAccountNameStartingWithAndIdNotOrderByAccountName(String prefix, UUID excludedId);

    /** Everyone the given user has exchanged at least one message with. */
    @Query("""
            select u from User u
            where u.id in (select m.recipientId from Message m where m.senderId = :me)
               or u.id in (select m.senderId from Message m where m.recipientId = :me)
            order by u.accountName
            """)
    List<User> findChatPeers(UUID me);
}
