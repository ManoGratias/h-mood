package com.hmood.projet4.compte;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Hachage des mots de passe : SHA-256 appliqué au sel + mot de passe.
 * Le sel est aléatoire et différent pour chaque compte : deux comptes avec le même mot de passe
 * n'ont pas la même empreinte, et une table d'empreintes précalculées ne sert à rien.
 */
public final class MotDePasse {

    private static final SecureRandom ALEA = new SecureRandom();

    private MotDePasse() {
    }

    /** Nouveau sel aléatoire de 16 octets, en hexadécimal. */
    public static String nouveauSel() {
        byte[] sel = new byte[16];
        ALEA.nextBytes(sel);
        return HexFormat.of().formatHex(sel);
    }

    public static String empreinte(String sel, String motDePasse) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha.digest((sel + motDePasse).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e); // n'arrive pas : SHA-256 est fourni par Java
        }
    }

    /** Compare en temps constant (MessageDigest.isEqual) pour ne rien laisser deviner par la durée. */
    public static boolean verifier(String sel, String empreinteAttendue, String motDePasseSaisi) {
        byte[] attendu = empreinteAttendue.getBytes(StandardCharsets.UTF_8);
        byte[] calcule = empreinte(sel, motDePasseSaisi).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(attendu, calcule);
    }
}
