package com.hmood.repository;

/** Erreur d'accès aux données, avec un message compréhensible pour l'utilisateur. */
public class DataAccessException extends RuntimeException {
    public DataAccessException(String message, Throwable cause) {
        super(message + (cause != null && cause.getMessage() != null ? " (" + cause.getMessage() + ")" : ""), cause);
    }
}
