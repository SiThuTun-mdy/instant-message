package dev.sithutun.im.chat;

import java.time.Instant;
import java.util.UUID;

/** A message as the API exposes it: account names instead of internal user ids. */
public record MessageDto(UUID id, String from, String to, String body, Instant sentAt) {
}
