# Plan des classes du Projet 4 — qui hérite de qui, et pourquoi

Plan proposé par l'IA (Claude) pour la partie Projet 4, puis suivi pour le code du paquet `com.hmood.projet4`.

```
                 «abstract» Utilisateur  (Serializable)
                 - identifiant, sel, empreinteMotDePasse
                 + espaces() : Set<Espace>   «abstract»
                 + role() : String           «abstract»
                    ▲                    ▲
                    │ extends            │ extends
             Auditeur                   Admin
   - morceauxFavoris : Set<String>      (gère le catalogue)
   - artistesFavoris : Set<String>
   - abonnements     : Set<String>

  Morceau (id, titre, artiste, genre)      Catalogue
                                           - parGenre : Map<String, List<Morceau>>

          «interface» Recommandeur
          + recommander(humeur, catalogue, taille) : Playlist
             ▲                        ▲
     MockRecommandeur          IaRecommandeur ──utilise──▶ «interface» AiClient (Projet 3)
     (mots-clés → genres)      (prompt + contrôle)            ▲               ▲
                                                        HttpAiClient    MockAiClient
```

Autour : `ComptesService` (inscription, connexion), `MotDePasse` (SHA-256 + sel), `Persistance` + `Donnees`
(sérialisation), `ChargeurJamendo` (remplit le catalogue), `Projet4Service` (point d'entrée pour l'interface).

## Pourquoi ces choix

- **`Utilisateur` est abstraite** : un compte est toujours un Auditeur ou un Admin. La classe mère regroupe ce
  qu'ils ont en commun (identifiant, mot de passe haché) et impose, par des méthodes abstraites, que chaque type
  dise quel menu il voit (`espaces()`) et quel est son rôle (`role()`).
- **Héritage plutôt qu'un champ `type`** : avec un champ texte « admin »/« auditeur », il faudrait des `if`
  partout ; avec l'héritage, `utilisateur.espaces()` appelle automatiquement la bonne version (polymorphisme),
  et seul l'Auditeur possède des favoris.
- **`Recommandeur` est une interface** (et pas une classe mère) : `MockRecommandeur` et `IaRecommandeur` n'ont
  aucun code ni aucun champ en commun, seulement le même service à rendre. Le reste du programme ne dépend que
  du contrat, on peut changer d'implémentation sans le modifier — comme `AiClient` au Projet 3.
- **`IaRecommandeur` contient un `AiClient`** (composition) au lieu d'en hériter : un recommandeur *utilise* une
  IA, il n'*est* pas une IA. On peut lui donner un faux client dans les tests.
- **`Map<genre, List<Morceau>>` pour le catalogue** : les morceaux d'un genre s'obtiennent directement
  (`get(genre)`), sans parcourir toute la liste ; la liste des genres est la liste des clés.
- **`Set` pour les favoris** : pas de doublon possible, et `contains` répond tout de suite.
