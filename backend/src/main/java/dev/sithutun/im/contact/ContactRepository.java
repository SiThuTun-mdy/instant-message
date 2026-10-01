package dev.sithutun.im.contact;

import dev.sithutun.im.user.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContactRepository extends JpaRepository<Contact, UUID> {

    boolean existsByOwnerIdAndContactId(UUID ownerId, UUID contactId);

    @Query("""
            select u from User u
            where u.id in (select c.contactId from Contact c where c.ownerId = :ownerId)
            order by u.accountName
            """)
    List<User> findContactsOf(UUID ownerId);
}
