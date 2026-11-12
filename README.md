# H MOOD — Ressens ton humeur. Vis ta musique.

Application musicale de bureau (Windows) en **Java 21 + JavaFX 21 + SQLite**.
Lecteur de musique locale qui propose la bonne musique selon ton humeur.

> Version actuelle : **0.1.0 — MVP exécutable**. Tout ce qui est affiché fonctionne réellement ;
> les modules pas encore développés sont marqués « bientôt » et le disent clairement.

---

## 1. Lancer l'application

Prérequis : JDK 21 et Maven 3.9 (déjà présents sur ce poste dans `~/tools`).

```powershell
# Depuis le dossier H_MOOD
.\lancer.ps1              # compile si besoin puis lance l'application
```

Ou manuellement :

```powershell
mvn javafx:run            # lancement en mode développement
mvn package               # tests + JAR autonome target\hmood.jar
java -jar target\hmood.jar
```

Les données (base SQLite, pochettes) sont stockées dans `%APPDATA%\HMood`.
Pour tester avec une base séparée : `java -Dhmood.data.dir=C:\temp\hmood-test -jar target\hmood.jar`.

## 2. Créer l'exécutable Windows

```powershell
.\creer-executable.ps1
```

Le script utilise `jpackage` (fourni avec le JDK) et produit :

- `dist\H MOOD\H MOOD.exe` — application autonome (Java embarqué), aucun installateur requis ;
- avec `.\creer-executable.ps1 -Installateur` : un installateur `.exe` (nécessite **WiX Toolset 3** installé).

## 3. Ce que contient le MVP

