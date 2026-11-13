Projet 4 : Appli de musique avec playlist IA

**Notions :** héritage, classe abstraite, polymorphisme, interface, collections (Cours 4 et 5) + appel d'une IA

Une application console de musique : des utilisateurs, un catalogue de morceaux, et une **playlist proposée par l'IA selon ton humeur**. Pas de base de données.

### ⭐ Socle

- [ ] Une classe abstraite `Utilisateur`, avec deux classes filles `Auditeur` et `Admin`. Le menu affiché dépend du type d'utilisateur (polymorphisme)
- [ ] Une classe `Morceau` (titre, artiste, genre) et un catalogue dans une collection (par exemple une `Map` par genre) qui s'appuie sur une bibliothèque de musiques déjà existente (exemple : jamendo.com).
- [ ] Inscription et connexion
- [ ] Un auditeur peut mettre des morceaux et des artistes en favoris, et s'abonner à des artistes
- [ ] L'`Admin` peut ajouter et supprimer des morceaux
- [ ] Une **interface** `Recommandeur` avec deux implémentations : `MockRecommandeur` (sans réseau) et `IaRecommandeur` (qui réutilise l'`AiClient` du Projet 3). L'utilisateur saisit son humeur, le programme propose une playlist
- [ ] **La sortie de l'IA est contrôlée** : les morceaux proposés qui n'existent pas dans le catalogue sont rejetés
- [ ] **Aucune clé API** dans le code
- [ ] Interface utilisateur GUI ou console

### 🚀 Extensions

- [ ] Persistance par sérialisation (Cours 5)
- [ ] Mot de passe haché (SHA-256 avec sel)
- [ ] Tests JUnit sur `Recommandeur`

### 🤖 Avec l'IA

- [ ] Fais proposer par l'IA le plan des classes **avant** de coder : qui hérite de qui, et pourquoi (temps 1)
- [ ] Demande-lui un prompt pour la playlist (humeur + liste du catalogue), puis **teste-le avec 3 humeurs différentes**. Note dans `NOTES.md` ce qu'elle a inventé ou ignoré
- [ ] Demande-lui 3 questions pièges sur ton code et réponds-y sans regarder

### 🔍 Points de contrôle : tu dois savoir répondre

- Pourquoi `Utilisateur` est-elle abstraite ? Que se passerait-il si tu pouvais écrire `new Utilisateur()` ?
- Pourquoi `Recommandeur` est-elle une interface ?
- Que fait ton programme si l'IA propose un morceau qui n'existe pas ? Où le gères-tu ?
- Pourquoi une `Map` plutôt qu'une simple liste pour le catalogue ?

### ✋ Défi sans IA (10 min)

Ajoute **à la main** une commande qui permet à l'`Admin` de lister les utilisateurs inscrits.



## 📑Barème proposé (sur 20)

| Critère                              | Points     | Ce que vous regardez                                                                                                                              |
| ------------------------------------ | ---------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Socle fonctionnel                    | 10         |                                                                                                                                                   |
| Qualité et robustesse du code        | 3          | Séparation logique et affichage, saisies invalides, aucune clé dans le dépôt                                                                      |
| Compréhension, Démo et communication | 5          | Soutenance + moyenne des observations des trois points de contrôle + Clarté, tenue du temps                                                       |
| Usage de l’IA                        | 2          | `NOTES.md` honnête : erreurs de l’IA repérées, points non compris, prompts utiles ; commits réguliers qui montrent une progression par petits pas |
| **Bonus**                            | jusqu’à +2 | Extensions. Note finale plafonnée à 20                                                                                                            |

**Pénalités :** clé API trouvée dans le dépôt, −2, et demandez la révocation de la clé (la supprimer du code ne suffit pas, elle reste dans l’historique Git). Code identique à celui d’un camarade : entretien avant notation.

**Repères indicatifs :** un débutant solide (socles propres, bonne compréhension) vise 13-15 ; un étudiant qui a tout fait avec de bons réflexes, 15-17 ; 18-20 se mérite par les extensions ou le Projet 4 **et** une compréhension irréprochable. Un projet ambitieux mal compris ne dépasse donc pas un projet simple bien compris.
