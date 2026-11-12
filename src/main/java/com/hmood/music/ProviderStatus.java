package com.hmood.music;

/** État réel d'un connecteur, affiché tel quel dans l'interface. */
public record ProviderStatus(String name, String role, boolean active, String detail) {
}
