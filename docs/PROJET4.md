# Projet 4 — Appli de musique avec playlist IA : où est quoi

Énoncé : [`ENONCE_PROJET4.md`](ENONCE_PROJET4.md). H MOOD est l'application graphique (JavaFX) ; la partie
demandée par l'énoncé est regroupée dans le paquet **`com.hmood.projet4`** et dans trois écrans
(connexion, *Catalogue IA*, *Administration*).

## Socle : correspondance énoncé → code

| Énoncé | Où | Comment |
|---|---|---|
| Classe abstraite `Utilisateur`, filles `Auditeur` et `Admin` | `projet4/modele/Utilisateur.java`, `Auditeur.java`, `Admin.java` | `Utilisateur` déclare `espaces()` et `role()` en `abstract` ; chaque fille les définit |
| Menu qui dépend du type d'utilisateur (polymorphisme) | `MainController.buildNav()` | appelle `utilisateur.espaces()` sans savoir si c'est un Auditeur ou un Admin ; Java exécute la version de la vraie classe. Admin : *Administration* + écoute + paramètres. Auditeur : écoute + *Catalogue IA* + paramètres |
| Classe `Morceau` (titre, artiste, genre) | `projet4/modele/Morceau.java` | + un identifiant (celui de Jamendo) ; `equals`/`hashCode` sur l'identifiant |
| Catalogue dans une collection (Map par genre) s'appuyant sur Jamendo | `projet4/catalogue/Catalogue.java`, `ChargeurJamendo.java` | `Map<String, List<Morceau>>` (TreeMap) ; rempli par l'Admin avec les morceaux populaires de 10 genres Jamendo |
| Inscription et connexion | `projet4/compte/ComptesService.java`, écran `ui/ConnexionView.java` | identifiant 3-20 caractères, mot de passe ≥ 6, confirmation ; le premier compte créé est l'Admin |
| Favoris (morceaux, artistes) et abonnements | `Auditeur.java` (3 `Set<String>`), page *Catalogue IA* | boutons ♡ (morceau), ☆ (artiste), « + S'abonner » ; les morceaux des artistes suivis sont listés |
| L'Admin ajoute et supprime des morceaux | page *Administration* (`AdminController`), `Projet4Service.ajouterMorceau / supprimerMorceau` | suppression avec confirmation |
| Interface `Recommandeur`, `MockRecommandeur` (sans réseau), `IaRecommandeur` (AiClient du Projet 3) | `projet4/ia/` | `AiClient`, `HttpAiClient`, `MockAiClient`, `AiException` sont ceux du Projet 3 (seul le `package` change) |
| Sortie de l'IA contrôlée | `IaRecommandeur.controler(...)` | chaque ligne de la réponse est cherchée dans le catalogue par identifiant ; absente → rejetée et affichée en orange sous la playlist |
| Aucune clé API dans le code | `Projet4Service.recommandeurIa()` | clé lue dans la variable d'environnement `AI_API_KEY` ; client_id Jamendo dans les Paramètres de l'appli ou `JAMENDO_CLIENT_ID` |
| Interface utilisateur | JavaFX | écran de connexion, pages *Catalogue IA* et *Administration* |

## Extensions

| Extension | Où |
|---|---|
| Persistance par sérialisation | `projet4/persistance/Persistance.java` : l'objet `Donnees` (comptes + catalogue) est écrit avec `ObjectOutputStream` dans `%APPDATA%\HMood\projet4.ser` |
| Mot de passe haché (SHA-256 avec sel) | `projet4/compte/MotDePasse.java` : sel aléatoire de 16 octets par compte, `SHA-256(sel + mot de passe)`, comparaison avec `MessageDigest.isEqual` |
| Tests JUnit sur `Recommandeur` | `src/test/java/com/hmood/projet4/RecommandeurTest.java` (7 tests, dont rejet des morceaux inventés) et `ComptesEtSauvegardeTest.java` (7 tests) |

## À savoir dire à la soutenance

- **Base de données.** L'énoncé dit « pas de base de données ». La partie Projet 4 (comptes, catalogue, favoris,
  abonnements) n'en utilise pas : elle est sauvegardée par sérialisation. Le reste de H MOOD (bibliothèque de
  fichiers importés, historique d'écoute, paroles…) existait avant et utilise SQLite. À dire franchement.
- **Le premier compte est l'Admin.** Il n'y a pas de compte Admin écrit dans le code (ce serait un mot de passe
  en clair dans le dépôt).
- **Pourquoi des identifiants dans le prompt ?** Pour contrôler la sortie : un identifiant se vérifie exactement
  dans le catalogue, alors qu'un titre peut être mal recopié ou inventé.

## Lancer et montrer

1. Ouvrir le dossier `H_MOOD` dans Cursor / VS Code (extension *Extension Pack for Java*), puis
   *Exécuter et déboguer* → **H MOOD (application)**. Ou double-cliquer `dist\H MOOD\H MOOD.exe`.
2. Créer le premier compte (Admin) → page *Administration* → **Charger / compléter depuis Jamendo**.
3. Se déconnecter (icône en bas du menu), créer un second compte (Auditeur) → *Catalogue IA* :
   favoris, abonnements, playlist selon l'humeur.
4. Pour l'IA réelle : définir `AI_API_KEY` (clé Groq, comme au Projet 3) avant de lancer l'appli.
   Sans clé, le mode « Sans réseau » (`MockRecommandeur`) est utilisé.
5. Tests : `mvn test` (ou l'onglet *Testing* de VS Code).

Pour une démo sans toucher à tes vraies données : configuration **H MOOD (données de test séparées)**
(dossier `donnees-test/`, ignoré par Git).
