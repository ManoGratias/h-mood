# NOTES — Projet 4 (H MOOD)

> À compléter **par moi** : l'énoncé demande un `NOTES.md` honnête (ce que j'ai compris, ce que l'IA a mal fait,
> ce que je n'ai pas compris, prompts utiles). Les parties marquées ✍️ sont à écrire avec mes propres mots.

## Ce que j'ai compris ✍️

- 
- 

## Ce que je n'ai pas encore compris ✍️

- 

## Ce que l'IA a mal fait ou inventé

Erreurs réellement constatées pendant le développement de H MOOD avec l'IA (Claude) :

- **Script de remplacement destructeur** : un script PowerShell écrit par l'IA pour remplacer des caractères a
  aplati un tableau de paires et corrompu 7 fichiers Java/FXML (des « E » devenus « n »…). Pas d'historique Git à
  ce moment-là : il a fallu reconstruire les fichiers. Depuis : Git dès le début, et remplacements vérifiés.
- **Manque de mémoire** : le cache des pochettes gardait toutes les images ; l'application plantait
  (OutOfMemoryError) après ~30 minutes. Corrigé par un cache borné.
- **Application figée** : la libération d'un lecteur audio se faisait sur le thread de l'interface et pouvait
  bloquer toute la fenêtre. Corrigé en la faisant en arrière-plan.
- **Paroles d'une autre chanson** : une recherche « tolérante » associait « Laisse moi te dire » de Jean-Marie
  Ragald à la chanson homonyme de Tayc. Corrigé en exigeant le même interprète.
- ✍️ (ce que j'ai repéré moi-même)

## Prompts utiles

- Prompt de la playlist : voir `IaRecommandeur.prompt(...)` — humeur + catalogue complet (`id | titre | artiste |
  genre`) + « réponds uniquement par des identifiants, un par ligne ».

## Test du prompt avec 3 humeurs ✍️

À faire avec la vraie IA (`AI_API_KEY` définie) dans la page *Catalogue IA*, et noter ce qu'elle a inventé ou ignoré
(les propositions rejetées s'affichent en orange) :

| Humeur | Morceaux retenus | Rejetés (inventés) | Remarque |
|---|---|---|---|
| | | | |
| | | | |
| | | | |

## Plan des classes

Proposé par l'IA : voir [`docs/PLAN_CLASSES.md`](docs/PLAN_CLASSES.md).

## 3 questions pièges sur mon code

Questions posées par l'IA ; **mes réponses ✍️, écrites sans regarder le code** :

1. Dans `IaRecommandeur.controler`, pourquoi utiliser un `LinkedHashSet` pour les morceaux retenus plutôt
   qu'une `ArrayList` ou un `HashSet` ? Que se passerait-il si l'IA renvoyait deux fois le même identifiant ?
   - Ma réponse : 
2. Si on supprimait le sel dans `MotDePasse` (empreinte = SHA-256 du mot de passe seul), qu'est-ce qui
   deviendrait possible pour quelqu'un qui récupère le fichier `projet4.ser` ?
   - Ma réponse : 
3. On ajoute un champ dans la classe `Auditeur` et on relance l'application : que devient l'ancien fichier
   `projet4.ser`, et à quoi sert `serialVersionUID` ?
   - Ma réponse : 

## Défi sans IA ✍️

Commande Admin « lister les utilisateurs inscrits », codée à la main en 10 minutes.
