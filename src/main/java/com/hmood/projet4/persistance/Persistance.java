package com.hmood.projet4.persistance;

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Sauvegarde par sérialisation Java (pas de base de données) : l'objet {@link Donnees} entier est écrit
 * dans un fichier avec ObjectOutputStream, et relu avec ObjectInputStream au lancement suivant.
 */
public class Persistance {

    private final Path fichier;

    public Persistance(Path fichier) {
        this.fichier = fichier;
    }

    /** Relit les données ; un fichier absent donne des données vides (premier lancement). */
    public Donnees charger() throws IOException {
        if (!Files.exists(fichier)) {
            return new Donnees();
        }
        try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(fichier))) {
            return (Donnees) in.readObject();
        } catch (ClassNotFoundException | ClassCastException | InvalidClassException e) {
            throw new IOException("Fichier de sauvegarde illisible (" + fichier.getFileName() + ") : " + e.getMessage(), e);
        }
    }

    /**
     * Écrit d'abord dans un fichier temporaire puis le renomme : si l'écriture échoue en route,
     * l'ancienne sauvegarde reste intacte.
     */
    public void sauvegarder(Donnees donnees) throws IOException {
        Path dossier = fichier.toAbsolutePath().getParent();
        if (dossier != null) {
            Files.createDirectories(dossier);
        }
        Path temporaire = fichier.resolveSibling(fichier.getFileName() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(temporaire))) {
            out.writeObject(donnees);
        }
        Files.move(temporaire, fichier, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public Path getFichier() {
        return fichier;
    }
}
