package dev.sithutun.im.contact;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contacts")
public class Contact {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "contact_id", nullable = false)
    private UUID contactId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Contact() {
    }

    public Contact(UUID ownerId, UUID contactId) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.contactId = contactId;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getContactId() {
        return contactId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
