# Plan d'exécution — Maisons (partie serveur)

Spécification d'origine : `assets/maisons.md`. Scope de ce plan : modèle BDD, accès, API, module scanner,
annonces Discord. Le site est traité dans son propre dépôt.

Chaque étape est autonome : elle compile, et pour la plupart elle est vérifiable en isolation. À exécuter dans
l'ordre — les dépendances sont indiquées.

## Décisions actées

| Sujet | Décision |
|---|---|
| Stockage des points | Registre : une ligne par (partie, joueur), avec détail par type et maison au moment du scoring |
| Attribution d'une maison | ~~Tirage au sort restreint aux maisons ayant le moins de membres~~ → le joueur choisit sa maison (amendement, étapes 7 et 9) |
| Choix de vacances | Enregistrés comme intention, appliqués à l'ouverture de la saison |
| Auth des mutations | Aucune — même pattern que `POST /gold/api/link` |
| Parties éligibles | Toute partie finie sur KGS / OGS, sans filtre de taille, handicap ou komi |
| Anti-farming | Aucun garde-fou dans cette livraison |
| Points après changement / départ | Restent acquis à la maison où ils ont été gagnés |
| Sortie en cours de saison | Impossible, sauf en quittant le serveur Discord (le compte disparaît) |
| Annonces Discord | Sur `bot.notification.channel.id` : classement quotidien le matin, arrivée dans une maison, récap de fin de saison |
| Points hors saison | Aucun : seules les parties datées dans la fenêtre de saison sont scorées |
| Éligibilité à rejoindre | Tout joueur ayant au moins un compte lié (présent dans `gold_ratings`) |
| Blason / RP | Textes en BDD (slug, nom, slogan, couleur, description), blasons côté site via le slug |
| Historique | Le registre est conservé, chaque ligne porte sa saison |

## Un risque à garder en tête

**Pas de plafond anti-farming.** Deux joueurs qui enchaînent des blitz sur OGS peuvent générer plusieurs
centaines de points en une soirée. Le registre conserve `gold_id` et `scored_at`, donc un plafond appliqué
a posteriori reste calculable sans migration si le besoin apparaît en cours de saison.

Le scanner qui tourne en local, lui, n'est pas un risque : `config.properties.dev` pointe `db.name=fg_dev`, donc
un `./gradlew :app:run` écrit ses points dans le schéma de dev et le ladder de prod n'est jamais touché.

## Barème retenu

Par partie et par joueur, cumulatif :

| Type | Colonne | Points | Condition |
|---|---|---|---|
| Partie jouée | `played` | 1 | toujours |
| Adversaire gold | `gold_opponent` | 2 | l'adversaire a un `discord_id` connu |
| Adversaire d'une maison adverse | `rival_house` | 2 | l'adversaire est membre d'une autre maison |
| Partie longue | `long_game` | 2 | `long_game = 1` |
| Victoire | `victory` | 2 | `result` désigne ce joueur |
| Partie à égalité | `even_game` | 1 | `handicap = 0` |
| Partie classée | `ranked` | 1 | `ranked = 1` |

Maximum : 11 points sur une victoire longue, classée et à égalité contre un membre d'une maison adverse.

> **Mise à jour, après la livraison** — ce total est désormais divisé par la taille du goban : inchangé en
> 19×19, divisé par 2 en 13×13, par 4 en 9×9, arrondi au supérieur dans les deux cas ; tout autre format n'est
> pas compté du tout. La vue `house_games` porte donc `size`, et `house_points` une colonne `total` — le détail
> par type reste brut et ne s'additionne plus au total. L'autorité sur ce point est
> `doc/migration taille de goban.sql`, pas la suite de ce document.

Trois précisions qui ne se devinent pas à la lecture du barème :

**« Partie à égalité » veut dire sans handicap**, pas score nul. Un vrai score nul est quasi impossible avec un
komi à 7,5. C'est `handicap = 0`, et ça se cumule avec `victory` — gagner une partie à égalité rapporte les
deux. L'Exam Hunter avait exactement ce point sous le nom `refinement`, calculé par `game.hasNoHandicap()`.

**« Adversaire gold » veut dire adversaire connu du serveur**, c'est-à-dire ayant lié un compte. Aucune
condition de rating : le rating peut arriver plus tard, et exiger `rating > 0` rendrait le barème instable
dans le temps — la même partie vaudrait deux points de plus une heure après.

**Un adversaire de la même maison ne donne que `gold_opponent`.** Les bonus se cumulent, mais `rival_house`
exige une maison différente.

---

## Étape 0 — Schéma BDD

Aucun code. Produit le SQL à appliquer à la main sur le serveur, comme les migrations précédentes.

**Fichier** : `doc/migration maisons.sql`

### Tables

```sql
DROP TABLE IF EXISTS `houses`;
CREATE TABLE `houses` (
  `id` INT NOT NULL,
  `slug` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `tagline` VARCHAR(255) NOT NULL,
  `color` VARCHAR(7) NOT NULL,
  `description` TEXT NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `houses_slug` (`slug`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
```

Le `slug` est la clé machine, stable et exposée par l'API. Le site retrouve le blason à partir de lui. Le
`name` peut changer sans rien casser.

```sql
DROP TABLE IF EXISTS `house_members`;
CREATE TABLE `house_members` (
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT NOT NULL,
  `joined` DATETIME NOT NULL,
  `pending_action` VARCHAR(16) NULL,
  PRIMARY KEY (`discord_id`),
  KEY `house_members_house` (`house_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