| Module | État |
|---|---|
| Interface principale (barre latérale, zone centrale, assistant IA, barre de lecture) | ✅ |
| Navigation animée entre les pages | ✅ |
| **Ma musique** : import de fichiers / dossiers (récursif) / glisser-déposer, lecture des tags (titre, artiste, album, genre, année, durée, pochette), recherche, filtres, favoris, retrait | ✅ |
| **Lecteur audio** : lecture/pause, précédent/suivant, progression, volume, aléatoire, répétition (off / file / morceau), file d'attente, visualiseur basé sur le vrai spectre audio | ✅ |
| **Playlists** : création, renommage, suppression, ajout depuis Ma musique, réordonnancement | ✅ |
| **Studio playlists** : génération par humeur, genre, décennie et durée | ✅ |
| **Scanner d'humeur** : 12 humeurs, questionnaire facultatif, playlist adaptée, artistes recommandés, morceaux similaires, ambiance visuelle, durée suggérée | ✅ |
| **Accueil** : bienvenue, ton humeur du jour, recommandations du jour, récemment écoutés, playlists, statistiques, suggestion du DJ selon l'heure | ✅ |
| **Assistant IA** (panneau de droite) : interprétation **locale** de demandes simples (« j'ai besoin de motivation », « un son pour un jour de pluie », « surprends-moi ») | ✅ (sans IA, indiqué comme tel) |
| **Thèmes** Bleu Glacier / Orange Couchant, mémorisés | ✅ |
| **Paramètres** : thème, nom, bibliothèque, audio, données locales, IA, confidentialité, à propos | ✅ |
| Historique d'écoute et d'humeur enregistré en base | ✅ |
| **Découvrir — catalogue musical réel** : 15 classements (Top 50 Monde / France / USA / Royaume-Uni, Rap français, Hip-Hop, Afrobeat, R&B, Pop, Électro, Hits viraux, Nouveautés, Artistes et Albums tendance, Indépendants), recherche, top d'un artiste, albums, titres similaires, infos MusicBrainz, « Pour toi » | ✅ données réelles, source et date affichées |
| Lecture : fichiers locaux, **extraits officiels 30 s** (Deezer, Apple), **morceaux complets** (Audius) ; lien ↗ vers le service officiel pour l'écoute intégrale | ✅ |
| **Écoute intégrale des tubes** : lecteur officiel Deezer intégré (connexion à ton compte Deezer, mémorisée) — titres complets selon ton abonnement | ✅ |
| **DJ IA** : 25 univers cohérents (Afrique : Top Côte d'Ivoire, Rap ivoirien, Ambiance Abidjan, Togo, Afrobeats, Top Sénégal, R&B africain, Soirée Afro, Afro Chill, Amapiano, Rumba congolaise · Rap FR/US/UK, Drill · Top France/Monde, Zouk & Konpa, Reggaeton, R&B · moments), titres phares classés par popularité réelle, **tempo (BPM) et énergie analysés** à partir des extraits, enchaînement de DJ (montée/descente du tempo, faibles écarts de BPM, pas deux fois le même artiste), courbe de tempo, fondu enchaîné | ✅ (sans IA externe, indiqué) |
| **ADN musical** : hélice animée colorée par tes humeurs, profil, genres, artistes, top titres, ambiances, évolution sur 30 jours | ✅ (historique local) |
| **Voyage temporel** : années 80, 90, 2000, 2010, aujourd'hui × genre × ambiance — vraies playlists éditoriales + tes morceaux de l'époque | ✅ |
| **Concentration** : minuteur Pomodoro (travail/pause, compteur du jour, pause musique), ambiances lo-fi / ambient / piano / instrumental / pluie (morceaux complets) | ✅ |
| **Sommeil** : ciel étoilé animé, minuteur d'arrêt 15–90 min avec baisse progressive du volume, ambiances douces / pluie / nuit / piano | ✅ |
| Fournisseur d'IA externe | 🔜 interface `AiProvider` prête, aucun fournisseur branché |

Formats lus : **MP3, M4A/AAC, WAV, AIFF** (limites du moteur JavaFX Media).
FLAC et OGG sont ignorés à l'import avec un message.

### Comment marche la recommandation (sans IA)

Le moteur local (`RuleBasedRecommendationEngine`) note chaque morceau pour une humeur :

- humeur associée par toi au morceau (Ma musique › clic droit › Humeur) : **+10**
- genre du fichier qui évoque l'humeur (ex. « lo-fi », « piano » → Concentré) : **+5**
- Nostalgique : morceau de plus de 15 ans : **+5**
- favori : +2 ; écoutes : +0,2 par écoute (plafonné)

Un morceau sans aucun point d'humeur n'est jamais proposé : si rien ne correspond, l'application le dit
et explique comment associer des humeurs.

## 4. Catalogue musical réel (module « Real Music Catalog »)

| Fournisseur | Rôle | État | Lecture |
|---|---|---|---|
| **Deezer** (API publique, sans clé) | Top Monde, Top France, classements par genre, playlists éditoriales (Rap, Afro, Viral, Nouveautés), artistes / albums tendance, recherche, top artiste, radio d'artiste | ✅ connecté | Extraits officiels 30 s + lien Deezer |
| **Apple Music** (RSS officiels + iTunes Search, sans clé) | Top 50 USA et Royaume-Uni | ✅ connecté | Extraits officiels 30 s + lien Apple Music |
| **Audius** (API publique) | Artistes indépendants, humeurs | ✅ connecté | Morceaux complets (autorisés par les artistes) |
| **MusicBrainz** | Métadonnées (première date de sortie, référence) | ✅ connecté | — |
| Spotify Web API | Catalogue / lecture | ⚙️ à configurer (Client ID / Secret ; lecture intégrale via SDK officiel + Premium) | — |
| Apple MusicKit | Lecture intégrale Apple Music | ⚙️ à configurer (compte Apple Developer) | — |
| SoundCloud API | Catalogue / lecture | ⚙️ à configurer (clé d'application) | — |

Règles respectées :

- **Aucune donnée inventée** : chaque classement affiche sa source exacte (ex. « Deezer · playlist officielle « Top France » ») et sa date d'actualisation ; un champ inconnu reste vide.
- **Pas de contournement** : seuls les extraits officiels fournis par l'API sont lus ; rien n'est enregistré ni redistribué. Quand la lecture n'est pas autorisée, l'application l'affiche et ouvre le service officiel.
- **Cache SQLite** (`catalog_cache`) d'1 h avec source et date, actualisation automatique des principaux classements toutes les heures, **repli sur le cache** (avec avertissement) en cas de réseau coupé ou de quota atteint, espacement des appels par fournisseur (MusicBrainz : 1 requête/s).
- Les données des fournisseurs ne servent qu'à l'affichage : ni entraînement, ni profilage.
- Les classements « par genre » de Deezer dépendent du pays de connexion (indiqué dans la source).

Architecture (`com.hmood.music`) :

- Interfaces : `MusicCatalogProvider`, `MusicChartProvider`, `MusicPlaybackProvider`, `MusicMetadataProvider`.
- Adaptateurs : `DeezerProvider`, `AppleMusicProvider`, `AudiusProvider`, `MusicBrainzProvider` (remplaçables).
- Services : `MusicCatalogService`, `TrendingService`, `NewReleasesService`, `PlaybackService`, `MusicDiscoveryService`, `MusicRecommendationService`.

## 5. Architecture

```
src/main/java/com/hmood/
├── app/             HMoodApp (JavaFX), Launcher, AppContext (assemblage + état partagé)
├── controller/      contrôleurs FXML : Main, Home, Library, Playlists, MoodScanner, Settings, Placeholder, AiPanel
├── model/           Track, Playlist, Mood (12 humeurs), Theme, TrackMetadata
├── service/         LibraryService, MetadataReader, PlaylistService, MoodService, MoodQuestionnaire, SettingsService
├── repository/      TrackRepository, PlaylistRepository, HistoryRepository, PreferenceRepository (JDBC)
├── database/        Database (connexion SQLite + schéma)
├── recommendation/  RecommendationEngine (interface) + RuleBasedRecommendationEngine
├── music/           catalogue réel : providers (Deezer, Apple, Audius, MusicBrainz), services, cache
├── catalog/         client Audius
├── ai/              AiProvider (interface), NotConfiguredAiProvider, AiProviderFactory, LocalIntentParser, AiAssistantService
├── player/          PlayerService (JavaFX Media), RepeatMode, PlaybackListener
├── ui/              ThemeManager, Icons, Logo, Visualizer, Covers, Dialogs, Ui
└── util/            Durations, AudioFormats, AppPaths
src/main/resources/com/hmood/
├── fxml/            MainView, HomeView, LibraryView, PlaylistsView, MoodScannerView, SettingsView, PlaceholderView
├── css/             base.css, theme-ice.css, theme-sunset.css
└── db/schema.sql    users, tracks, artists, albums, playlists, playlist_tracks, favorites,
                     listening_history, mood_history, user_preferences (+ clés étrangères et index)
```

Principes :

- **Interface / métier / audio / données / recommandation / IA séparés** ; les contrôleurs ne touchent jamais au SQL.
- **Injection manuelle** via `AppContext` (fabrique de contrôleurs FXML) : remplaçable par Spring si un backend arrive.
- **Points d'extension** : `RecommendationEngine` (service de recommandation externe), `AiProvider` (OpenAI, Anthropic,
  Mistral, modèle local…), `PlayerService` (autre moteur audio pour FLAC/OGG).
- Import en tâche de fond (`Task`), appels IA hors du thread JavaFX : l'interface ne se bloque pas.

## 6. Tests

```powershell
mvn test
```

43 tests : catalogue (cache, repli hors ligne, décodage Deezer/Audius sans invention de données), schéma SQLite, repositories (upsert, favoris, playlists ordonnées, cascades, historique, préférences),
moteur de recommandation, interprétation locale des demandes, questionnaire, recherche, métadonnées, formats.

## 7. Feuille de route (phase 2)

1. Fournisseur IA réel derrière `AiProvider` (clé saisie dans Paramètres, stockée localement) → page **DJ IA**.
2. **ADN musical** : graphiques à partir de `listening_history` et `mood_history` (déjà alimentées).
3. **Voyage temporel** : ambiances par décennie (le filtre décennie existe déjà dans le Studio playlists).
4. **Concentration** (minuteur type Pomodoro) et **Sommeil** (minuteur d'arrêt).
5. Connecteurs Spotify / SoundCloud / MusicKit dès qu'une clé est fournie (saisie dans Paramètres).
6. Lecture FLAC/OGG via une bibliothèque audio complémentaire.

Le cahier des charges complet est dans `docs/CAHIER_DES_CHARGES.md`.