```

Une maison au plus par joueur, d'où la PK sur `discord_id`. Quitter une maison, c'est supprimer la ligne.
`pending_action` vaut `NULL`, `'STAY'`, `'CHANGE'` ou `'LEAVE'` ; elle est remise à `NULL` à l'ouverture de
la saison suivante. `joined` sert à ne pas scorer rétroactivement les parties jouées avant l'arrivée.

```sql
DROP TABLE IF EXISTS `house_points`;
CREATE TABLE `house_points` (
  `gold_id` VARCHAR(255) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `house_id` INT NOT NULL,
  `season` VARCHAR(9) NOT NULL,
  `played` INT NOT NULL,
  `gold_opponent` INT NOT NULL,
  `rival_house` INT NOT NULL,
  `long_game` INT NOT NULL,
  `victory` INT NOT NULL,
  `even_game` INT NOT NULL,
  `ranked` INT NOT NULL,
  `scored_at` DATETIME NOT NULL,
  PRIMARY KEY (`gold_id`, `discord_id`),
  KEY `house_points_season_house` (`season`, `house_id`),
  KEY `house_points_season_player` (`season`, `discord_id`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
```

La PK `(gold_id, discord_id)` fait toute l'idempotence : une partie ne peut pas être comptée deux fois pour
le même joueur, quel que soit le nombre de passages du scanner. `house_id` est figé à l'écriture, donc le
total d'une maison ne bouge jamais quand un joueur en change. Pas de clé étrangère, volontairement : les
parties sont supprimées après 32 jours par `CleanService` et les lignes de points doivent survivre.

```sql
DROP TABLE IF EXISTS `house_seasons`;
CREATE TABLE `house_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  `last_ranking` DATETIME NULL,
  PRIMARY KEY (`season`)
)
ENGINE = InnoDB
DEFAULT CHARACTER SET = utf8mb4;
```

Garde-fou d'idempotence pour tout ce qui doit arriver une fois : appliquer les intentions de vacances,
annoncer la clôture de la saison, poster le classement du jour. Même rôle que le `hasPromotionScore` de
l'Exam Hunter, en table plutôt qu'en déduction.

### Vue

```sql
DROP VIEW IF EXISTS `house_games`;
CREATE VIEW `house_games` AS
  SELECT g.gold_id, g.date, g.result, g.ranked, g.long_game, g.handicap,
         b.discord_id AS black_discord_id, w.discord_id AS white_discord_id
  FROM ogs_games g
    LEFT JOIN ogs_user_info b ON g.black_id = b.ogs_id
    LEFT JOIN ogs_user_info w ON g.white_id = w.ogs_id
  WHERE g.result <> 'unfinished'
UNION
  SELECT g.gold_id, g.date, g.result, g.ranked, g.long_game, g.handicap,
         b.discord_id AS black_discord_id, w.discord_id AS white_discord_id
  FROM kgs_games g
    LEFT JOIN kgs_user_info b ON g.black_id = b.kgs_id
    LEFT JOIN kgs_user_info w ON g.white_id = w.kgs_id
  WHERE g.result <> 'unfinished';
```

`LEFT JOIN`, pas `JOIN` : un adversaire inconnu du serveur doit apparaître avec un `discord_id` nul, c'est
ce qui distingue le bonus `gold_opponent`.

`handicap` est nécessaire au bonus « partie à égalité ». Pas de `size` ni `komi` en revanche : le barème
retenu ne filtre sur aucun des deux. Si un filtre arrive un jour, ajouter la colonne suffit —
`connection.query()` dérive les noms automatiquement, aucune inscription nulle part.

Les deux plateformes qui stockent des parties sont incluses, comme `fgc_validity_games`. La différence avec
cette vue tient uniquement aux filtres : la validité FGC ne retient que le 19×19 sans handicap avec un komi
standard sur 30 jours, la comptabilité des maisons prend tout.

### Seed

Quatre `INSERT` reprenant mot pour mot `assets/maisons.md` :

| id | slug | name | tagline | color |
|---|---|---|---|---|
| 1 | `FILS_DU_FROID` | Fils du Froid | Le meilleur coup est celui qui brise. | `#740001` |
| 2 | `NEXUS_ALPHA` | Nexus Alpha | Chaque coup est une équation. | `#0E1A40` |
| 3 | `SABRE_SILENCIEUX` | Sabre Silencieux | Un coup, un destin ! | `#1A472A` |
| 4 | `LUNAIRES_AETHER` | Lunaires d'Æther | Pourquoi jouer comme hier ? | `#B85209` |

`description` reçoit le paragraphe RP correspondant. Le récit global « La chute de l'Harmonie » n'est pas
stocké : il n'appartient à aucune maison et vit dans le site.

**Vérification** : appliquer sur le serveur, puis `SELECT * FROM houses` renvoie 4 lignes et
`SELECT COUNT(*) FROM house_games` renvoie un nombre plausible de parties.

---

## Étape 1 — Squelette du module

Objectif : le module existe, compile, est branché, et ne fait rien. Aucun risque en prod.

- `settings.gradle.kts` : `include(":modules:house")` dans le bloc des modules communautaires
- `modules/house/build.gradle.kts` : `plugins { id("fulgurogo-module") }` + `implementation(project(":modules:discord"))`
  (qui réexporte `common`)
- `app/build.gradle.kts` : `implementation(project(":modules:house"))`
- `modules/house/src/main/kotlin/com/fulgurogo/house/HouseModule.kt` : `object` avec `const val TAG = "HSE"`
  et un `init()` vide pour l'instant
- `app/src/main/kotlin/com/fulgurogo/App.kt` : remplacer `// TODO HouseModule` par `HouseModule.init()`,
  après `FgcModule.init()` et **avant** `ApiModule.init(isDebug)` — `api` dépendra de `house`

**Vérification** : `./gradlew build` puis `./gradlew :app:run` démarre comme avant.

---

## Étape 2 — Modèles et lecture seule

Dépend de : 0, 1.

`modules/house/src/main/kotlin/com/fulgurogo/house/db/model/` :

- `House` — `id`, `slug`, `name`, `tagline`, `color`, `description`
- `HouseMember` — `discordId`, `houseId`, `joined`, `pendingAction` (+ `pendingHouseId` en 9.1, la destination d'un `CHANGE`)
- `HouseGame` — `goldId`, `date`, `result`, `ranked`, `longGame`, `handicap`, `blackDiscordId?`, `whiteDiscordId?`
- `HousePoints` — les 7 compteurs, un `total()`, plus `goldId` / `discordId` / `houseId` / `season`
- `HouseStanding` — agrégat par maison : `House` + `memberCount`, `totalPoints`, leader
- `HouseRankedMember` — un joueur dans le classement de sa maison : identité Discord, points par type, total, rang

Tous en `data class` annotées `@GenerateNoArgConstructor`.

`db/HouseDatabaseAccessor.kt`, un `object` avec les noms de tables en `private const val`, toutes les requêtes
via `connection.query(...)` (jamais `createQuery`, sinon la dérivation `snake_case` → `camelCase` est perdue) :

- `houses()`, `house(slug)`
- `member(discordId)`
- ~~`memberCounts()` → `Map<Int, Int>`, pour l'attribution équilibrée~~ (supprimée en 9.1 avec le tirage)
- `standings(season)` → agrégation `SUM` sur `house_points` groupée par `house_id`, jointe à `houses`
- `ranking(season, houseId)` → membres de la maison triés par total décroissant, avec le détail par type
- `playerPoints(season, discordId)` → totaux par type du joueur
- `playerRank(season, discordId)` → rang du joueur dans sa maison

Les agrégats sont paramétrés par saison, donc ce sont des requêtes, pas des vues : une vue ne peut pas
recevoir la saison courante, qui est calculée en Kotlin.

**Vérification** : `./gradlew build`. Pas de comportement observable à ce stade.

---

## Étape 3 — Périodes et saisons

Dépend de : 1. Pas de BDD, purement du calcul de dates — l'endroit le plus facile à se tromper et le plus
facile à relire.

`modules/house/src/main/kotlin/com/fulgurogo/house/HouseSeason.kt` :

- `enum class HousePeriod { SEASON, VACATION }`
- `period(now): HousePeriod` — `VACATION` en juin, juillet et août, `SEASON` sinon
- `seasonName(now): String` — `"2026-2027"`. À partir de septembre : `YYYY-(YYYY+1)`. Jusqu'en mai :
  `(YYYY-1)-YYYY`. En juin, juillet et août, renvoie la saison qui vient de finir.
- `seasonWindow(season): Pair<ZonedDateTime, ZonedDateTime>` — du 1er septembre 00:00 au 1er juin 00:00

Tout passe par `ZonedDateTime.now(DATE_ZONE)` et les helpers de `ZonedDateTimeExtensions.kt`, jamais `now()` nu.

Pendant les vacances, `seasonName` désigne la saison écoulée : c'est volontaire. Une partie du 31 mai
scannée le 1er juin tombe encore dans la fenêtre et compte, alors qu'une partie du 15 juin est hors
fenêtre et ne comptera jamais. La règle « pas de points hors saison » porte sur la **date de la partie**,
pas sur la date du scan, et c'est ce qui évite de perdre les dernières parties de mai.

**Override de dev** : on est le 28 juillet 2026, donc en `VACATION` — le parcours « Rejoindre une maison »
est inatteignable sans triche. Clé optionnelle `house.period.override` valant `SEASON`, `VACATION` ou vide.
Lue une fois, ignorée si vide, et journalisée au démarrage quand elle est active pour qu'on ne l'oublie pas
en prod.

**Vérification** : un `main` jetable, ou un log au démarrage affichant période et saison courantes. À contrôler
au moins pour le 31 août, le 1er septembre, le 31 mai et le 1er juin.

---

## Étape 4 — Le barème

Dépend de : 2, 3.

`modules/house/src/main/kotlin/com/fulgurogo/house/HousePointsCalculator.kt` — une fonction pure, sans accès
BDD, qui prend une partie, le côté du joueur, sa maison et la maison de l'adversaire, et renvoie un
`HousePoints` ou `null` si le joueur n'est pas concerné. Mêmes contours que l'ancien
`ExamPoints.fromGame(game, black)`, avec le barème du tableau plus haut.

Les décisions à respecter exactement : les bonus se cumulent tous, sans exception ; `even_game` vaut
`handicap = 0` et non un score nul, et se cumule donc avec `victory` ; un adversaire de la même maison ne
rapporte que `gold_opponent` ; un adversaire inconnu du serveur ne rapporte ni `gold_opponent` ni
`rival_house`.

Le piège de cette étape est le mot « égalité ». Le lire comme `result = 'jigo'` donne un barème plausible
mais faux, où le bonus ne se déclencherait presque jamais — le komi à 7,5 rend le score nul quasi impossible.
La bonne lecture est le vocabulaire go : jouer à égalité, c'est jouer sans handicap.

**Vérification** : lecture croisée du barème, plus un log détaillé au premier passage du scanner à l'étape 5.
Contrôler en particulier qu'une partie gagnée sans handicap crédite bien `victory` **et** `even_game`.

---

## Étape 5 — Le scanner

Dépend de : 4. C'est l'étape qui écrit en base.

`HousePointsService : PeriodicFlowService(90, 30)` — pas `StalestFirstService`, il n'y a pas de file de lignes
périmées à faire tourner ici, on avance sur des parties non encore scorées. Délai initial de 90 s pour passer
après `GoldService`, intervalle de 30 s pour ne pas tomber sur le même créneau que gold et fgc à 15 s.

`onTick()` :

1. Calcule saison et fenêtre. Si l'override de période dit `VACATION` et qu'on veut couper complètement,
   sortir tôt — sinon la fenêtre suffit à filtrer.
2. Récupère un lot de parties à scorer.
3. Pour chaque partie, calcule les points des deux côtés et insère de 0 à 2 lignes dans `house_points`.

La requête de sélection porte toute la logique d'idempotence :

```sql
SELECT g.* FROM house_games g
  JOIN house_members m
    ON m.discord_id IN (g.black_discord_id, g.white_discord_id)
   AND g.date >= m.joined
  LEFT JOIN house_points p ON p.gold_id = g.gold_id
 WHERE p.gold_id IS NULL
   AND g.date >= :seasonStart AND g.date < :seasonEnd
 ORDER BY g.date
 LIMIT :batch
```

Trois propriétés à ne pas casser :

- **Le `JOIN` sur `house_members`** écarte les parties sans aucun membre impliqué. Sans lui, ces parties
  reviendraient à chaque tick, rempliraient le lot et bloqueraient la progression.
- **`g.date >= m.joined`** interdit le rattrapage : un joueur qui rejoint en novembre ne marque pas sur les
  parties d'octobre encore présentes dans la fenêtre de 32 jours.
- **La fenêtre de saison** rend les parties de juin, juillet et août définitivement inatteignables, ce qui
  implémente « pas de points hors saison » sans état supplémentaire.

Conséquence assumée : le scan est marqué au grain de la partie, l'éligibilité au grain du joueur. Si A est
membre depuis septembre et B depuis novembre, une partie d'octobre entre eux ne crédite que A, et le passage
d'une seule ligne suffit à la considérer comme traitée. C'est le comportement voulu.

Écriture en `INSERT ... ON DUPLICATE KEY UPDATE gold_id = gold_id` ou `INSERT IGNORE`, pour que deux passages
concurrents ne puissent pas doubler une ligne.

**Écarté** : un verrou de config pour éteindre le scanner en local. Il n'a pas de raison d'être — la config de dev
pointe `db.name=fg_dev`, donc un run local écrit dans le schéma de dev et ne touche pas les points de prod. Une clé
de plus à tenir dans trois fichiers, avec le risque miroir de l'oublier en prod et de ne rien marquer en silence,
pour un danger qui n'existe pas.

**Retenu** : lot de 50, et pas de sortie anticipée sur la période. Le scanner tourne aussi en juin, juillet et
août, c'est ce qui lui fait ramasser les dernières parties de mai avant que `CleanService` ne les supprime ; la
fenêtre de saison suffit à écarter les parties de l'été.

**Vérification** : démarrer avec un log par partie scorée, comparer à la main le contenu de `house_points`
avec quelques parties récentes de `house_games`, vérifier qu'un second tick n'ajoute rien.

---

## Étape 6 — API de lecture

Dépend de : 2, 3. Ajouter `implementation(project(":modules:house"))` à `modules/api/build.gradle.kts`.

`GET /gold/api/houses` — la page « Maisons ». Renvoie la période et la saison courantes, puis les 4 maisons
avec leur RP, leur effectif, leur total de points et leur leader.

`GET /gold/api/house/{slug}` — la page d'une maison. Le RP complet, plus le classement de ses membres par
points. 404 sur un slug inconnu.

Nommage cohérent avec l'existant : pluriel pour la liste, singulier pour le détail, comme `players` /
`player/{id}`. Les deux handlers suivent le pattern maison de `Api` : `context.handle("nomDeRoute") { ... }`,
réponses via les helpers de `ContextExtensions`.

La période est incluse dans la réponse plutôt que dans un endpoint dédié, pour que le site n'ait pas à la
recalculer ni à faire un aller-retour de plus. Le serveur reste la seule source de vérité sur le calendrier.

**Forme des réponses** — ⚠ arrêtée côté serveur, pas encore confirmée avec le dépôt du site (point ouvert 5).

```json
// GET /gold/api/houses
{
  "period": "SEASON", "season": "2025-2026",
  "houses": [
    {
      "slug": "gardiens", "name": "Les Gardiens", "tagline": "…", "color": "#3366cc", "description": "…",
      "memberCount": 3, "totalPoints": 30,
      "leader": { "discordId": "111", "discordName": "Alice", "discordAvatar": "…", "rank": 1,
                  "points": { "played": 2, "goldOpponent": 4, "rivalHouse": 4, "longGame": 4,
                              "victory": 4, "evenGame": 2, "ranked": 2, "total": 22 } }
    }
  ]
}

// GET /gold/api/house/{slug} : mêmes period et season, la maison sous la clé "house" (forme identique,
// leader compris), plus "members": [ … ] — la même forme que "leader", tous les membres, meilleur d'abord.
```

Quatre points de ce contrat qui ne se devinent pas :

- **`total` est dans la réponse.** C'est le seul endroit où l'API le calcule, et il vient de
  `HousePointsBreakdown.total()`. Sans lui chaque consommateur additionnerait les sept colonnes lui-même, et un
  barème qui gagne une colonne serait faux sur le site en restant juste sur le serveur.
- **`houses` est trié par total décroissant, puis effectif croissant, puis nom.** À total égal, la plus petite
  maison passe devant : le même total atteint avec moins de joueurs est la meilleure performance, et ça compense
  l'avantage mécanique d'avoir plus de monde qui marque. Le nom ne départage que l'égalité sur les deux. Conséquence
  à connaître : l'ordre dépend donc de `memberCount`, qui ne compte que les membres actuels alors que `totalPoints`
  garde les points de ceux qui sont partis — un départ peut réordonner deux maisons à égalité sans que personne
  n'ait marqué. L'ordre n'est pas celui du classement des membres d'une maison, qui ne départage que sur le nom.
  Pas de rang sur les maisons : à quatre, une égalité n'est pas improbable, et un rang compté sur la position
  afficherait un 2e et un 3e là où il y a deux 2es — c'est aussi pourquoi ces départages ne sont qu'un ordre
  d'affichage et ne prétendent pas qu'une maison a battu l'autre.
- **`rank` est un rang de compétition** (1, 2, 2, 4), pas la position dans la liste : le site l'affiche, il ne
  le recompte pas.
- **Les nulls sont explicites.** Une maison vide renvoie `"leader": null`, pas une clé absente — c'est le
  mapper JSON de Javalin qui sérialise les réponses, pas le Gson utilisé pour lire les corps de requête.
  Vérifié au `curl`, contre `fg_dev`.
- **Le slug ne tient pas compte de la casse.** `/gold/api/house/nexus_alpha` répond comme
  `/gold/api/house/NEXUS_ALPHA` : la collation MySQL de la colonne est insensible à la casse. À ne pas prendre
  pour une garantie de l'API — c'est la base qui le fait, pas le code.

`memberCount` et `totalPoints` ne comptent pas la même population, exprès : le total somme le registre par
maison et garde donc les points des joueurs partis, l'effectif et le leader ne voient que les membres actuels.
Le total est donc supérieur ou égal à la somme des totaux des membres, et une maison vide peut afficher des
points. `members` peut aussi être plus court que `memberCount` : un membre sans ligne `discord_user_info` n'a ni
nom ni avatar à montrer et sort du classement.

**Vérification** : `curl` sur les deux routes, en local, contre la prod.

---

## Étape 7 — API de mutation

Dépend de : 6.

> **Amendement (9.1) — le joueur choisit sa maison.** Le tirage au sort a été retiré : les deux corps de requête
> portent un `slug`, `HouseAssignment` et `HouseDatabaseAccessor.memberCounts()` ont été supprimés, et rien
> n'équilibre plus les effectifs. Ce qui suit décrit la version d'origine ; les écarts effectifs sont : `join`
> exige un `slug` et répond `404` sur un slug inconnu ; `choice` accepte un `slug`, obligatoire sur `CHANGE`
> (`400` sans, `404` sur un slug inconnu, `400` si c'est la maison actuelle) et ignoré sur `STAY` et `LEAVE` ; la
> destination d'un `CHANGE` est stockée dans `house_members.pending_house_id`
> (`doc/migration choix de maison.sql`). Le reste — codes de retour, absence d'auth, forme du 200, double 409,
> 404 lu et pas déduit — tient tel quel.

`POST /gold/api/house/join` — corps `{ "discordId": "..." }`. Attribution au sort parmi les maisons les moins
peuplées.

Codes de retour :
- `400` corps invalide
- `403` on est en `VACATION`
- `404` `discord_id` inconnu, ou aucun compte lié (pas de ligne `gold_ratings`)
- `409` le joueur a déjà une maison
- `200` avec la maison attribuée

`POST /gold/api/house/choice` — corps `{ "discordId": "...", "action": "STAY" | "CHANGE" | "LEAVE" }`.
Enregistre l'intention dans `pending_action`, sans rien appliquer. Modifiable autant de fois qu'on veut
pendant l'été.

- `400` corps ou action invalide
- `403` on n'est pas en `VACATION`
- `404` le joueur n'a pas de maison
- `204` enregistré

L'attribution équilibrée : lire les effectifs des maisons candidates, garder celles à l'effectif minimum,
en tirer une au sort. Pour un changement, exclure la maison actuelle des candidates.

Pas de vérification d'identité, conformément à la décision : le `discordId` du corps est pris tel quel, comme
le fait déjà `POST /gold/api/link`. Un tiers peut donc déclencher un `LEAVE` sur un autre joueur. C'est un
choix assumé — le site n'est pas critique de ce point de vue — tracé ici pour que ce ne soit pas une surprise
plus tard.

L'annonce Discord de l'arrivée dans une maison se branche ici, mais elle est écrite à l'étape 10 avec les
deux autres, pour que les trois messages soient rédigés d'un bloc.

**Forme du 200 de `join`** — la maison attribuée, son RP seul, sans effectif ni total :

```json
{ "slug": "gardiens", "name": "Les Gardiens", "tagline": "…", "color": "#3366cc", "description": "…" }
```

Sous-ensemble strict de la forme `house` de l'étape 6, mêmes noms de champs, pour que le site l'affiche avec le
même code. Les chiffres sont omis plutôt que remplis : un `join` répond sur la maison tirée, un effectif relu
juste après serait une deuxième question que personne n'a posée. `choice` ne renvoie pas de corps (204).

Trois choix d'implémentation qui ne sont pas dans les codes de retour :

- **Le 409 est répondu deux fois.** La lecture de `member` en fait la réponse normale, et `addMember` le
  reconfirme depuis la clé primaire — la seule des deux qui tienne si deux `join` du même joueur arrivent
  ensemble. Même contrat que `addGame` : c'est la clé qui arbitre, pas l'hypothèse d'un seul écrivain.
- **Le 404 de `choice` vient d'une lecture, pas du nombre de lignes touchées.** MySQL rapporte 0 ligne
  modifiée aussi bien pour un membre inconnu que pour une intention déjà à cette valeur : un 404 déduit de là
  répondrait « inconnu » à un joueur qui enregistre `LEAVE` deux fois.
- **`HouseAction` est un enum**, et c'est lui le vocabulaire de la colonne `pending_action`. Le corps de requête
  garde un `String` parce que Gson mappe un nom d'enum inconnu sur null sans un mot, alors qu'une action
  inconnue doit ressortir en 400. La comparaison ignore la casse et les espaces autour.

`HouseAssignment.draw(excluding)` porte le tirage, dans le module maison et pas dans le handler : l'étape 9 tire
de la même façon pour les `CHANGE`, et deux tirages divergeraient. La restriction à l'effectif minimum se fait
*après* l'exclusion, sinon le tirage d'un changement n'est plus équilibré sur les trois maisons restantes.

**Vérification** : override de période sur `SEASON`, `join` sur un compte de test, vérifier que la maison
tombe bien sur un effectif minimum ; override sur `VACATION`, vérifier que `join` répond 403 et que `choice`
écrit bien `pending_action`.

---

## Étape 8 — Bloc maison dans le profil joueur

Dépend de : 6.

`GET /gold/api/player/{id}` gagne un bloc `house`, `null` quand le joueur n'a pas de maison, sinon : slug,
nom, couleur, slogan, points par type, total, rang dans la maison, et l'intention en cours pendant les
vacances.

Composé dans le handler à partir de `HouseDatabaseAccessor`, **pas** en modifiant la vue `api_players` :
le calcul dépend de la saison courante, qui vient du Kotlin, et ça évite une modification de vue en prod.

**Forme du bloc** :

```json
// GET /gold/api/player/{id}, à côté de "accounts", "rating", "games"…
"house": {
  "period": "SEASON", "season": "2025-2026",
  "slug": "NEXUS_ALPHA", "name": "Nexus Alpha", "tagline": "…", "color": "#0E1A40",
  "points": { "played": 1, "goldOpponent": 2, "rivalHouse": 2, "longGame": 2,
              "victory": 2, "evenGame": 1, "ranked": 1, "total": 11 },
  "rank": 1,
  "pendingAction": null
}
```

Quatre points de ce contrat qui ne se devinent pas :

- **`period` et `season` sont dans le bloc**, ajout à ce qu'énumérait le plan. Sans la période, un
  `pendingAction` nul est ambigu — hors vacances il n'y a rien à montrer, en vacances il veut dire « pas encore
  choisi », et le site ne peut pas trancher sans redemander le calendrier à `/gold/api/houses`. `season` étiquette
  les points sans que le site ait à deviner sur quoi ils sont comptés. Même raison qu'à l'étape 6 : le serveur reste
  la seule source de vérité sur le calendrier.
- **`pendingAction` n'est rempli qu'en `VACATION`**, et il est *parsé* en `HouseAction` au lieu d'être recopié :
  une valeur que la colonne ne devrait jamais contenir ne ressort donc pas telle quelle vers le site.
  *Amendement (9.1)* : un `pendingHouse` l'accompagne — le blason (slug, nom, couleur) de la maison visée — rempli
  sous la même condition et seulement sur un `CHANGE`, puisque c'est la seule intention qui ait une destination. Le
  `pending_house_id` de la base, lui, ne sort pas : les identifiants internes de maison restent hors de l'API.
- **Pas de `description`, ni d'effectif, ni de total de maison.** Le bloc porte le blason (slug, nom, slogan,
  couleur), pas la page de la maison — sinon chaque profil traînerait quatre paragraphes de RP. Les noms de champs
  partagés sont les mêmes qu'à l'étape 6, donc le site stylise le badge avec le même code.
- **`points` et `rank` viennent de la même lecture.** `HouseDatabaseAccessor.playerStanding` prend la ligne du
  joueur dans le classement de sa maison au lieu de resommer ses points à part : le total affiché est par
  construction celui qui a produit le rang. Deux sommes à une connexion d'écart pourraient encadrer un tick du
  scanner et se contredire.

Cette méthode remplace `playerPoints` et `playerRank` de l'étape 2, que rien n'avait jamais appelées et qui
faisaient à elles deux quatre allers-retours là où une seule connexion suffit. `HousePointsTotal`, qui n'était
mappé que par `playerPoints`, disparaît avec elles.

**Vérification** : faite. Trois membres et trois lignes de points posés à la main dans `fg_dev`, deux à égalité
sur 11 : le profil rend bien 1, 1, 3 — le rang de compétition, pas la position — un total de 11 qui est le maximum
du barème, `pendingAction: "CHANGE"` en `VACATION` et `null` avec l'override sur `SEASON`, et `"house": null` sur
un joueur sans maison. Les trois routes s'accordent : 11 + 11 + 5 = les 27 points que `/gold/api/houses` annonce
pour la maison, et les rangs du classement sont ceux des profils. Lignes de test supprimées après coup.

---

## Étape 9 — Transition de saison

Dépend de : 3, 7.

`HouseSeasonService : PeriodicFlowService(120, 600)` — un tick toutes les 10 minutes suffit pour des
événements datés au jour. Même cadence que `ping` et `clean`.

`onTick()` traite deux bascules, toutes deux protégées par `house_seasons` :

**Clôture** — on est en `VACATION`, la saison écoulée a `opened IS NOT NULL` et `closed IS NULL` : figer le
classement final, annoncer (étape 10), écrire `closed = NOW()`.

La condition `opened IS NOT NULL` est ce qui évite le faux départ : au premier déploiement en juillet 2026,
`seasonName` renvoie `2025-2026`, saison qui n'a jamais existé en base. Sans ce garde-fou, on annoncerait la
clôture d'une saison vide.

**Ouverture** — on est en `SEASON` et la saison courante n'a pas de ligne avec `opened` renseigné :

1. Appliquer les intentions : `STAY` ou `NULL` → rien ; `CHANGE` → réattribution au sort parmi les 3 autres,
   effectif minimum d'abord, `joined` remis à maintenant, et annonce d'arrivée comme un join ordinaire ;
   `LEAVE` → suppression de la ligne

> **Amendement (9.1)** — un `CHANGE` va dans la maison que le joueur a nommée, lue dans `pending_house_id` ; il n'y
> a plus de tirage. Une destination inapplicable — absente, supprimée depuis, ou la maison actuelle — laisse le
> membre où il est, avec une ligne de log : le tick suivant n'y changerait rien, et le nettoyage groupé de fin
> d'ouverture efface l'intention. Le nettoyage remet les deux colonnes à `NULL`, pas seulement `pending_action`.
2. Remettre tous les `pending_action` à `NULL`
3. `opened = NOW()`

Les points ne sont pas effacés : ils portent leur saison et les lectures filtrent dessus. L'historique existe
donc sans travail supplémentaire.

Quatre points d'implémentation qui ne sont pas dans la description des deux bascules :

- **L'ordre des deux écritures est inversé entre les branches, exprès.** La clôture annonce *puis* écrit
  `closed` : un crash entre les deux réannonce le récap au tick suivant, alors qu'écrire d'abord brûlerait le
  garde-fou et perdrait le récap pour de bon. Un doublon se voit et se supprime, un message de fin de saison
  manquant se remarque un an plus tard. L'ouverture, elle, réclame `opened` en **dernier** : chaque intention
  s'efface en s'appliquant, donc un tick interrompu laisse chaque membre soit traité soit encore porteur de son
  intention, et le tick suivant reprend exactement le reste. Réclamer l'ouverture d'abord les laisserait en
  plan — garde-fou dépensé, personne déplacé.
- **`CHANGE` est une seule requête** : `house_id`, `joined` et `pending_action = NULL` ensemble. En deux passes,
  un redémarrage entre les deux redéplacerait le joueur, dans une deuxième maison.
- **Le compte de lignes est fiable pour `opened` et `closed`**, contrairement à ce que dit `setPendingAction` :
  le prédicat (`opened IS NULL`, `closed IS NULL`) est dans le `WHERE` et le `SET` le rend faux, donc une ligne
  *matchée* est forcément une ligne *changée* et les deux sémantiques de MySQL tombent d'accord. Ça donne un
  « c'est moi qui l'ai fait » utilisable sans dépendre de `useAffectedRows`.
- **L'ordre d'affichage des maisons est factorisé** dans `List<HouseStanding>.ranked()`, côté module maison :
  total décroissant, puis effectif croissant, puis nom (cf. étape 6). Il y a désormais trois lecteurs — la route
  `houses`, le récap de clôture, le classement quotidien de l'étape 10 — et un podium qui diffère entre le site et
  le bot n'est pas le genre de bug que quelqu'un signale.

Un `pending_action` illisible est laissé tel quel plutôt que deviné : `HouseAction.from` répond null, une ligne
de log le dit, et le nettoyage groupé de fin d'ouverture l'efface. Le traiter comme un `STAY` arriverait au même
endroit, mais en silence.

**Vérification** : faite, contre `fg_dev`, en quatre passes — quatre membres, un par intention (`STAY`,
`CHANGE`, `LEAVE`, `NULL`), et deux lignes de points dont une pour un joueur qui part.

1. *Faux départ* — `VACATION`, `house_seasons` vide : le service tick (confirmé via `/gold/api/health`) et ne
   fait rien. Aucune ligne créée, aucun récap. C'est le garde-fou `opened IS NOT NULL` du premier déploiement.
2. *Ouverture* — override sur `SEASON` : le `STAY` garde sa maison, le `CHANGE` est redessiné hors de la sienne
   avec `joined` restampé, le `LEAVE` disparaît, le membre sans intention n'est pas touché, tous les
   `pending_action` retombent à `NULL`, et `opened` est écrit.
3. *Clôture* — override retiré, donc `VACATION` : `Closing season 2025-2026: FILS_DU_FROID 11,
   SABRE_SILENCIEUX 5, LUNAIRES_AETHER 0, NEXUS_ALPHA 0`, puis `closed` écrit. L'ordre est bien celui de
   `ranked()`, et surtout `SABRE_SILENCIEUX` affiche 5 points avec zéro membre : les points du joueur parti sont
   restés à la maison, ce qui vérifie la décision au passage. ⚠ Ce relevé date d'avant le passage au départage
   par effectif ; les deux maisons à 0 y sont encore départagées par le nom seul, et avec la règle actuelle
   `NEXUS_ALPHA` (0 membre) passerait devant `LUNAIRES_AETHER` (1 membre après le `CHANGE`).
4. *Idempotence* — redémarrage, nouveau tick : rien, les deux garde-fous tiennent.

Le tirage du `CHANGE` n'est pas déterministe et n'a pas à l'être : le seed laissait deux maisons vides, le
tirage a pris l'une des deux. Ce qui est vérifié, c'est qu'il exclut la maison d'origine et qu'il tire à
effectif minimum. Ce passage est aussi la première exécution réelle de `HouseAssignment.draw`, que l'étape 7
avait laissée non vérifiée faute de base accessible.

Lignes de test supprimées après coup.

---

## Étape 10 — Annonces Discord

Dépend de : 9. Le module dépend déjà de `discord` depuis l'étape 1.

Tout part sur `bot.notification.channel.id` via `DiscordModule.discordBot.sendMessageEmbeds(...)`, aucune
nouvelle clé de config.

Trois annonces, pas plus.

**Classement quotidien** — un message le matin, entre 7h et 9h : total des 4 maisons et meilleur membre de chacune.
Garde-fou obligatoire : `house_seasons.last_ranking`. Avec un tick de 600 s, la fenêtre 7h–9h contient 18
ticks ; sans le garde-fou, ça fait 18 messages. Condition : `last_ranking IS NULL` ou antérieur à minuit du
jour courant. Écrire `last_ranking` juste après l'envoi. Porté par `HouseSeasonService`.

**Arrivée dans une maison** — un message à chaque attribution, avec le joueur et sa maison. Déclenché depuis
les deux endroits qui attribuent : le handler `POST /gold/api/house/join` (étape 7) et l'application des
intentions `CHANGE` à l'ouverture de saison (étape 9). Le mieux est une seule fonction d'annonce appelée par
les deux, sinon les deux messages divergeront.

**Récap de fin de saison** — la maison gagnante, le classement final des 4 maisons, le meilleur joueur de
chacune. Une fois, à la clôture, garde-fou `house_seasons.closed`.

Pas d'annonce d'ouverture de saison : le 1er septembre, ce sont les arrivées et le classement quotidien qui
parlent.

Textes en français, c'est du contenu vu par les joueurs. Ton et longueur à calquer sur l'ancien
`ExamPointsService` (`git show 9c973cf^:app/src/main/kotlin/com/fulgurogo/features/exam/ExamPointsService.kt`),
qui donne le registre attendu sur ce serveur.

**Tranché** : le classement quotidien liste les 4 maisons **et** le meilleur membre de chacune, pas un top
joueurs global. C'est ce que `standings()` renvoie déjà — un top N inter-maisons aurait demandé une requête de
plus pour une information que la page des maisons donne mieux.

Trois décisions d'implémentation qui ne sont pas dans la description des annonces :

- **Le classement quotidien réclame `last_ranking` *avant* d'envoyer**, à l'inverse de la clôture. Les deux
  fréquences n'ont pas le même pire cas : quotidien, un doublon est du spam et un oubli est invisible ; annuel,
  un doublon est du bruit et un oubli est un récap perdu. Le compromis coûte d'ailleurs moins qu'il n'y paraît,
  parce qu'il n'y a rien à attendre : `sendMessageEmbeds` passe le message à la file de JDA et rend la main, donc
  « écrire après l'envoi » n'aurait jamais voulu dire qu'« après la mise en file » et n'aurait rien confirmé sur
  la réception. Réclamer d'abord a le même sens et ne peut pas doubler.
- **Les maisons ne sont pas numérotées** dans les messages. L'ordre porte le classement, et un numéro compté sur
  la position afficherait un 2e et un 3e là où deux maisons sont à égalité — même raison qu'à l'étape 6. La
  maison gagnante du récap est lue sur les totaux et pas sur la première ligne, donc une vraie égalité est
  annoncée comme telle au lieu de couronner celle qui a trié première.
- **`HouseNotifier` ne fait que du texte.** Le *quand* reste dans les appelants, parce que les garde-fous sont des
  lignes de `house_seasons` et qu'un formateur qui irait interroger la base pour décider s'il parle serait bien
  plus dur à relire. Une ligne de log par annonce, en revanche, est indispensable : `queue()` ne rend rien, donc
  sans elle on ne peut pas répondre après coup à « le récap est-il parti ? ».

**Tranché** : `HOUSES_PATH` vaut `/houses`, confirmé contre les routes du site — et pas `/maisons` comme le
supposait la première écriture. Le lien du classement complet pointe donc au bon endroit.

**Vérification** : faite, contre `fg_dev` et le bot de dev, en trois passes. Le texte a été capturé en logguant
temporairement le corps des messages, instrumentation retirée depuis — le rendu final dans Discord reste à
regarder de visu, c'est la seule chose que le serveur ne peut pas contrôler lui-même.

1. *Ouverture* — l'arrivée d'un `CHANGE` et le classement quotidien partent tous les deux. Ordre affiché :
   `Fils du Froid 12 (2 membres)`, `Nexus Alpha 7 (0 membre)`, `Sabre Silencieux 0 (0 membre)`,
   `Lunaires d'Æther 0 (1 membre)` — les deux maisons à 0 sont bien départagées par l'effectif. Singuliers et
   pluriels corrects (`0 point`, `1 membre`, `2 membres`), et `Nexus Alpha` garde ses 7 points avec 0 membre.
2. *Garde-fou* — redémarrage, nouveau tick le même jour : **aucun** classement renvoyé. Puis
   `POST /gold/api/house/join` sur un compte libre : une ligne `joinHouse`, une annonce d'arrivée, et un second
   POST identique répond 409. Ce passage vérifie au réel `addMember`, son 409 et `GoldDatabaseAccessor.player`,
   que l'étape 7 avait laissés non vérifiés.
3. *Clôture* — `Les **Fils du Froid** remportent la saison 2025-2026 !`, le classement final des 4 et le meilleur
   membre de chacune, puis `closed` écrit.

Deux corrections trouvées à l'écriture des messages, pas à la relecture : la ligne du meilleur membre était
indentée avec des **espaces insécables** (U+00A0) entrées par accident, remplacées par un préfixe `↳` — Discord
les aurait probablement rendues, mais une indentation invisible dans le source qui dépend du client pour
s'afficher n'est pas quelque chose à livrer. Le reste du module a été scanné, aucun autre invisible.

---

## Étape 11 — Finitions

Dépend de : tout ce qui précède.

**Purge** — fait : `house_members` ajouté à la liste de `CleanDatabaseAccessor.removeAllFrom`, et **pas**
`house_points`, parce que supprimer les points d'un joueur parti ferait rétrécir le total de sa maison, ce qui
contredit la décision « les points restent acquis à la maison ».

⚠ **Cette purge n'est plus commentée**, contrairement à ce que disait cette étape (et `CLAUDE.md`, corrigé au
passage). `CleanService.removeUsersWhoLeft` tourne, gardée par `debug` — un run de dev n'agit jamais sur les
drapeaux de départ, puisqu'en debug le bot ne sait pas qui est encore sur le serveur — et par un délai de grâce
d'un jour qui couvre aussi le départ-retour. L'ajout n'est donc pas inerte : en prod, quitter le Discord
supprimera bien l'appartenance. C'est le comportement voulu, mais il fallait le dire dans ce sens.

C'est aussi le seul chemin de sortie d'une maison en cours de saison. L'API ne propose « Quitter » qu'en
vacances, et rien d'autre ne supprime une ligne de `house_members` pendant la saison.

**Santé** — rien à faire, et vérifié : les deux services remontent dans `GET /gold/api/health`, qui répond 200
avec les dix services sains. Les seuils de péremption sont **240 s et 3120 s**, pas 150 s et 3000 s comme
l'annonçait cette étape : la formule est `max(intervalle × 5, 60 s) + délai initial`, et les délais initiaux de
90 s et 120 s avaient été oubliés.

**Config** — rien à faire, c'était déjà en place : `house.period.override` est présente, vide, dans les trois
fichiers de `modules/common/src/main/resources/` — `config.properties.dev`, `config.properties.prod` et la copie
de travail `config.properties`. Les trois, pas seulement les deux variantes : seul `config.properties` est sur le
classpath, donc une clé ajoutée aux variantes sans être recopiée n'existe pas pour l'application qui tourne en
local. C'est la seule clé du module, et la seule du projet à être lue par `Config.getOrNull` plutôt que
`Config.get` — donc la seule qui puisse rester vide sans faire tomber un service.

Le suffixe se place après `.properties` et pas avant. `release.sh` copie `config.properties.dev` et
`config.properties.prod` par nom exact ; un fichier nommé `dev.config.properties` casse la release sans
message d'erreur. Aucun `GitConfig.kt` à mettre à jour, il n'existe plus — les identifiants vivent dans ces
trois fichiers depuis leur suppression.

**Documentation** — fait. Dans `CLAUDE.md` : `house` dans l'ordre d'`init()` (avant `api`, qui en dépend),
`house.period.override` à l'énumération des clés, les intervalles et les délais initiaux au paragraphe sur
l'étalement des ticks, un point 4 sur les maisons dans le flux de données, et deux corrections au passage — le
point sur l'API disait que tout sortait de deux vues, ce qui n'est plus vrai des routes maisons, et celui sur
`CleanService` disait la purge commentée.

Dans `doc/changelog.txt`, trois lignes en français dans le style du fichier — le changelog annonçait déjà « le
remplaçant arrive bientôt » à propos de la disparition de l'Exam Hunter, promesse que les maisons tiennent.
⚠ Le fichier est une liste à plat sans marqueur de version : les lignes ajoutées cohabitent donc avec celles de
8.8 sans qu'on puisse les distinguer. À voir s'il faut le remettre à zéro pour 8.9.

**Version** — `fulgurogo.version.name` passe de 8.8 à 8.9, et `./gradlew :app:shadowJar` produit bien
`app-8.9-all.jar`.

**Vérification** : faite. `./gradlew build` passe, `./gradlew :app:run` démarre, et `/gold/api/health` répond 200
avec les dix services sains, `HousePointsService` et `HouseSeasonService` compris.

---

## Points ouverts

À trancher avant ou pendant l'exécution, aucun ne bloque le démarrage :

1. ~~**`house.scanner.enabled`** (étape 5)~~ — écarté : `fg_dev` isole déjà les écritures locales, cf. étape 5.
2. ~~**Taille du lot du scanner** (étape 5)~~ — 50.
3. ~~**Intervalles**~~ — 90/30 pour le scanner (étape 5), 120/600 pour la saison (étape 9), tous retenus.
4. ~~**Formulation des messages Discord** (étape 10), et contenu du classement quotidien~~ — écrits et vérifiés,
   cf. étape 10. `HOUSES_PATH` est confirmé à `/houses`.
5. **Contrat avec le dépôt du site** — la forme des réponses de `/gold/api/houses` et `/gold/api/house/{slug}`
   est écrite à l'étape 6 et implémentée, mais ⚠ elle n'a pas été confirmée avec le front : c'est un contrat
   posé, pas un contrat négocié. Reste entière la convention de nommage des blasons à partir du slug, que le
   serveur n'a pas à connaître.
