# Plan d'exécution — Ligue d'Aurak (partie serveur)

Spécification d'origine : le brief « Ligue d'Aurak ». Contrat OGS : **`doc/ogs-online-league-api.md`**, qui est la
référence à jour de l'API — le [wiki](https://github.com/online-go/online-go.com/wiki/Custom-OGS-Online-Leagues) n'en
documente qu'une partie, la [spec OpenAPI](https://online-go.com/api-docs/) le reste, et une sonde du 10 août 2026 a
mesuré ce que ni l'un ni l'autre ne dit. Scope de ce plan : modèle BDD, accès, client OGS, tirages, ingestion des
résultats, renommée, API, annonces Discord et MP. Le site est traité dans son propre dépôt.

Même forme que `doc/plan-maisons.md` : chaque étape compile seule, s'exécute dans l'ordre, et porte sa propre
vérification. Les vingt questions que la spécification laissait ouvertes sont tranchées ; le **journal des décisions**
en fin de document donne chaque choix et son motif, et la section qui le précède liste ce qui reste à constater — rien
qui bloque l'écriture du code. Les ⚠ du corps du texte signalent ce qu'il ne faut pas casser en relisant, pas des
questions en attente.

---

## Décisions actées

| Sujet | Décision |
|---|---|
| Calendrier | La saison de la ligue est celle des maisons : **1<sup>er</sup> septembre → 31 mai**, 16 sessions |
| Périmètre | Un joueur de la ligue est membre d'une maison **et** a un compte OGS lié |
| Académie | Une académie = une maison. Pas de nouvelle entité, `houses` fait foi |
| Adversaires | Jamais deux membres d'une même maison |
| Tirage | Un par début de session, automatique, sur les membres **actifs**, dans la fenêtre **7h-9h** |
| Critères de tirage | `\|Δrating gold\| + 400 × rencontres déjà jouées`, à minimiser. Glouton puis 2-opt |
| Couleurs | Tirées au sort à chaque match |
| Parties OGS | `japanese`, 19×19, handicap 0, `byoyomi` 40 min + 5×30 s — envoyé dans le payload de chaque rencontre |
| Identité OGS | `member_id` = `sha256(discordId + league.member.salt)`, calculé et non stocké |
| Ligue OGS | **Une seule ligue permanente**, `FulguroGo`, créée à la main. Elle traverse les saisons, et **il n'y a pas de ligue de dev** |
| Isolation dev / prod | Aucune côté OGS : dev et prod partagent la ligue. Le seul garde-fou est `league.test.players` |
| Bac à sable de dev | En dev, seuls Drooxi et Judas peuvent entrer dans la ligue. Clé vide en prod (voir plus bas) |
| Liens de challenge | Noir et blanc en MP Discord, spectateur public sur le site |
| MP en échec | Les liens restent en base, renvoi manuel au cas par cas. Une ligne de log le signale |
| Sortie | Un joueur peut quitter/revenir en cours de saison ; il devient « inactif », ses points restent |
| Délier OGS | Sortie automatique de l'académie |
| Nouvelle saison | Académies vidées le 1<sup>er</sup> septembre, tout le monde repostule |
| Barème | 2 pts par match joué, 5 pts par victoire, 10 pts si tous les matchs sont joués |
| Bonus « sans faute » | **16 matchs joués ou exemptés.** Une session où le tirage n'a pas trouvé d'adversaire ne pénalise pas le joueur |
| Exemption | Ne rapporte aucun point. Le banc revient au candidat le moins souvent exempté de la saison, au sort à égalité |
| Match non joué | 0 point pour les deux, **ni exemption ni bonus**, et définitivement clos au règlement de la session |
| Date limite | La partie doit être **lancée** avant minuit le dernier jour de la session, et **terminée** au règlement — la première fenêtre 7h-9h après la fin de la session |
| Classement | Par joueur : nom, maison, joués, victoires, défaites, renommée |
| Accès OGS | Clé en main, ligue `FulguroGo` créée. API vérifiée par sonde le 10 août 2026 — cf. `doc/ogs-online-league-api.md` |

---

## L'API Custom OGS Online Leagues

Quatre appels suffisent à la ligue. La surface réelle est plus large — et comporte des endpoints dangereux —, elle est
détaillée dans `doc/ogs-online-league-api.md`.

**Authentification** : deux en-têtes sur chaque requête, `X-OGS-LEAGUE` (l'identifiant de la ligue) et
`X-OGS-LEAGUE-AUTH` (la clé d'API, délivrée par les administrateurs OGS). Pas d'OAuth, pas de session, pas de CSRF.

| # | Appel | Rôle |
|---|---|---|
| 1 | `PUT /api/v1/online_league/member/{member_id}` — corps `{"rating": <int>}` | Inscrit un membre, ou met à jour son rating de départ |
| 2 | `POST /api/v1/online_league/matches/` — les deux `*_member_id`, notre `league_match_id`, et **tous les réglages de la partie** | Crée une rencontre, ou renvoie l'existante. Rend `id`, `black_invite`, `white_invite`, `spectator_link` |
| 3 | `GET /api/v1/online_league/matches/?league_match_id__startswith=…` | État et résultat de **toutes** les rencontres d'une session, en un appel |

Trois appels, et **pas de callback**. `PUT /callback` existe, et il a été écarté : voir la conséquence dédiée plus bas.

Cinq conséquences qui déplacent le plan par rapport à une lecture naïve du brief :

- **La ligue est unique et permanente.** Elle s'appelle `FulguroGo`, elle est déjà créée, et elle traverse les saisons :
  ce que le 1<sup>er</sup> septembre déclenche côté serveur, c'est le vidage des académies et rien d'autre. Un
  `POST /leagues/` existe bien dans la spec OpenAPI — donc en créer une autre serait possible, contrairement à ce que
  le wiki laisse croire — mais ce n'est pas ce qu'on fait, et l'appeler avec la clé d'une ligue existante n'a pas été
  essayé.
- **Le `member_id` est le nôtre.** Il est dans l'URL, pas retourné par OGS. L'API ne demande **jamais** l'identifiant
  OGS du joueur — le lien entre un compte OGS et un `member_id` se fait quand le joueur clique son invitation et se
  connecte (« registration happens automatically if needed »). C'est ce qui rend le « ils restent dans la ligue OGS
  après avoir quitté l'académie » gratuit : un `member_id` inscrit une fois l'est pour toujours.
- **Le `league_match_id` est le nôtre aussi**, et c'est la clé de l'idempotence : un `POST /matches/` rejoué avec le
  même `league_match_id` répond **200** au lieu de **201** et renvoie la rencontre existante, mêmes liens compris.
  Mesuré. Reprendre une création interrompue se réduit donc à rejouer l'appel.
- **Le callback existe, et il est écarté.** C'est un simple `GET` sur une URL à nous avec l'`id` substitué : il ne
  transporte **aucune donnée**, donc il faut de toute façon relire le résultat chez OGS — exactement l'appel que le
  balayage fait déjà. Il n'était donc jamais une source de résultats, seulement une notification. Or `GET /matches/`
  rend l'objet complet des 35 champs pour toutes les rencontres à la fois, et **tout champ du modèle est un filtre**,
  `__startswith` compris : un `?league_match_id__startswith=fg_prod_2026-2027_8_` rend exactement les rencontres d'une
  session, d'une saison et d'un environnement. Un appel par balayage, borné pour toujours. Le callback n'achetait donc
  que de la latence, sur un classement réglé une fois par quinzaine — contre une route publique tenue de répondre 200 à
  n'importe quel id, une étape de déploiement manuelle dont l'oubli était silencieux, et l'inconnue « quel id OGS
  envoie-t-il ? ». Décision 18.
- **Les réglages de partie sont les nôtres**, rencontre par rencontre. Le wiki ne documente que `handicap` et laisse
  croire le contraire ; la spec OpenAPI et la sonde disent autrement — `rules`, `height`, `width`, `time_control`,
  `main_time`, `periods`, `period_time` et leurs équivalents pour les autres systèmes de temps sont tous modifiables, et
  réellement validés : un `byoyomi` sans `periods` répond 400. Il n'y a donc **rien à demander à OGS** sur la
  configuration : tout part dans le payload.

### Les réglages de partie

Ce n'est pas une liste de préférences : chaque ligne est là parce qu'une autre valeur casse quelque chose ailleurs dans
l'application. Ce sont des constantes de `OgsLeagueClient`, envoyées à chaque `POST /matches/`.

| Champ | Valeur | Pourquoi cette valeur et pas une autre |
|---|---|---|
| `height`, `width` | **19** | `fgc_validity_games` ne retient que le 19×19. Un 13×13 sortirait les parties de ligue du comptage FGC |
| `rules` | **`japanese`** | ✅ Mesuré : la partie sort avec un **komi de 6,5** — et non 7,5, comme une version précédente de ce plan l'affirmait. La conclusion tient pour une autre raison : c'est le **demi-point** qui rend le jigo impossible, pas la valeur. Et 6,5 passe la fenêtre de FGC, `komi > 6 AND komi < 9`. ⚠ Mais la marge n'est plus que de 0,5 : si OGS passait un jour son défaut japonais à 6,0, les parties de ligue sortiraient silencieusement du comptage FGC |
| `handicap` | **0** | FGC exige `handicap = 0`, et les maisons créditent le bonus `even_game` dessus. Le tirage par rating proche fait déjà l'équilibrage ; `-1` (automatique) rendrait les parties plus justes mais les sortirait des deux comptages |
| `time_control` | **`byoyomi`** | Le système que `isLongGame()` sait lire à partir de `main_time` |
| `main_time` | **2400** (40 min) | Deux seuils du code existant, aucun négociable. `isLongGame()` demande `main_time >= 1200` pour le bonus `long_game` des maisons, **et** `speed == "live"` : `OgsService` écarte les parties en correspondance à l'ingestion et le WebSocket ne voit que le live, donc une ligue en correspondance serait invisible de tout le pipeline |
| `periods`, `period_time` | **5** et **30** | 5×30 s. `periods` est obligatoire dès que `time_control` vaut `byoyomi`, et son absence est un 400 |
| `name` | **`Ligue d'Aurak — Saison 2026 - 2027 — Session 08`** | Le seul champ du payload qu'un joueur lise — OGS s'en sert aussi comme nom de la partie —, donc le seul en français. Session sur deux chiffres pour que les noms d'une saison aient tous la même forme. 47 caractères pour un `maxLength` de 255 : au-delà, ce serait un 400 sur **chaque** création d'un tirage, pas un défaut cosmétique |

Une partie de ligue coche donc **tous** les bonus du barème des maisons sauf `rival_house`, qui dépend de l'adversaire
et qui est acquis par construction puisque le tirage interdit les paires intra-maison. Autrement dit une partie de
ligue vaut le maximum du barème des maisons, 11 points sur une victoire. C'est cohérent, et c'est exactement ce que la
décision 8 acte.

✅ **Les deux choses que le payload ne permettait pas de dire sont mesurées**, sur la partie 89632834 créée par la
rencontre 13688 le 11 août 2026. Le payload ne porte aucun champ `ranked` et ne dit rien du `speed` ; la partie qui en
sort porte `"ranked": true` et `"speed": "live"`. Une partie de ligue est donc classée, vue par l'ingestion — `OgsService`
écarte la correspondance, le WebSocket ne voit que le live — et `isLongGame()` la retient (`live` + byoyomi 2400 ≥ 1200).
Rien à changer : les valeurs choisies produisent bien l'effet attendu.

### Une seule ligue, et ce que ça implique

**Il n'y a qu'une ligue OGS, celle de production**, `FulguroGo`. Pas de ligue de dev : c'était le plan initial, il est
abandonné. Ses identifiants vivent dans `ogs.league.id` et `ogs.league.auth`, **avec la même valeur dans les trois
fichiers de config** — dev compris.

C'est une rupture avec tout le reste de l'application, et il faut la nommer : `db.name`, `bot.token`,
`bot.guild.id` et `bot.notification.channel.id` diffèrent entre dev et prod, de sorte qu'un `./gradlew :app:run` ne
peut par construction rien toucher de la production. **La ligue OGS n'a pas cette protection.** Un run local crée de
vraies rencontres sur la vraie ligue et envoie de vrais liens d'invitation.

Ce qui prend le relais est donc `league.test.players`, et il faut le voir comme **le seul garde-fou** et non comme une
précaution supplémentaire :

- **Bac à sable.** Seuls les Discord id listés dans `league.test.players` peuvent entrer dans la ligue. En dev :
  `236813095207436289` (Drooxi) et `453473841252007937` (Judas). **Vide en prod**, ce qui veut dire « aucune
  restriction ». La clé est lue par `Config.getOrNull` et **journalisée au démarrage quand elle est renseignée** — même
  contrat que `house.period.override`, la seule autre clé optionnelle du projet.

  Les deux risques miroirs se valent et cette ligne de log est ce qui les rend visibles : l'oublier renseignée en prod
  limiterait silencieusement la ligue à deux joueurs, l'oublier vide en dev laisserait un run local apparier toute la
  communauté sur la vraie ligue.

- **Les écritures de la ligue restent dans `fg_dev`.** C'est ce qui borne les dégâts : un tirage local écrit ses matchs
  dans le schéma de dev, donc le classement de prod ne bouge pas. Ce qui fuit vers la production, c'est le côté OGS —
  des rencontres et des liens dans la vraie ligue, pour les deux comptes de test.

- **Il n'y a rien à enregistrer chez OGS**, et c'est une conséquence directe de la ligue unique. Le
  `callback_url_template` est un réglage **global à la ligue** : un enregistrement lancé depuis un poste de dev
  repointerait le callback de la production vers un `localhost` injoignable. Ce danger a pesé dans l'abandon du
  callback — il ne restait plus un chemin secondaire à protéger, mais une étape de déploiement fragile au service d'un
  gain de latence nul. Le balayage de l'étape 8 ne dépend d'aucun réglage stocké chez OGS, donc dev et prod y sont
  isolés par construction : chacun ne demande que les rencontres de son propre préfixe `db.name`.

⚠ **Les identifiants ne vont pas dans ce fichier.** `doc/plan-ligue.md` est suivi par git ; `ogs.league.auth` est une
clé d'API et va dans `config.properties`, qui est gitignoré précisément pour ça — comme `bot.token`, `db.password` et
`kgs.login.password`. Le plan ne nomme que les clés.

### Ce qui a été demandé à OGS, et ce qui reste

La clé est en main, la ligue existe, et la sonde du 10 août a répondu à tout le reste — voir
`doc/ogs-online-league-api.md` pour le détail. L'état des six questions que ce plan avait préparées :

| Question | Réponse |
|---|---|
| Deux ligues, dev et prod | ✅ tranché autrement : **une seule ligue**, `FulguroGo`, partagée |
| La configuration des parties | ✅ sans objet : les réglages partent dans le payload de chaque rencontre |
| `league_match_id` est-il unique de votre côté ? | ✅ **oui**, et le `POST` est idempotent — 201 puis 200. ⚠ mais la comparaison porte sur **tout le payload** : un champ envoyé qui diffère est un 400 nommant le champ |
| `GET /matches` filtre-t-il sur `league_match_id` ? | ✅ **oui**, honoré, et **tout champ du modèle est un filtre**, `__startswith` compris. Un champ inconnu est un 400, pas un filtre ignoré. C'est ce qui a permis d'écarter le callback |
| Peut-on annuler une rencontre ? | ⚠ pas sur `/matches/{id}`, qui est en lecture seule (405). Un `PUT /matches/` existe sur la collection et pourrait le faire, non essayé |
| Le nom d'une ligue est-il modifiable ? | ⚠ sans objet : la ligue est unique et sans année |

Rien ne bloque donc plus l'écriture du code. Des trois constats qui attendaient une partie réellement jouée, deux sont
faits le 11 août : `game` porte bien l'`id` de la partie OGS — un entier, `89632834` pour la rencontre `13688`, ce qui
fonde `gold_id = "OGS_<game>"` — et la partie est `ranked` et `live`. Il ne reste que le **type réel des champs de
résultat** sur une rencontre *terminée*.

---

## Le calendrier des sessions

16 sessions, deux par mois — du 1<sup>er</sup> au 14, puis du 15 à la fin du mois, que le mois fasse 28, 30 ou
31 jours — moins deux exceptions : la 1<sup>re</sup> quinzaine de septembre (« formation de l'académie ») et la
2<sup>e</sup> quinzaine de décembre (fêtes).

| Mois | Sessions |
|---|---|
| Septembre | 1 : 15 → 30 |
| Octobre | 2 : 1 → 14, 3 : 15 → 31 |
| Novembre | 4 : 1 → 14, 5 : 15 → 30 |
| Décembre | 6 : 1 → 14 |
| Janvier | 7, 8 |
| Février | 9, 10 |
| Mars | 11, 12 |
| Avril | 13, 14 |
| Mai | 15 : 1 → 14, 16 : 15 → 31 |

9 mois × 2 − 2 = **16**, ce qui recoupe exactement le chiffre du brief et la saison que `HouseSeason` applique depuis
le commit `b24f79a` (1<sup>er</sup> septembre → 31 mai, trêve en juin-juillet-août). « Fin juin » dans le brief est un
lapsus : la même règle jusqu'à fin juin donnerait 18 sessions. Le plan réutilise `HouseSeason` tel quel.

---

## Le barème de renommée

| Type | Points | Condition |
|---|---|---|
| Match joué | 2 | la partie était lancée avant la fin de la session et terminée au règlement |
| Victoire | 5 | le résultat désigne ce joueur |
| Sans faute | 10 | les 16 sessions sont **jouées ou exemptées** |

Un joueur qui joue et gagne ses 16 matchs plafonne à 16 × 7 + 10 = **122 points**.

Un match non joué ne rapporte rien, à personne, et **ne peut plus rapporter** : ni les 2 points, ni une exemption, donc
le bonus « sans faute » tombe avec lui. Le challenge OGS reste éventuellement cliquable — c'est chez OGS, pas chez
nous — mais la partie qui en sortirait ne compte plus pour la ligue. C'est l'étape 8 qui rend cet état terminal, et
c'est le seul endroit du module où une écriture ferme une porte.

**La date limite est double, et les deux moitiés ne tombent pas au même moment.** La partie doit être *lancée* avant
minuit le dernier jour de la session, et *terminée* au moment du **règlement** — la première fenêtre 7h-9h qui suit la
fin de la session. Les sept heures qui séparent les deux ne sont pas une tolérance de gentillesse : c'est la marge dont
l'ingestion a besoin pour qu'une partie finie à 23h50 le dernier jour compte bel et bien.

### Le bonus « sans faute », et les exemptions

Le bonus se lit **session par session**, pas match par match. Sur les 16 sessions de la saison, chacune doit être dans
l'un de ces deux états pour le joueur :

- **jouée** — il avait un match et il l'a joué ;
- **exemptée** — il était candidat au tirage et le tirage ne lui a pas trouvé d'adversaire, parce que l'effectif actif
  était impair ou que tous les autres actifs étaient de sa maison. Ce n'est pas de son fait, donc ça ne le pénalise pas.
  Il ne gagne pour autant aucun point de match : une exemption neutralise, elle ne crédite pas.

Tout le reste casse le bonus : un match tiré et non joué, évidemment, mais aussi une session où le joueur n'était pas
membre actif — parce qu'il s'est inscrit en janvier, ou parce qu'il avait quitté son académie. Ces deux cas-là sont de
son fait ou de son choix.

**Conséquence de modèle, et c'est le vrai coût de cette règle** : une exemption ne laisse aucune trace naturelle. Un
joueur non apparié ne produit aucune ligne de `league_matches`, et l'état d'appartenance passé n'est pas reconstituable
— `league_members` ne garde que `joined`, `active` et un `left_since`, ce qui ne dit pas si un joueur qui est parti et
revenu deux fois était actif le 15 janvier. Le tirage doit donc **écrire les exemptions au moment où il les constate**,
d'où la table `league_exemptions` de l'étape 0.

### Les résultats qui ne désignent pas de vainqueur

**Le jigo est écarté par les réglages, pas par le barème.** Chaque rencontre part avec `rules: japanese` — komi 6,5,
mesuré —
et `handicap: 0`, ce qui rend le score nul impossible ; c'est la même raison pour laquelle le bonus « partie à égalité »
des maisons a dû être lu comme « sans handicap » et non comme « score nul ». Le code traite quand même le cas, au
minimum et sans en faire une règle affichée : une partie terminée dont le résultat ne désigne ni noir ni blanc compte
comme **jouée, sans victoire** — 2 points aux deux, la session compte pour le bonus. C'est le comportement le moins
surprenant si ces réglages changeaient un jour, et il tient en une branche.

**Les parties annulées, elles, sont traitées proprement — et c'est la sonde qui l'a rendu possible.** Une rencontre
porte `annulled`, `moderator_annulled` et `annulment_reason`, renseignés par OGS : la ligue sait donc qu'une partie a
été annulée, et pourquoi, sans dépendre de l'ingestion. Une rencontre annulée n'est pas une victoire.

⚠ Le reste de l'application, en revanche, ne sait pas faire, et la ligue ne le corrige pas — les parties de ligue
alimentant `house_points` et `fgc_validity` (décision 8), l'écart se verra là :

- `OgsService` (le poll REST) écarte les parties annulées à l'ingestion — `if (it.annulled) return@mapNotNull null`.
- `OgsRealTimeService` (le WebSocket) ne connaît pas la notion : rien dans `OgsWsGameData` ne la porte.
- Et **aucun des deux ne défait une partie annulée après coup**, ce qui est le cas le plus fréquent, puisqu'une
  annulation arrive presque toujours après la fin de la partie.

Donc une partie de ligue annulée pourra ne rien rapporter en renommée tout en ayant crédité des points de maison. C'est
rare, ça se corrige à la main dans `house_points`, et le prétendre réglé serait pire que l'écrire ici.

**Pas de table de points.** La renommée est entièrement **dérivable** de la table des matchs : un match porte ses deux
joueurs, leur maison figée au tirage, et son résultat. L'idempotence est déjà assurée par la clé primaire du match, et
un registre séparé serait une deuxième source de vérité à tenir d'accord avec la première. Le total d'une académie est
la somme de ses matchs avec la maison figée au tirage — c'est ce qui fait que la renommée d'une académie ne rétrécit
pas quand un joueur la quitte, exactement comme `house_points`.

---

## Étape 0 — Schéma BDD

Aucun code. Produit le SQL à appliquer à la main sur le serveur, comme les migrations précédentes. Et rien d'autre :
il n'y a **aucun appel à passer à OGS** au déploiement, le callback ayant été écarté.

**Fichier** : `doc/migration ligue.sql`

Trois conventions valent pour les six blocs qui suivent, et elles sont celles de `doc/migration maisons.sql` :

- **`CREATE TABLE IF NOT EXISTS`**, et non `DROP TABLE IF EXISTS` + `CREATE`. Les deux sont rejouables, mais seul le
  second efface une saison de matchs et toutes les académies quand il est rejoué en cours de saison. La contrepartie est
  qu'un changement de colonne ultérieur ne sera pas repris : il demandera son propre `ALTER`, dans son propre fichier.
- **`INT(11)`**, pour se lire comme les treize autres tables. Type identique — la largeur d'affichage ne veut plus rien
  dire depuis que MySQL 8.0 l'a dépréciée.
- **`ROW_FORMAT = DYNAMIC` explicite sur les quatre tables dont la PK contient un `VARCHAR(255)`**, soit 1 020 octets en
  utf8mb4, donc au-delà de la limite d'index de 767 octets de `COMPACT`. C'est de la ceinture-bretelles : le serveur a
  `innodb_default_row_format = dynamic` et `innodb_large_prefix = 1`, et `house_members` tient une PK de 1 020 octets en
  production sans rien déclarer. Si l'une d'elles échoue quand même en erreur 1071, le correctif est un `discord_id`
  plus court, pas un autre `ROW_FORMAT` — un snowflake Discord fait 20 caractères.

### Tables

```sql
CREATE TABLE IF NOT EXISTS `league_seasons` (
  `season` VARCHAR(9) NOT NULL,
  `opened` DATETIME NULL,
  `closed` DATETIME NULL,
  PRIMARY KEY (`season`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

Même rôle que `house_seasons` : le garde-fou de ce qui doit arriver une fois par saison — l'ouverture, et le récap de
clôture. Aucune colonne pour la ligue OGS : elle est permanente et vit dans la config.

```sql
CREATE TABLE IF NOT EXISTS `league_sessions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `drawn` DATETIME NULL,
  `notified` DATETIME NULL,
  `settled` DATETIME NULL,
  PRIMARY KEY (`season`, `session`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
```

Trois garde-fous, un par événement de la vie d'une session. `drawn` : un service qui tick toutes les dix minutes voit
le début d'une session environ 1 400 fois, et c'est cette colonne qui dit qu'elle a déjà été tirée, pas le calendrier.
`notified` sépare l'annonce du tirage, pour qu'un échec Discord ne coûte pas un deuxième tirage ni ne perde l'annonce.
`settled` marque le règlement de la session : les matchs non terminés y deviennent définitivement nuls, et ce n'est pas
une opération à repasser.

Une ligne est créée par le tirage, donc une session non tirée n'a pas de ligne du tout — ce qui est le bon état pour les
sessions à venir, et une différence lisible avec une session tirée sans match.

```sql
CREATE TABLE IF NOT EXISTS `league_players` (
  `discord_id` VARCHAR(255) NOT NULL,
  `ogs_registered` DATETIME NULL,
  PRIMARY KEY (`discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;
```

Le côté OGS, **permanent et sans saison** : une inscription à la ligue OGS ne s'annule pas, et le brief demande
explicitement qu'un joueur y reste après avoir quitté son académie. Deux tables plutôt qu'une parce que les deux
appartenances n'ont pas la même durée de vie, et qu'un `active` par saison mélangé à un état à vie est exactement le
genre de colonne qu'on finit par purger par erreur.

Deux colonnes, et pas trois. **Il n'y a pas de colonne `ogs_member_id`** : le `member_id` est calculé, pas stocké —
voir juste en dessous. Et pas de colonne de rating, parce que le rating poussé est une constante identique pour tout le
monde (étape 5) : une colonne qui porterait la même valeur sur toutes les lignes ne dit rien que `ogs_registered` ne
dise déjà.

### Le `member_id` : un hash salé du `discord_id`

C'est nous qui choisissons le `member_id`, il est dans l'URL du `PUT`, et il est à vie. Il est donc **dérivé** plutôt
que tiré au sort :

```
memberId = sha256(discordId + salt).hex().take(32)
```

Déterministe, donc rien à stocker, rien à relire avant un appel, et aucune colonne à désynchroniser. 128 bits, donc pas
de collision à envisager.

**Le sel n'est pas décoratif, mais son motif est plus étroit que prévu.** ⚠ Correction apportée par la sonde : l'API
des rencontres répond **403 sans les en-têtes d'auth**, donc le `member_id` ne fuit **pas** par là — un tiers ne peut ni
lister les rencontres ni y lire les `member_id`. Restent les pages web d'OGS, qui peuvent afficher ce que l'API protège,
et c'est le seul risque que le sel couvre encore.

Ce risque suffit à le garder, parce que la menace est concrète et bon marché à réaliser : ce n'est pas d'énumérer les
2⁶³ snowflakes Discord, c'est que n'importe qui présent sur le serveur FulguroGo récupère la liste des quelques
centaines d'ids de membres et hashe le lot en une seconde pour faire la correspondance. Un sel coûte une clé de config
et ferme ça. Il vit dans `league.member.salt`.

⚠ **Et il porte un risque d'exploitation qu'il faut connaître** : le sel devient une donnée dont dépend l'identité de
chaque joueur chez OGS. Le changer, ou le perdre, réinscrit tout le monde sous de nouveaux `member_id` et détache les
joueurs de leur historique de ligue OGS. Or il vit dans `config.properties`, qui est **gitignoré** — donc hors des
sauvegardes de la base, contrairement à ce qu'aurait été une colonne d'uuid. À traiter comme `bot.token` : il ne change
jamais, et il est sauvegardé avec les autres secrets du serveur.

```sql
CREATE TABLE IF NOT EXISTS `league_members` (
  `season` VARCHAR(9) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `joined` DATETIME NOT NULL,
  `active` TINYINT(1) NOT NULL DEFAULT 1,
  `left_since` DATETIME NULL,
  PRIMARY KEY (`season`, `discord_id`),
  KEY `league_members_active` (`season`, `active`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;
```

L'académie, par saison. La PK porte la saison, ce qui **vide les académies gratuitement** : le 1<sup>er</sup> septembre
la nouvelle saison n'a simplement aucune ligne, et l'historique de la précédente reste lisible. Aucune purge à écrire,
donc aucune purge à rater.

`active` plutôt qu'une suppression : un joueur qui quitte garde ses matchs et ses points visibles, et peut revenir.

La maison n'est **pas** ici : `house_members` en est la source, et la dupliquer créerait une deuxième vérité. Elle est
figée au tirage, où elle compte vraiment, et pas à l'inscription.

```sql
CREATE TABLE IF NOT EXISTS `league_matches` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `black_discord_id` VARCHAR(255) NOT NULL,
  `white_discord_id` VARCHAR(255) NOT NULL,
  `black_house_id` INT(11) NOT NULL,
  `white_house_id` INT(11) NOT NULL,
  `pairing_score` DOUBLE NOT NULL,
  `league_match_id` VARCHAR(64) NOT NULL,
  `ogs_match_id` INT(11) NULL,
  `black_invite` VARCHAR(255) NULL,
  `white_invite` VARCHAR(255) NULL,
  `spectator_link` VARCHAR(255) NULL,
  `black_notified` DATETIME NULL,
  `white_notified` DATETIME NULL,
  `ogs_game_id` INT(11) NULL,
  `gold_id` VARCHAR(255) NULL,
  `result` VARCHAR(255) NULL,
  `created` DATETIME NOT NULL,
  `finished` DATETIME NULL,
  PRIMARY KEY (`season`, `session`, `black_discord_id`),
  UNIQUE KEY `league_matches_white` (`season`, `session`, `white_discord_id`),
  UNIQUE KEY `league_matches_league_id` (`league_match_id`),
  KEY `league_matches_ogs_match` (`ogs_match_id`),
  KEY `league_matches_gold_id` (`gold_id`),
  KEY `league_matches_season_black` (`season`, `black_discord_id`),
  KEY `league_matches_season_white` (`season`, `white_discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;
```

Pas d'`AUTO_INCREMENT`, comme partout ailleurs dans ce schéma. La PK et la clé unique sur le blanc disent la vraie
règle du domaine : **un match au plus par joueur et par session**, quel que soit son côté. C'est ce qui rend un tirage
relancé deux fois inoffensif — la deuxième insertion tombe sur la clé, pas sur une ligne en double.

`league_match_id` est ce qu'on pousse chez OGS, dérivé de la PK et donc déterministe. C'est la clé de l'idempotence
d'OGS : deux tentatives de création de la même rencontre portent le même identifiant, et la seconde renvoie la première
au lieu d'en créer une autre.

Il est **préfixé du nom de la base** — `"<db.name>_<season>_<session>_<blackDiscordId>"`, soit
`fg_dev_2026-2027_8_236813095207436289`, 38 caractères. Ce préfixe est la conséquence directe de n'avoir qu'une seule
ligue OGS : sans lui, un tirage de dev et un tirage de prod qui apparient les mêmes deux joueurs sur la même session
avec le même joueur en noir enverraient **le même `league_match_id` à la même ligue**. Or les comptes de test sont de
vrais membres de la communauté, donc ils seront aussi tirés en production — la collision n'est pas théorique, elle est
attendue. `db.name` est le discriminant qui existe déjà, il est garanti présent et il diffère par construction entre
les deux environnements.

⚠ Correction apportée par la mesure du 10 août au soir : l'idempotence d'OGS porte sur **tout le payload**, pas sur le
seul `league_match_id`. Un rejeu dont un champ diffère répond 400 en nommant le champ, ce qui rend la plupart des
collisions bruyantes plutôt que silencieuses. Le préfixe reste néanmoins nécessaire, parce que le cas silencieux
subsiste exactement là où il fait mal : deux payloads **identiques** — mêmes joueurs, même session, mêmes couleurs —
partageraient une seule rencontre entre dev et prod. Et ce même mécanisme gèle les réglages d'une rencontre pour sa
durée de vie : changer une constante de `OgsLeagueClient` en cours de saison ferait échouer tout rejeu sur les
rencontres déjà créées. Détail dans `doc/ogs-online-league-api.md`.

`black_house_id` / `white_house_id` sont figées à l'écriture, pour la même raison que dans `house_points` : le total
d'une académie ne doit pas bouger quand un joueur change de maison ou la quitte.

`ogs_match_id` est un **entier** — l'`id` d'une rencontre OGS en est un. L'index est conservé bien que plus rien ne
cherche un match dessus depuis l'abandon du callback : le balayage filtre sur le préfixe de `league_match_id` et
rapproche ses résultats par cette colonne, que la clé unique sert déjà. Il ne coûte rien sur une table de cette taille,
et c'est l'index qu'une recherche par id voudrait. `ogs_match_id` reste ce qu'un humain cite en lisant OGS.

Les trois liens **ne sont jamais effacés**, contrairement à ce que suggérait le brief (« sauvegardés le temps de la
session ») : ils sont la seule façon de renvoyer son lien à un joueur dont le MP a échoué, et ce renvoi se fait à la
main, éventuellement des jours plus tard. `black_notified` / `white_notified` datent le MP parti, ce qui est ce qui
permet de répondre à « qui n'a pas reçu son lien ? » sans relire les logs.

`gold_id` (`OGS_<ogs_game_id>`) est le pont vers `ogs_games`, la clé que le reste de l'application connaît déjà. Pas de
clé étrangère, exprès : `CleanService` supprime les parties au bout de 32 jours et un match de novembre doit rester
lisible en mai — d'où `result` recopié dans la ligne plutôt que lu par jointure.

`result` a **trois** familles de valeurs, et c'est le cœur de la règle « non joué, non rejouable » : `NULL` tant que le
sort du match n'est pas fixé, le vainqueur qu'OGS désigne (`black`, `white`, …) quand il a été joué, et `'unplayed'` dès que
le règlement de la session est passé sans qu'un résultat soit arrivé. `'unplayed'` est terminal — plus aucune écriture
ne le regarde — ce qui est exactement ce qui rend une partie jouée après coup sur OGS sans effet sur la ligue.

Le règlement ne laisse **aucun** match à `NULL`, et c'est ce qui ferme le mode de panne le plus vicieux de ce module :
un match éternellement en suspens n'est ni joué ni exempté, donc il coûte silencieusement le bonus « sans faute » à ses
deux joueurs, et cela ne se découvre qu'en fin de saison.

`ROW_FORMAT = DYNAMIC` explicite, comme `house_points` : la PK fait 1 060 octets en utf8mb4 — 36 pour la saison,
4 pour la session, 1 020 pour l'id — trop large pour `COMPACT`. (Les 2 040 octets d'une version précédente de ce plan
étaient le chiffre de `house_points`, qui a deux `VARCHAR(255)` dans sa PK. La conclusion ne change pas.)

```sql
CREATE TABLE IF NOT EXISTS `league_exemptions` (
  `season` VARCHAR(9) NOT NULL,
  `session` INT(11) NOT NULL,
  `discord_id` VARCHAR(255) NOT NULL,
  `reason` VARCHAR(32) NOT NULL,
  `created` DATETIME NOT NULL,
  PRIMARY KEY (`season`, `session`, `discord_id`),
  KEY `league_exemptions_player` (`season`, `discord_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 ROW_FORMAT = DYNAMIC;
```

Les joueurs qu'un tirage a laissés sans adversaire. C'est la seule chose qui rende le bonus « sans faute » calculable :
sans cette table, une session sans match pour un joueur est indistinguable d'une session où il n'était pas là, et le
bonus deviendrait faux dans le sens qui se voit le moins — un joueur assidu qui le perd sans comprendre pourquoi.

Écrite par le tirage, dans la même transaction logique que les matchs, et **jamais** par le règlement de session : une
exemption est une décision du tirage, pas une conséquence d'un match non joué.

`reason` vaut `'ODD'` (effectif impair, il restait un joueur) ou `'NO_RIVAL'` (tous les autres actifs étaient de sa
maison). Aucun code ne lit cette colonne : elle existe pour la question « pourquoi n'ai-je pas été tiré ? », qui sera
posée, et à laquelle un `SELECT` doit pouvoir répondre sans relire les logs de janvier.

La PK `(season, session, discord_id)` fait l'idempotence, comme partout ailleurs : un tirage rejoué n'ajoute rien.

### Vue

Aucune. Le classement dépend de la saison courante, calculée en Kotlin — même raison qu'aux maisons : une vue ne peut
pas recevoir la saison. Les agrégats sont des requêtes dans `LeagueDatabaseAccessor`, et il n'y a donc rien à modifier
en prod sur les cinq vues existantes.

### Rien à enregistrer chez OGS

Le fichier ne porte **aucun appel sortant**. Une version précédente y mettait un `PUT /online_league/callback` à passer
une fois depuis la production ; le callback est écarté, et l'étape 8 dit pourquoi. Ce qui disparaît du déploiement :
une étape manuelle réservée à la prod dont l'oubli était silencieux, et le risque miroir qu'un poste de dev repointe le
template de la ligue partagée vers un `localhost` injoignable.

Un seul contrôle facultatif, en lecture et sans effet : `GET /online_league/callback` avec les deux en-têtes doit
répondre `{"callback_url_template": null}`. Au 10 août 2026 c'était le cas. S'il répond autre chose, quelqu'un en a
enregistré un, et une URL périmée mérite d'être effacée.

**Vérification** : appliquer sur `fg_dev`, puis `SHOW TABLES LIKE 'league_%'` renvoie 6 lignes, et
`information_schema.TABLES` les donne toutes en `Dynamic` avec 0 vue ajoutée. Puis, parce que c'est le seul endroit où
ce fichier dévie du plan, **rejouer le fichier entier avec des lignes en base** : une saison et un membre témoins
doivent survivre au second passage. Les supprimer ensuite.

---

## Étape 1 — Squelette du module

Objectif : le module existe, compile, est branché, ne fait rien. Aucun risque en prod.

- `settings.gradle.kts` : `include(":modules:league")` dans le bloc des modules communautaires, après `house`
- `modules/league/build.gradle.kts` : `plugins { id("fulgurogo-module") }`, plus
  `implementation(project(":modules:discord"))`, `implementation(project(":modules:house"))`,
  `implementation(project(":modules:gold"))` et `implementation(project(":modules:ogs"))` — la ligue lit les maisons et
  les ratings, et parle à OGS
- `app/build.gradle.kts` : `implementation(project(":modules:league"))`
- `modules/league/.../league/LeagueModule.kt` : `object` avec `const val TAG = "LGE"` et un `init()` vide
- `App.kt` : `LeagueModule.init()` après `HouseModule.init()` et **avant** `ApiModule.init(isDebug)` — `api` dépendra
  de `league` comme il dépend déjà de `house`

Nommage : module `league`, package `com.fulgurogo.league`, TAG `LGE`, tables `league_*`. Exactement le pattern
`house` / `HSE` — l'anglais dans le code, et le français réservé à ce que lisent les joueurs : les messages Discord et
les libellés que l'API sert au site. « Ligue d'Aurak » et « Académie » n'apparaissent donc nulle part dans un nom de
classe, de table ou de colonne.

**Vérification** : `./gradlew build`, puis `./gradlew :app:run` démarre comme avant.

---

## Étape 2 — Le calendrier des sessions

Dépend de : 1. Aucune BDD, du calcul de dates pur — l'endroit le plus facile à se tromper, et le plus facile à relire.

`modules/league/.../league/LeagueSession.kt` :

- `data class Session(val number: Int, val start: ZonedDateTime, val end: ZonedDateTime)`, `end` exclusive
- `sessions(season: String): List<Session>` — les 16 sessions, dans l'ordre, numérotées 1 à 16. Construites en
  parcourant les mois de `HouseSeason.seasonWindow(season)` et en écartant les deux exceptions
- `current(season, now): Session?` — la session contenant `now`, nulle hors saison et pendant les deux trous
- `ended(season, now): List<Session>` — les sessions dont l'`end` est passé, pour le règlement de l'étape 7
- `count(season): Int` — 16, calculé et non écrit en dur, pour que la constante du bonus « sans faute » et le découpage
  ne puissent pas diverger

Tout passe par `ZonedDateTime.now(DATE_ZONE)` et les helpers de `ZonedDateTimeExtensions.kt`, jamais `now()` nu, et le
calendrier prend l'instant en paramètre comme le fait `HouseSeason` — c'est ce qui rend les bascules vérifiables à la
main.

Le piège de l'étape est la 2<sup>e</sup> quinzaine de décembre. Son absence ne crée **pas** de session plus longue : la
session 6 s'arrête bien le 15 décembre 00:00, et il n'y a simplement pas de session jusqu'au 1<sup>er</sup> janvier. Un
`end` étendu au 1<sup>er</sup> janvier ferait compter les parties des fêtes dans la session 6 — et, depuis la règle du
match non joué, ferait aussi vivre ces matchs quinze jours de plus que les autres.

**Vérification** : un log au démarrage listant les 16 sessions avec leurs bornes, contrôlé au moins pour le
14 septembre (aucune), le 15 septembre (session 1), le 14 décembre (session 6), le 20 décembre (aucune), le
1<sup>er</sup> janvier (session 7), le 31 mai (session 16) et le 1<sup>er</sup> juin (aucune).

---

## Étape 3 — Modèles et accès BDD

Dépend de : 0, 1, 2.

`db/model/` — tous en `data class` annotées `@GenerateNoArgConstructor` :

- `LeagueSeasonState` — `season`, `opened`, `closed`
- `LeagueSessionState` — `season`, `session`, `drawn`, `notified`, `settled`
- `LeaguePlayer` — `discordId`, `ogsRegistered`. Le `member_id` n'en est **pas** un champ : il se calcule, et une seule
  fonction le fait — `LeagueMemberId.of(discordId)`. Deux implémentations du même hash seraient deux identités
  différentes pour le même joueur, et la deuxième ne se remarquerait qu'à la création d'une rencontre
- `LeagueMember` — `season`, `discordId`, `joined`, `active`, `leftSince`
- `LeagueMatch` — les colonnes de `league_matches`, plus `isPlayed()`, `isUnplayed()` et `winner()`
- `LeagueExemption` — `season`, `session`, `discordId`, `reason`, plus `enum class ExemptionReason { ODD, NO_RIVAL }`
- `LeagueCandidate` — un membre actif prêt à être tiré : `discordId`, `houseId`, `rating`
- `LeagueStanding` — une ligne du classement : identité Discord, maison, `played`, `won`, `lost`, `exempted`, `renown`,
  `rank`, `active`
- `LeagueRenown` — le détail (`playedPoints`, `victoryPoints`, `perfectBonus`, `total()`), pour que le site ne
  ré-additionne rien

`db/LeagueDatabaseAccessor.kt`, un `object`, noms de tables en `private const val`, **toutes** les requêtes via
`connection.query(...)` et jamais `createQuery` — sinon la dérivation `snake_case` → `camelCase` est perdue et une
colonne se mappe silencieusement sur null.

Lectures :
- `seasonState(season)`, `sessionState(season, session)`
- `player(discordId)`, `playersToRegister()` — les lignes dont `ogs_registered IS NULL`
- `member(season, discordId)`, `members(season)`
- `candidates(season)` — les membres actifs joints à `house_members`, `gold_ratings`, `ogs_user_info` et
  `league_players` : cette jointure applique les quatre conditions d'éligibilité en un seul endroit — une maison, un
  compte OGS, un `member_id` enregistré chez OGS, et un rating **exploitable**, c'est-à-dire
  `gold.rating > 0 AND gold.error = 0`
- `matches(season)`, `matches(season, session)`, `matchesOf(season, discordId)`
- `matchByLeagueId(leagueMatchId)` — comment une rencontre rendue par le balayage retrouve sa ligne. `matchByOgsId`
  existe aussi, mais plus aucun chemin ne s'en sert depuis l'abandon du callback : le balayage charge la session entière
  par `matches(season, session)` et rapproche en Kotlin, une requête au lieu de N
- `pendingMatches(season, session)` — les matchs sans `result`, pour le rattrapage et la clôture
- `unnotifiedMatches(season, session)` — ceux dont un MP n'est pas parti
- `pastOpponents(season)` → `Map<Pair<String, String>, Int>`, les rencontres déjà tirées par paire, pour la pénalité
- `exemptions(season)` → `Map<String, Int>`, le nombre de sessions exemptées par joueur, pour le bonus
- `standings(season)`

Écritures :
- `openSeason(season)` / `closeSeason(season)` — `INSERT IGNORE` puis `UPDATE … WHERE opened IS NULL`, exactement comme
  `HouseDatabaseAccessor.openSeason` : le prédicat est dans le `WHERE` et le `SET` le rend faux, donc « ligne matchée »
  et « ligne changée » coïncident et le booléen de retour est un vrai « c'est moi qui l'ai fait » malgré
  `useAffectedRows` laissé par défaut
- `claimDraw(season, session)` / `claimNotification(...)` / `claimSettlement(...)` — même idiome
- `claimRedraw(season, session, startOfDay)` — le rattrapage d'un tirage vide, au plus une fois par jour :
  `UPDATE … SET drawn = NOW() WHERE … AND drawn < :startOfDay`, exactement `claimDailyRanking` des maisons
- `addPlayer(discordId)`, `markRegistered(discordId)`
- `addMember(season, discordId)` (`INSERT IGNORE`, retourne s'il a créé la ligne), `setActive(...)`
- `addMatches(matches)` — `INSERT IGNORE`, retourne le nombre de nouvelles lignes, comme `addPoints`
- `addExemptions(exemptions)` — `INSERT IGNORE`, même idiome
- `setMatchChallenge(...)`, `markNotified(...)`, `setMatchGame(...)`, `finishMatch(...)`
- `markUnplayed(season, session)` — `UPDATE … SET result = 'unplayed', finished = NOW() WHERE result IS NULL`, la seule
  écriture destructive du module (étape 8). Pas de condition sur `ogs_game_id` : au moment où elle tourne, une partie
  lancée à temps a eu ses sept heures pour se terminer et se faire ingérer

**Vérification** : `./gradlew build`. Aucun comportement observable.

---

## Étape 4 — Inscription et sortie

Dépend de : 3. Ajouter `implementation(project(":modules:league"))` à `modules/api/build.gradle.kts`.

`POST /gold/api/league/join` — corps `{ "discordId": "..." }`.

- `400` corps invalide
- `403` on est hors saison (`HouseSeason.period() == VACATION`), ou le bac à sable de dev est actif et ce joueur n'y
  est pas
- `404` joueur inconnu, sans maison, ou sans compte OGS lié
- `409` déjà membre **actif**
- `200` avec l'état de l'inscription

L'inscription fait deux choses : la ligne d'académie de la saison, et — si le joueur n'en a pas encore — sa ligne
`league_players`, avec `ogs_registered` nul. L'appel `PUT member/{id}` chez OGS n'est **pas** fait dans le handler : il est
laissé au tick (étape 7), parce qu'une inscription ne doit pas échouer parce qu'OGS est momentanément indisponible, et
parce que le handler serait sinon le deuxième endroit du projet où un appel réseau sortant bloque une requête HTTP
entrante.

Un joueur inscrit puis parti puis revenu retombe sur sa ligne existante, `active` repassant à 1 : l'inscription est
donc un `INSERT IGNORE` suivi d'un `setActive(true)`, pas une insertion sèche. Son `joined` n'est pas restampé, et sa
renommée déjà acquise reste dans ses matchs.

Le **bac à sable de dev** est refusé ici plutôt que filtré en silence : un compte de test non listé doit recevoir un
403 explicite, sinon on passe une soirée à chercher pourquoi il n'apparaît dans aucun tirage. Le filtre est répété
dans `candidates()` — deux endroits, mais l'un est une réponse à un humain et l'autre une garantie sur le tirage, et
c'est la même liste lue au même endroit.

`POST /gold/api/league/leave` — corps `{ "discordId": "..." }`. Passe `active` à 0 et stampe `left_since`.

- `400` corps invalide
- `404` pas membre
- `204` enregistré

Contrairement aux maisons, quitter est possible **pendant** la saison : c'est le brief. Rien n'est retiré côté OGS —
un `DELETE /member/{id}` existe pourtant, mais le brief veut qu'un joueur reste dans la ligue OGS. Un joueur qui quitte
pendant une session garde le match déjà tiré :
il est libre de le jouer, et s'il ne le joue pas la règle du match non joué s'applique comme pour tout le monde.

**Aucune vérification d'identité**, comme `POST /gold/api/link` et `POST /gold/api/house/join` : le `discordId` du corps
est pris tel quel. C'est la convention de cette API, et la ligue ne l'infléchit pas — même si l'auth Discord existe dans
le projet (`GET /gold/api/auth/profile` vérifie un vrai token OAuth) et aurait pu être exigée ici.

Ce que ça expose, dit une fois et sans détour : n'importe qui peut poster un `leave` sur le `discordId` d'un autre. La
victime perd le tirage de la session en cours et, avec la décision 1, le bonus « sans faute » de toute la saison.
C'est un cran au-dessus de ce que les maisons exposaient, où un `LEAVE` n'était qu'une intention d'été révocable.

Deux atténuations, qui viennent gratuitement et ne sont pas des décisions supplémentaires : un `join` qui suit restaure
`active = 1` sans toucher `joined`, donc un sabotage repéré **avant le tirage suivant** ne coûte rien ; et la ligne de
log de `leave` nomme le joueur, ce qui permet au moins de reconstituer après coup. Une réparation manuelle en base reste
possible dans les autres cas.

**Les trois sorties automatiques**, sans endpoint, toutes réconciliées **par une seule requête** au tick (étape 7) :

```sql
UPDATE league_members m SET m.active = 0, m.left_since = NOW()
 WHERE m.season = :season AND m.active = 1
   AND (NOT EXISTS (SELECT 1 FROM ogs_user_info o WHERE o.discord_id = m.discord_id)
     OR NOT EXISTS (SELECT 1 FROM house_members h WHERE h.discord_id = m.discord_id))
```

Une réconciliation d'état plutôt qu'un appel à passer depuis chaque chemin de sortie, et c'est le point important :
elle marche **quelle que soit la façon dont la ligne a disparu**, y compris par une future route de déliaison que
personne n'aurait pensé à brancher sur la ligue. Le coût est une latence d'au pire dix minutes, sans conséquence
puisque seul le tirage lit `active`.

1. **Compte OGS supprimé.** Il n'existe aucune route de déliaison dans l'API, et il n'en est pas prévu : `Api.link`
   ajoute, rien ne retire. Le « si un joueur délie son compte OGS » du brief n'a donc pas de parcours côté site.

   Mais le cas se produit quand même, et par un chemin déjà en production : `CleanService.removeDeletedAccounts()`
   tourne à chaque tick et fait `DELETE FROM ogs_user_info WHERE ogs_name LIKE 'deleted-%'` — **sans toucher à quoi que
   ce soit d'autre**. Un joueur qui supprime son compte OGS perd donc son lien tout en gardant sa ligne d'académie.
   C'est le vrai déclencheur de cette réconciliation, et la raison pour laquelle elle n'est pas du code mort.
2. **Sortie de maison.** Un joueur sans ligne `house_members` ne peut plus représenter d'académie. Même mécanique.
3. **Départ du Discord.** `CleanDatabaseAccessor.removeAllFrom` gagne `league_members` **et** `league_players`, et
   **pas** `league_matches` — supprimer les matchs ferait rétrécir la renommée d'une académie, ce qui contredit la règle
   « les points restent acquis ». Même asymétrie que `house_members` / `house_points`, pour la même raison.

   Purger `league_players` ne perd rien de reconstructible, et c'est la décision 10 qui le rend vrai : le
   `member_id` étant un hash du `discord_id`, il redérive à l'identique au retour du joueur, qui retrouve donc son
   identité et son historique de ligue chez OGS. La seule chose perdue est `ogs_registered`, donc un `PUT member`
   redépensé — et OGS décrit lui-même cet appel comme « register/update », donc le rejouer est sans effet de bord.
   Avec un uuid tiré au sort, ce même choix aurait détaché le joueur de son historique OGS ; ici il ne coûte qu'une
   requête. C'est aussi ce qui évite de garder le `discord_id` d'une personne partie du serveur, alors que
   `discord_user_info`, `ogs_user_info`, `gold_ratings` et `house_members` sont tous purgés.

**Vérification** : `curl` sur les deux routes contre `fg_dev` avec Drooxi et Judas ; vérifier qu'un `join` après un
`leave` remet `active` à 1 sans toucher `joined`, qu'une deuxième inscription ne recrée pas la ligne `league_players`,
et qu'un troisième compte reçoit bien un 403.

---

## Étape 5 — Le client OGS

Dépend de : 1. Le contrat est connu et vérifié, la clé est en main — cf. `doc/ogs-online-league-api.md`.

`modules/league/.../league/ogs/OgsLeagueClient.kt` — les trois appels que le serveur fait, et rien d'autre :

```kotlin
fun registerMember(memberId: String): Boolean
fun createMatch(
    blackMemberId: String, whiteMemberId: String, leagueMatchId: String, season: String, sessionNumber: Int
): OgsLeagueMatch?
fun matchStatus(ogsMatchId: Int): OgsLeagueMatch?
```

La saison et la session ne servent qu'à **nommer** la rencontre — `Ligue d'Aurak — Saison 2026 - 2027 — Session 08`, le
seul champ du payload qu'un joueur lise, et donc le seul en français. Elles ne font pas partie de l'identité de l'appel,
qui est `league_match_id` seul.

Trois appels, et **pas de `findMatch`** : la sonde du 10 août a montré que `POST /matches/` est idempotent sur
`league_match_id` — 201 à la création, 200 ensuite, même `id` et mêmes liens. Reprendre une création interrompue se
réduit donc à rejouer l'appel, ce qui supprime la lecture préalable que ce plan prévoyait et divise par deux le trafic
OGS d'un tirage.

`PUT /callback` n'est pas dans le client, et n'est plus nulle part : le callback est écarté. Le client n'a donc que ces
trois méthodes, et `matchStatus` sert au diagnostic d'une rencontre précise — le balayage de l'étape 8 interroge la
collection.

Cinq points d'implémentation :

- **Les en-têtes passent par `OgsApiClient`, qui est étendu.** Il n'envoie aujourd'hui que `User-Agent` et ne sait pas
  faire de `PUT` : on lui ajoute un paramètre d'en-têtes optionnel sur `get`/`post`, et un `put`. Un seul client OGS dans
  le projet, donc un seul endroit où corriger le jour où OGS resserre ses limites ou change ses attentes d'en-têtes — ce
  qui est déjà arrivé, cf. l'épisode `Accept-Language` côté KGS.

  Deux précautions, parce que ce fichier est utilisé en production par `OgsService` et `OgsRealTimeService` :
  **les en-têtes doivent être additifs et par défaut vides**, pour que le comportement des deux services existants soit
  strictement inchangé ; et `ensureSpamDelay()` reste un **état d'instance**. Le rendre statique donnerait une vraie
  garantie de rythme globale, mais changerait le comportement de l'existant — `OgsService` tourne toutes les 15 s et se
  retrouverait à attendre les appels de la ligue. La ligue instancie donc son propre `OgsApiClient`, comme les deux
  autres services le font déjà.

  ⚠ Conséquence à connaître : trois instances signifient trois compteurs, donc rien ne garantit 500 ms entre un appel de
  la ligue et un appel d'`OgsService`. C'est déjà le cas aujourd'hui entre les deux services existants, donc la ligue
  n'introduit pas le problème — elle l'aggrave d'un tiers.

- **Un seul modèle pour les trois appels.** `POST /matches/`, `GET /matches/` et `GET /matches/{id}` renvoient
  exactement le même objet, mesuré au champ près. `OgsLeagueMatch` le mappe une fois, avec les 35 champs, dont les neuf
  `readOnly` du résultat.

- **`ogs_match_id` est un entier.** L'`id` d'une rencontre est un `int`, pas une chaîne — d'où la signature de
  `matchStatus` et le type de la colonne.

- **Un tirage crée N rencontres d'affilée.** Avec 500 ms de délai anti-spam, 20 matchs font une dizaine de secondes
  d'appels dans un tick — acceptable, et l'échec partiel n'a plus besoin d'être traité comme un cas à part : un match
  dont la création a échoué reste en base sans liens, le tick suivant rejoue le `POST`, et l'idempotence fait le reste.
  D'où `ogs_match_id` nullable et `setMatchChallenge` séparé de `addMatches`.

- **Le rating poussé est une constante**, identique pour tout le monde, `LEAGUE_START_RATING = 1500`. Le champ est
  obligatoire dans `PUT member/{id}`, mais on ne lui confie rien : OGS entretient son propre classement de ligue à
  partir des parties, et notre `gold_ratings.rating` sert au **tirage**, de notre côté. La sonde l'a confirmé — le
  rating envoyé reste dans `pending_rating_change` et `league_rating` demeure nul jusqu'à ce qu'un compte OGS soit lié.
  C'est aussi ce qui rend l'inscription définitivement ponctuelle : `PUT member` est idempotent (201 puis 200), donc
  `ogs_registered` n'a qu'à éviter de re-`PUT`er tout le monde à chaque tick.

**Vérification** : les trois appels ont déjà été exercés à la main le 10 août, contre la ligue réelle et avec les deux
comptes de test — Drooxi et JudasImov sont inscrits, la rencontre `probe_idempotence_01` existe. Il reste à vérifier que
le client Kotlin produit les mêmes requêtes : logguer intégralement requête et réponse au premier appel, et comparer au
journal de la sonde.

---

## Étape 6 — Le tirage

Dépend de : 3. Fonction pure, sans BDD, sans horloge — la partie la plus facile à relire et à corriger.

`modules/league/.../league/LeaguePairing.kt` :

```kotlin
data class Draw(val pairings: List<Pairing>, val exemptions: List<Exemption>)

fun draw(candidates: List<LeagueCandidate>, history: Map<Pair<String, String>, Int>): Draw
```

Le tirage renvoie **les deux** : les paires, et les candidats qu'il n'a pas pu apparier avec la raison. C'est lui, et
lui seul, qui sait pourquoi un joueur est resté sur le banc — un effectif impair (`ODD`) ou aucun adversaire hors de sa
maison (`NO_RIVAL`) — et cette information est ce qui rend le bonus « sans faute » juste. La déduire plus tard, en
comparant les candidats aux matchs écrits, redonnerait la bonne liste mais pas la bonne raison, et surtout supposerait
de savoir qui était candidat, ce que rien ne garde.

`ODD` et `NO_RIVAL` ne sont pas exclusifs sur le papier : un joueur seul de sa maison face à un effectif impair relève
des deux. La règle est de retenir `NO_RIVAL`, qui est la cause la plus spécifique et la plus utile à expliquer.

Score d'appariement, à minimiser :

```
score(a, b) = |rating(a) - rating(b)| + REPEAT_PENALTY * rencontres(a, b)
```

Les paires dont les deux joueurs sont de la même maison ne sont **pas** notées : elles n'existent pas dans le graphe,
ce qui rend la contrainte inviolable par construction plutôt que par un poids très élevé qu'un cas limite pourrait
franchir.

`REPEAT_PENALTY = 400`, soit **deux paliers de `gold_tiers`**, qui en font 200 chacun. Les deux termes sont donc dans
la même unité, des points de rating, et le réglage se lit comme une phrase : revoir un adversaire déjà rencontré coûte
autant que d'en affronter un deux tiers plus loin. La nouveauté prime nettement sur l'équilibrage, ce qui est le bon
arbitrage sur 16 sessions — un joueur qui affronte trois fois la même personne dans l'année n'a pas joué une ligue.

### L'algorithme : glouton, puis amélioration locale

1. **Glouton.** Énumérer les paires valides, trier par score croissant, prendre chaque paire dont les deux joueurs sont
   encore libres. O(n² log n), déterministe, dix lignes.
2. **2-opt.** Balayer les couples de paires déjà formées et échanger les adversaires quand le total baisse, en
   revérifiant la contrainte de maison à chaque échange. Répéter jusqu'à ce que plus rien ne bouge.

Le glouton seul ne suffit pas, et pour une raison précise qu'il faut avoir en tête avant de toucher à ce fichier :
**sans la pénalité, le score est une simple distance sur une droite** — les ratings — et le couplage optimal consiste à
apparier les voisins dans l'ordre trié, ce que le glouton fait naturellement. Ce sont la pénalité de répétition et
l'exclusion intra-maison qui cassent cette propriété, la première en ajoutant un terme qui ne respecte aucune inégalité
triangulaire, la seconde en trouant le graphe. Et comme `REPEAT_PENALTY` vaut 400, ce terme pèse lourd : plus la
pénalité est forte, plus le glouton seul peut se tromper.

Le cas typique, avec des chiffres :

```
A-B =  20  (jamais joué)      A-C = 210
C-D = 420  (déjà rencontrés)  B-D = 210

glouton : A-B puis C-D  = 440   -- il prend A-B en premier parce que c'est la meilleure paire seule
2-opt   : A-C  et  B-D  = 420   -- et il n'y a pas de meilleure paire *ensemble*
```

Le 2-opt est retenu plutôt qu'un couplage exact (Blossom) parce qu'il rattrape la quasi-totalité de ces cas pour vingt
lignes, reste déterministe, et se teste par un invariant trivial : **le total ne peut que baisser**. Un couplage exact
donnerait la garantie, mais au prix d'un algorithme long et subtil que personne ne débuguera un 15 janvier au soir,
pour un gain invisible à vingt joueurs. Pas de garantie d'optimalité, donc, et c'est assumé.

### Les cas de bord

**Effectif impair.** Un joueur reste sur le banc et reçoit une exemption `ODD`. Il ne gagne **rien** — pas de bye : le
classement reflète ce qui a été joué, et son bonus « sans faute » est déjà sauvé par la décision 1. Une
exemption neutralise, elle ne crédite pas.

Qui reste sur le banc n'est en revanche **pas** laissé à l'implémentation. Si on prenait simplement « celui que le
glouton n'a pas su caser », ce serait systématiquement le joueur au rating le plus extrême, session après session et
saison après saison, pour une perte sèche de 7 points à chaque fois. La règle est donc explicite : **on écarte parmi
les candidats qui ont subi le moins d'exemptions cette saison, et on départage au sort**. La lecture est gratuite,
`league_exemptions` existe déjà, et le tirage au sort a un précédent dans le projet — `HouseAssignment.draw`.

**Joueur non appariable** : tous les autres actifs sont de sa maison. Exempté de la même façon, raison `NO_RIVAL`, et
pas de bye non plus. Ce cas-là ne se choisit pas : il s'impose.

**Les couleurs sont tirées au sort** à chaque match. Ni le rating, ni une alternance sur la saison : deux lignes, et
équitable en espérance. La contrepartie est visible et acceptée — sur seize sessions, un joueur peut prendre noir dix
fois. Si ça devient un sujet, l'alternance équilibrée se lit dans `league_matches` sans changement de schéma.

**Rating manquant ou en erreur** — `gold_ratings.rating = 0` avant le premier passage de `GoldService`, ou
`error = 1` : le joueur **n'est pas candidat et n'est pas exempté**. La session lui manque donc pour le bonus « sans
faute ». C'est la seule des quatre règles où le joueur paie pour quelque chose qui ne vient pas de lui, et il faut le
savoir en exploitation : `gold_ranks.error` est la **somme** des drapeaux d'erreur par plateforme, donc un scraper KGS
cassé met en erreur tous les joueurs ayant un compte KGS, et pourrait sortir la moitié de la ligue d'un tirage. Le
`rating = 0` du joueur fraîchement lié, lui, ne dure que jusqu'au prochain tick de `GoldService` et n'est pas un
risque. En pratique : surveiller `/gold/api/health` les jours de tirage.

**Vérification** : un `main` jetable avec quatre jeux de candidats posés à la main — effectif pair équilibré, effectif
impair, une maison majoritaire à elle seule, et un historique chargé — en contrôlant qu'aucune paire intra-maison ne
sort, que la pénalité fait bien changer d'adversaire à la deuxième rencontre, et que **la somme des joueurs appariés et
des joueurs exemptés fait exactement le nombre de candidats**. Cette dernière égalité est l'invariant à tester en
priorité : un candidat qui disparaît sans être ni apparié ni exempté est un joueur qui perdra son bonus en fin de
saison, six mois plus tard, sans que rien ne l'ait signalé.

Deux invariants du 2-opt à vérifier au passage, parce qu'ils sont faciles à casser en le relisant : le total après
échange est **strictement inférieur** au total avant, sinon la boucle ne termine pas ; et un échange ne doit jamais
produire une paire intra-maison, ce qui veut dire revérifier la contrainte à chaque échange et pas seulement à la
construction. Un jeu de candidats où deux maisons ont deux joueurs chacune suffit à exercer le second.

---

## Étape 7 — Le service de session

Dépend de : 4, 5, 6. C'est l'étape qui écrit et qui parle à OGS.

`LeagueSessionService : PeriodicFlowService(150, 600)` — dix minutes suffisent pour des événements datés au jour, comme
`HouseSeasonService`. Délai initial de 150 s, derrière les 90 et 120 s des maisons, pour ne pas ouvrir toutes les
connexions d'un coup au démarrage à froid.

`onTick()`, dans cet ordre :

1. **Ouverture de saison.** En `SEASON`, si la saison n'a pas de ligne `opened` : réclamer `opened`. C'est tout — il
   n'y a rien à créer chez OGS, et les académies sont vides par construction (étape 0). Ce bloc n'existe que pour
   donner un point d'ancrage au récap de clôture.
2. **Réconciliation.** Désactiver les membres actifs qui n'ont plus de maison ou plus de compte OGS (étape 4).
   Enregistrer chez OGS (`registerMember`) les joueurs dont `ogs_registered` est nul.
3. **Règlement des sessions passées.** Dans la **fenêtre 7h-9h**, pour chaque session dont l'`end` est franchi et qui
   n'a pas de `settled` : un dernier rattrapage, puis condamner ce qui reste sans résultat, puis réclamer `settled`
   (étape 8). Le règlement **ne consulte pas la période** : la session 16 se règle le 1<sup>er</sup> juin, donc en
   `VACATION`, pour la même raison que `HousePointsService` ignore la période — la fenêtre porte sur le calendrier des
   sessions, pas sur celui des maisons.
4. **Tirage.** Si une session est en cours, qu'on est dans la **fenêtre 7h-9h** et que `claimDraw(season, session)`
   réussit : lire les candidats et l'historique, appeler `LeaguePairing.draw`, écrire les matchs avec la maison de
   chaque joueur figée **et les exemptions**, puis créer les rencontres OGS et sauver les trois liens. Les exemptions
   s'écrivent avant les appels OGS : elles ne dépendent de personne, et un échec réseau ne doit pas coûter le bonus de
   fin de saison d'un joueur.
5. **Rattrapage des liens et des MP.** Les matchs de la session en cours sans `ogs_match_id` : retenter la création.
   Ceux sans `black_notified` / `white_notified` : retenter le MP (étape 10).
6. **Annonce.** Si la session est tirée et que `claimNotification` réussit : le message de channel.
7. **Suivi des résultats.** Le rattrapage de l'étape 8, pour les matchs de la session en cours.
8. **Clôture de saison.** En `VACATION`, si la saison a `opened`, pas `closed`, **et que la session 16 est réglée** :
   le récap final, puis `closed`. Annonce **avant** écriture, comme la clôture des maisons — un doublon se voit et se
   supprime, un récap perdu se remarque un an plus tard.

   La condition sur la session 16 est ce qui évite d'annoncer un classement faux. Les deux événements tombent le même
   1<sup>er</sup> juin, dans la même fenêtre et donc dans le même tick : sans elle, l'ordre des blocs suffirait, mais
   il suffirait *par accident*, et un récap de fin de saison annoncé sur des matchs encore en suspens est exactement le
   genre d'erreur qu'on ne peut pas rattraper.

Le tirage réclame son garde-fou **avant** de tirer, à l'inverse de la clôture de saison, et pour la même raison que le
classement quotidien des maisons : sur ~1 400 ticks par session, un double tirage est la panne à éviter, et un tirage
manqué se voit dans l'heure.

### Le tirage vide, et son rattrapage

Reste la panne que ce choix crée : un crash entre `claimDraw` et l'écriture des matchs laisse une session marquée tirée
et sans aucun match. Personne ne joue pendant quinze jours, et les 16 sessions du bonus deviennent inatteignables pour
tout le monde. La fenêtre est de quelques dizaines de millisecondes — trois allers-retours base et l'algorithme — mais
elle tombe entre 7h et 9h, ce qui est précisément l'heure à laquelle on redéploie un dimanche matin.

Une session tirée sans match a pourtant **deux causes indistinguables** : ce crash, et le tirage légitimement vide —
personne d'inscrit le 15 septembre, ou tous les actifs dans la même maison. D'où la condition retenue, qui ne cherche
pas à les distinguer mais rend la distinction inutile : **retirer une session vide quand il y a de quoi la remplir
maintenant.**

```
si la session est tirée, sans aucun match ni exemption :
    lire les candidats, appeler draw()
    si draw() ne rend aucune paire  -> ne rien faire, ne rien écrire, ne rien logguer
    sinon                           -> claimRedraw (garde-fou du jour), puis écrire
```

Le cas du crash a des candidats, donc la session est retirée le lendemain matin. Le cas normal n'a rien à apparier, donc
il ne produit ni écriture ni ligne de log — c'est le point qui fait préférer cette condition à une colonne
`candidates` : elle est silencieuse exactement là où l'autre aurait raconté une panne inexistante quinze jours de suite.

`claimRedraw` est un `UPDATE … SET drawn = NOW() WHERE … AND drawn < :startOfDay`, la même mécanique que
`claimDailyRanking` chez les maisons : au plus un rattrapage par jour, sur la colonne qui existe déjà. Il est réclamé
**après** avoir constaté qu'il y a des paires à écrire, et pas avant, sinon on aurait déplacé le problème d'un cran.

⚠ **Effet de bord assumé** : le tirage devient rattrapable en cours de session. Trois joueurs qui s'inscrivent le
16 septembre, sur une session 1 tirée à vide le 15, sont appariés le 17 au matin au lieu d'attendre le 1<sup>er</sup>
octobre. C'est une entorse à « un tirage par session », et elle est acceptée : elle ne concerne que les sessions
totalement vides, et une académie qui se remplit en cours de septembre est exactement le cas où on préfère jouer que
d'attendre.

L'ordre des blocs n'est pas cosmétique. La réconciliation passe avant le tirage pour qu'un joueur parti la veille ne
soit pas apparié et qu'un joueur inscrit hier ait son `member_id` chez OGS. La clôture de la session précédente passe
aussi avant le tirage, pour qu'un `15 septembre 00:05` règle la session écoulée avant d'ouvrir la suivante — sinon un
tick unique tire d'abord et clôture ensuite, ce qui marche mais rend les logs illisibles. Les MP passent après la
création des rencontres, faute de lien à envoyer, et l'annonce de channel après le tirage pour qu'elle ne parle jamais
d'un tirage à moitié écrit.

### La fenêtre du matin

Le tirage part dans la **fenêtre 7h-9h**, la même que le classement quotidien des maisons, et pour la même raison :
c'est le seul moment où une notification a une chance d'être lue le jour même. Un tirage à 00:00 pile enverrait les MP
en pleine nuit. La session perd ses sept premières heures, ce qui sur quinze jours ne se voit pas.

La condition est « une session est en cours, elle n'est pas encore tirée, et il est entre 7h et 9h59 » — et **pas**
« c'est le premier jour de la session ». La différence est ce qui rend le tirage auto-réparant : si le serveur est
tombé le 15 au matin, la session est tirée le 16 au matin, avec quatorze jours devant elle au lieu de quinze. Attacher
le tirage au premier jour perdrait la session entière pour une coupure de deux heures.

Conséquence à connaître dans l'autre sens : un serveur arrêté trois jours fait démarrer la session le quatrième, sans
que rien ne le signale au-delà de la date du `drawn`. C'est acceptable, et c'est ce que la ligne de log doit dire —
« session 8 tirée avec 3 jours de retard » plutôt que « session 8 tirée ».

**Le règlement de la session précédente partage cette fenêtre**, et passe avant le tirage. Ce n'est pas un détail
d'ordonnancement : c'est la date limite elle-même. Une partie doit être lancée avant minuit le dernier jour et terminée
au règlement, donc les sept heures entre les deux sont la marge d'ingestion, et les faire coïncider avec le tirage
suivant donne un moment unique où tout se joue — la session écoulée se ferme, la suivante s'ouvre.

Les deux trous du calendrier ne font pas exception, et c'est voulu : le règlement suit la **première fenêtre après la
fin de la session**, pas le tirage suivant. La session 6 se termine le 15 décembre et se règle le 15 décembre à 7h, même
s'il n'y a pas de tirage ce jour-là ; la session 16 se règle le 1<sup>er</sup> juin à 7h, alors qu'aucune session ne
suit. Sans cette formulation, la session 6 hériterait de dix-sept jours de grâce et la 16 n'aurait aucune date limite.

**Vérification** : contre `fg_dev`, override de période sur `SEASON`, Drooxi et Judas dans deux maisons différentes.
Contrôler qu'un deuxième tick ne retire pas, qu'un redémarrage ne retire pas, et que `league_matches` contient
exactement une ligne.

---

## Étape 8 — Résultats et renommée

Dépend de : 7. **Deux chemins d'écriture, et il faut les deux** : le balayage, qui apporte les résultats, et le
règlement, qui ferme ce qui n'est pas arrivé.

### Pourquoi il n'y a pas de callback

OGS propose un callback de fin de partie, un `GET` sur une URL à nous avec l'`id` de la rencontre substitué. Le plan
l'a longtemps prévu. Il est écarté, et l'argument est court : **il ne transporte aucune donnée.** Le handler n'aurait rien
pu faire d'autre que relire la vérité chez OGS — sa charge utile est un id, pas un résultat — donc il aurait déclenché
exactement l'appel que le balayage fait déjà. Ce n'était jamais une source, seulement une notification.

Et il ne pouvait pas non plus être le chemin principal : aucun callback n'arrive en dev, puisque le
`callback_url_template` est global à la ligue et pointerait la production. Le balayage était donc obligatoire de toute
façon, ce qui faisait du callback un second chemin redondant vers la même donnée.

Restait un seul argument sérieux, le trafic : interroger chaque rencontre en attente une par une, c'est N requêtes par
passage, donc une cadence lente et une latence de l'ordre de l'heure. Il tombe avec la mesure du 10 août au soir —
`GET /matches/` rend l'objet complet de **toutes** les rencontres, et tout champ du modèle est un filtre, `__startswith`
compris. Un balayage est donc **un appel**, et on peut le passer à chaque tick.

Ce que l'abandon supprime, et qui n'était pas rien : une route publique non authentifiée tenue de répondre 200 à
n'importe quel id, `0` compris ; une étape de déploiement manuelle réservée à la production, dont l'oubli était
silencieux ; l'inconnue « quel id OGS substitue-t-il, le sien ou le nôtre ? », qui imposait deux colonnes indexées et
deux `SELECT` par appel ; et un couplage entre le nom d'hôte de production et un état stocké chez OGS — déménager le
serveur aurait voulu dire penser à re-`PUT`er.

⚠ Ce que ça ne change pas : le résultat vient toujours d'OGS et non de `ogs_games`, et les deux inconnues qui restent —
ce que contient `game`, et le type réel des champs de résultat sur une rencontre terminée — pèsent identiquement sur les
deux conceptions, puisque les deux lisent la même réponse.

### Le balayage

Dans le tick de l'étape 7, pour la session en cours, **un seul appel** :

```
GET /matches/?league_match_id__startswith=<db.name>_<saison>_<session>_&page_size=100
```

Le préfixe fait tout le travail : il borne la réponse à une session, d'une saison, d'un environnement. La réponse ne
grossit donc jamais avec l'historique de la ligue, et un run de dev ne voit par construction que ses propres rencontres.
C'est le même préfixe que celui introduit pour éviter les collisions d'identifiants — il sert deux fois.

Puis, pour chaque rencontre rendue, rapprochée de sa ligne par `league_match_id` :

1. Dès qu'un `game` est connu, écrire `ogs_game_id` et `gold_id = "OGS_<game>"`.
2. Si `finished`, lire le résultat dans la réponse et l'écrire avec `finished = NOW()`, via `finishMatch`, dont le
   `WHERE result IS NULL` fait que rien ne ressuscite un match déjà réglé.

Trois précautions de mise en œuvre :

- **Charger la session en une requête**, `matches(season, session)`, et rapprocher en Kotlin. Un `SELECT` par rencontre
  rendue serait N requêtes pour rien.
- **Suivre `next`, jamais incrémenter `page`.** Une page au-delà de la dernière répond **400** `Invalid page.`, pas une
  page vide. À 100 par page et une vingtaine de rencontres par session, la pagination ne se déclenchera jamais — c'est
  précisément pourquoi il faut l'écrire correctement du premier coup, puisque rien ne l'exercera.
- **Un champ de filtre inconnu est un 400**, pas un filtre ignoré — mesuré. C'est ce qui rend ce balayage sûr à
  construire : le mode de panne redouté, filtrer et se faire ignorer en silence, n'existe pas ici.

**Le résultat vient d'OGS, pas de `ogs_games`.** C'est la correction que la sonde a apportée à ce plan : la rencontre
porte neuf champs `readOnly` qu'OGS remplit — `outcome`, `black_lost`, `white_lost`, `annulled`, `moderator_annulled`,
`annulment_reason`, `rating_complete`, `black_member_rating`, `white_member_rating`. Il n'y a donc besoin ni de la
jointure sur `ogs_games`, ni d'un appel à `/api/v1/games/{id}`.

Deux conséquences qui valent mieux que l'économie d'une jointure :

- **L'angle mort des parties annulées se ferme.** `annulled` et `moderator_annulled` disent explicitement qu'une partie
  a été annulée, et `annulment_reason` pourquoi. Le plan documentait jusqu'ici ce cas comme un défaut hérité de
  l'ingestion OGS — `OgsService` écarte les parties annulées, `OgsRealTimeService` ignore la notion, et aucun des deux
  ne défait une annulation postérieure. La ligue, elle, peut traiter le cas proprement : une rencontre annulée n'est pas
  une victoire, et le savoir ne demande aucun travail supplémentaire.
- **La ligue ne dépend plus du pipeline d'ingestion pour ses résultats.** `ogs_games` reste utile — c'est par lui que
  les parties de ligue alimentent les maisons et FGC (décision 8) — mais un scraper OGS cassé ne bloque plus le
  classement de la ligue. Et la renommée ne dépend même pas de `game` : le vainqueur est sur l'objet rencontre, donc
  `ogs_game_id` et `gold_id` ne servent qu'au lien vers `ogs_games` pour le site.

⚠ Le type réel de `outcome`, `black_lost` et `white_lost` reste à constater sur une rencontre terminée : la spec
OpenAPI les annonce en `string`, ce qui est douteux pour deux champs dont le nom dit un booléen. C'est le premier point
à vérifier quand la première partie de ligue se terminera, et le seul endroit du module qui en dépend est la fonction
qui convertit une réponse OGS en `result`.

Recopier le résultat dans `league_matches` plutôt que le relire à chaque affichage garde tout son sens : `CleanService`
supprime les parties au bout de 32 jours, et surtout un classement ne doit pas dépendre d'un appel réseau par ligne.

### Le règlement d'une session

C'est ce qui implémente « 0 point pour les deux, non rejouable ». Dans la **première fenêtre 7h-9h après l'`end` d'une
session**, en trois temps :

1. Un dernier passage de rattrapage, pour ne pas condamner un match dont le résultat était à un appel de nous.
2. `markUnplayed(season, session)` : `result = 'unplayed'`, `finished = NOW()`, sur **tout** ce qui reste à `NULL`.
3. Réclamer `settled`.

L'étape 2 ne fait aucune distinction, et c'est la simplification que la fenêtre de sept heures rend possible. La
question « la partie a-t-elle commencé ? » n'a plus à être posée : une partie lancée avant minuit le dernier jour a eu
sept heures pour se terminer et se faire ingérer, et si elle ne l'a pas fait, elle ne compte pas. Les trois façons
d'arriver là — une partie abandonnée en cours et laissée en plan sur OGS, une partie annulée avant d'être ingérée
(l'angle mort du barème), une partie qu'un scraper cassé n'a pas vue — reçoivent le même traitement, ce qui est
préférable à trois branches dont deux ne se déclencheraient jamais en test.

Surtout, **le règlement ne laisse rien à `NULL`**, ce qui ferme définitivement le trou : plus aucun match ne peut rester
suspendu et coûter en silence, six mois plus tard, le bonus « sans faute » de ses deux joueurs.

L'ordre — rattraper, condamner, réclamer — reprend celui de l'ouverture de saison des maisons : le garde-fou en dernier,
pour qu'un tick interrompu reprenne exactement là où il s'est arrêté au lieu de brûler sa seule chance.

### La renommée

Calculée en lecture, dans `standings(season)` :

```
renown = 2 × joués + 5 × victoires + (joués + exemptés == sessionCount ? 10 : 0)
```

« Joué » veut dire `result IS NOT NULL AND result != 'unplayed'`, « exempté » une ligne de `league_exemptions`. Pas de
table de points, pas de service de scoring — voir le barème plus haut.

L'égalité est un `==` et pas un `>=`, et c'est un choix, pas une paresse : les deux comptes portent sur des sessions
distinctes par construction — un joueur exempté d'une session n'y a pas de match — donc leur somme ne peut pas dépasser
`sessionCount`, et un `>=` masquerait un bug de comptage au lieu de le laisser apparaître. La propriété tient parce qu'un joueur
n'est jamais à la fois apparié et exempté sur une même session — c'est l'invariant que l'étape 6 teste en priorité.

### Une partie de ligue compte partout, et c'est voulu

Avec les réglages de la décision 3, une partie de ligue passe **tous** les filtres existants de l'application, et rien
n'est fait pour l'en empêcher :

| Compteur | Ce qu'une victoire en ligue rapporte |
|---|---|
| Renommée | 2 (joué) + 5 (victoire) = **7** |
| Maisons | `played` 1 + `gold_opponent` 2 + `rival_house` 2 + `long_game` 2 + `victory` 2 + `even_game` 1 + `ranked` 1 = **11**, le maximum du barème |
| FGC | une partie de plus, et — si les parties sont classées — une partie classée de plus |

Les 11 points de maison ne sont pas un accident : les deux joueurs sont liés (`gold_opponent`), le tirage interdit les
paires intra-maison (`rival_house`), et notre payload impose le temps long et le handicap nul. Jouer en ligue, c'est
jouer pour sa maison — c'est exactement ce que dit le brief.

⚠ Une seule de ces sept lignes n'est pas garantie : `ranked`. Aucun champ de ce nom n'existe dans le payload de création,
donc rien ne permet d'affirmer que les parties de ligue seront classées. Si elles ne le sont pas, le total tombe à 10 et
`total_ranked_games` n'est pas crédité — à constater sur la première partie jouée.

**Zéro ligne de code pour obtenir ça** : c'est ce qui se produit si on ne fait rien, et les alternatives auraient toutes
demandé d'altérer `house_games` ou `fgc_validity_games` en prod, ce que ce plan évite partout ailleurs.

Une conséquence à connaître, cohérente mais surprenante : une partie jouée **après** la fin de sa session rapporte
toujours ses points de maison et sa validité FGC, tout en ne rapportant plus de renommée. Les deux règles sont justes
prises séparément — la ligue a une date limite, la maison non — mais le joueur qui pose la question mérite cette
réponse-là et pas un haussement d'épaules.

**Vérification** : un match posé à la main dans `fg_dev` avec le `league_match_id` de la rencontre de la sonde, puis un
tick — le balayage doit la retrouver par le préfixe, lire `started: false`, `game: null` et ne rien écrire. Puis la même
chose sur une rencontre réellement jouée, pour contrôler le `game` récupéré, le `result` recopié et le classement. Le
règlement se teste en posant un match sur une session déjà passée et en vérifiant qu'il finit `'unplayed'`, puis qu'un
balayage ultérieur ne le ressuscite pas — c'est le `WHERE result IS NULL` de `finishMatch` qui doit tenir.

⚠ Deux choses que rien n'exercera spontanément, et qu'il faut donc provoquer : la **pagination** (poser plus de
`page_size` rencontres dans une session, ou baisser `page_size` à 1 le temps d'un test) et le cas **`game` non nul**,
qui n'apparaît qu'une fois une partie réellement lancée.

---

## Étape 9 — API de lecture

Dépend de : 3, 8.

`GET /gold/api/league` — la page « Ligue ». Saison, session courante (numéro et bornes), nombre total de sessions, et
le classement complet.

`GET /gold/api/league/session/{number}` — les appariements d'une session : les deux joueurs (identité Discord, maison,
rating), le **lien spectateur**, et le vainqueur quand le match est terminé — ou l'état « non joué » quand la session
est réglée. 404 hors 1-16.

Le bloc `league` s'ajoute à `GET /gold/api/player/{id}`, à côté de `house` : membre ou non, actif ou non, sa renommée
détaillée, son rang, ses matchs de la saison avec leur adversaire et leur résultat. Composé dans le handler, **pas**
dans la vue `api_players` : le calcul dépend de la saison courante, connue du Kotlin seul, et ça évite une modification
de vue en prod.

Tous les handlers suivent le pattern maison : `context.handle("nomDeRoute") { ... }`, réponses via les helpers de
`ContextExtensions`, rang de compétition (1, 2, 2, 4) calculé côté serveur, `total` toujours dans la réponse pour que
personne ne réadditionne le barème.

**⚠ Règle de confidentialité, non négociable** : `black_invite` et `white_invite` ne sortent **jamais** de l'API. Le
code d'OGS le dit explicitement — « the assumption is that only the correct user has been given the key » — et aucune
route de ce serveur n'est authentifiée : exposer un lien joueur, même sur le profil de l'intéressé, laisserait
n'importe qui jouer le match de n'importe qui. Seul `spectator_link` est public, et c'est aussi pour ça que le renvoi
d'un lien perdu se fait à la main.

### Forme des réponses

⚠ Contrat **posé côté serveur**, comme celui des maisons : le site s'aligne. Les formes déjà servies par
`/gold/api/houses` sont reprises à l'identique, pour que le front réutilise ses composants.

```json
// GET /gold/api/league
{
  "season": "2026-2027", "period": "SEASON",
  "sessionCount": 16,
  "currentSession": { "number": 8, "start": "2027-01-15T00:00:00+01:00",
                      "end": "2027-02-01T00:00:00+01:00", "drawn": true, "settled": false },
  "standings": [
    { "discordId": "111", "discordName": "Alice", "discordAvatar": "…",
      "house": { "slug": "NEXUS_ALPHA", "name": "Nexus Alpha", "color": "#0E1A40" },
      "active": true, "rank": 1,
      "played": 7, "won": 5, "lost": 2, "exempted": 1,
      "renown": { "playedPoints": 14, "victoryPoints": 25, "perfectBonus": 0, "total": 39 } }
  ]
}

// GET /gold/api/league/session/{number} : mêmes season, period, sessionCount, la session sous "session"
// (forme identique à currentSession), plus :
{
  "matches": [
    { "black": { … même forme qu'une ligne de standings, sans les compteurs … },
      "white": { … },
      "spectatorLink": "https://online-go.com/…",
      "result": "black", "winnerDiscordId": "111" }
  ],
  "exemptions": [ { "discordId": "222", "discordName": "…", "discordAvatar": "…",
                    "house": { … }, "reason": "ODD" } ]
}

// GET /gold/api/player/{id}, à côté de "house" :
"league": {
  "season": "2026-2027", "period": "SEASON", "sessionCount": 16,
  "active": true, "rank": 3,
  "played": 7, "won": 5, "lost": 2, "exempted": 1,
  "renown": { "playedPoints": 14, "victoryPoints": 10, "perfectBonus": 0, "total": 24 },
  "matches": [
    { "session": 8, "color": "black", "opponent": { … }, "spectatorLink": "…",
      "result": "black", "won": true }
  ]
}
```

Six points de ce contrat qui ne se devinent pas :

- **`result` a trois états et le site doit les distinguer.** `null` veut dire « la session est en cours, le match n'est
  pas encore joué » ; `"unplayed"` veut dire « la session est réglée, ce match ne comptera jamais » ; le reste est un
  résultat. Les afficher pareil ferait passer un match perdu par forfait pour un match à jouer.
- **`winnerDiscordId` est calculé, pas déduit.** `result` porte `"black"` ou `"white"`, et faire la correspondance
  côté site suppose de savoir quel joueur était de quel côté — le serveur le sait, donc il le dit. `null` quand il n'y a
  pas de vainqueur.
- **Les exemptions sont dans la réponse de la session**, à part des matchs. Sans elles, la page d'une session laisse
  croire qu'un membre actif a été oublié, alors que le tirage a explicitement constaté qu'il n'y avait personne pour lui.
  `reason` vaut `ODD` ou `NO_RIVAL`.
- **`exempted` est dans les compteurs du classement.** C'est ce qui rend le bonus « sans faute » lisible : `played +
  exempted == sessionCount` est la condition, et un site qui n'affiche que `played` donnera l'impression que le bonus est
  attribué à tort.
- **Le bloc `house` est le sous-ensemble « blason »** des maisons — `slug`, `name`, `color` — sans `tagline` ni
  `description`, comme dans le bloc `house` d'un profil. Un classement de vingt joueurs ne traîne pas quatre paragraphes
  de RP répétés vingt fois.
- **`"league": null`** sur un joueur qui n'a jamais été membre de la saison en cours, clé présente et valeur nulle, pas
  clé absente : c'est le mapper JSON de Javalin qui sérialise, comme pour `"house": null`.

Le classement inclut les **inactifs**, avec `active: false` — le brief le demande explicitement, leurs points restent
visibles. Ils sont classés comme les autres : leur rang est réel, ils ne sont juste plus tirés.

Le chemin est **`/league`**, des deux côtés : `/gold/api/league` pour l'API, et `LEAGUE_PATH = "/league"` pour la page du
site vers laquelle pointent les annonces Discord (`frontend.url` + le chemin, comme `/houses` chez les maisons). Une
constante unique dans `LeagueNotifier`, et rien d'autre du serveur n'en dépend.

**Vérification** : `curl` sur les trois routes en local contre `fg_dev`, et un `grep` sur les réponses pour s'assurer
qu'aucun `invite` n'y figure.

---

## Étape 10 — Annonces Discord et MP

Dépend de : 7.

`LeagueNotifier`, sur le modèle de `HouseNotifier` : du formatage, rien d'autre. Le *quand* reste dans le service,
parce que les garde-fous sont des lignes de `league_sessions` et de `league_matches`. Textes en français, une ligne de
log par annonce — `queue()` ne rend rien, donc sans elle « le MP est-il parti ? » est sans réponse après coup.

**Annonce de tirage**, sur `bot.notification.channel.id` : « les appariements de la session N sont prêts », le nombre
de matchs, et le lien vers le site. Pas la liste des paires : à 20 matchs l'embed est illisible et plafonne de toute
façon à 2 048 caractères (`ellipsize` dans `DiscordBot`).

**MP aux joueurs** : à chaque joueur apparié, son adversaire, son côté, son lien de challenge et **la date de fin de
sa session**, puisque passé cette date le match ne compte plus. C'est le chemin critique de la feature.

`DiscordBot` ne sait pas envoyer de MP aujourd'hui. Il faut ajouter un `sendPrivateMessageEmbeds(discordId, ...)`, sur
le modèle de `modifyRole` : `openPrivateChannelById(...).flatMap { it.sendMessageEmbeds(...) }.queue(...)`, avec la
même discipline — rien ne remonte à l'appelant, chaque échec est une ligne de log, et un `try/catch` synchrone parce
que JDA lève côté client avant même de mettre en file.

Mais contrairement à un rôle, un MP raté n'est pas cosmétique, et le traitement est donc plus lourd que pour les
maisons — c'est **la seule chose de ce module qui ne peut pas se rattraper toute seule** :

- `black_notified` / `white_notified` ne sont stampés **qu'au succès** du `queue()`, dans le callback de succès de JDA
  et pas avant. C'est l'inverse du choix fait pour le classement quotidien des maisons, où réclamer d'abord évitait le
  spam : ici un doublon de MP est bénin et un MP perdu est bloquant, donc l'arbitrage s'inverse avec lui.
- Le tick retente les MP non stampés à chaque passage, tant que la session est en cours. Un joueur momentanément
  injoignable finit par recevoir son lien.
- Un échec **définitif** — MP fermés, pas de serveur en commun — se voit à un `notified` toujours nul en fin de
  session. Le log nomme le joueur et le match ; les liens sont en base, et le renvoi se fait à la main. C'est la
  décision actée, et c'est pour ça que les liens ne sont jamais effacés.
- ⚠ Une piste non retenue mais qui règlerait le cas proprement : exposer le lien derrière l'auth Discord existante
  (`GET /gold/api/auth/profile` authentifie vraiment, contrairement au reste de l'API). À garder pour plus tard si les
  renvois manuels deviennent fréquents.

**Récap de fin de saison** : le vainqueur de la ligue, le classement des académies, le meilleur joueur de chacune. Une
fois, garde-fou `league_seasons.closed`.

**Channel** : `bot.notification.channel.id`, celui des maisons et des parties. Aucune clé de config nouvelle, aucun
channel à créer sur les deux serveurs Discord, et les joueurs le suivent déjà. Dix-sept messages par saison — seize
tirages et le récap — dans un channel qui reçoit chaque partie jouée sur KGS et OGS : c'est peu, au prix d'être noyé dans
ce flux. Si ça devient un sujet, une clé optionnelle avec repli sur celle-ci se pose plus tard sans rien casser.

**Vérification** : contre `fg_dev` et le bot de dev, avec Drooxi et Judas. Vérifier qu'un MP reçu stampe bien
`notified`, et — en fermant les MP d'un des deux comptes — qu'un échec laisse la colonne nulle, log une ligne, et est
retenté au tick suivant sans bloquer le reste.

---

## Étape 11 — Finitions

Dépend de : tout ce qui précède.

**Purge** — `league_members` et `league_players` dans `CleanDatabaseAccessor.removeAllFrom`, et **pas**
`league_matches` ni `league_exemptions` : les deux portent la renommée d'une académie, qui ne doit pas rétrécir quand
un joueur s'en va.

**Santé** — rien à faire : le service de la ligue s'enregistre seul dans `ServiceRegistry` depuis
`PeriodicFlowService.start()`. Avec 600 s d'intervalle et 150 s de délai initial, le seuil de péremption sera de
`max(600 × 5, 60) + 150 = 3 150 s`. Vérifier que `GET /gold/api/health` répond 200 avec le nouveau compte de services.

**Config** — quatre clés nouvelles, dans les **trois** fichiers de `modules/common/src/main/resources/` :
`config.properties.dev`, `config.properties.prod` **et** la copie de travail `config.properties`. Seul le dernier est sur
le classpath, donc une clé ajoutée aux deux variantes seulement n'existe pas pour l'application qui tourne en local. Le
suffixe se place après `.properties`, jamais avant : `release.sh` copie par nom exact et un `dev.config.properties` casse
la release sans message.

Pas de clé d'URL : la base est `ogs.api.url`, qui existe déjà, et le client ajoute `/online_league` comme `OgsService`
ajoute `/players`.

| Clé | Rôle | Dev / prod |
|---|---|---|
| `ogs.league.id` | L'en-tête `X-OGS-LEAGUE` — `FulguroGo` | **identique** : une seule ligue |
| `ogs.league.auth` | L'en-tête `X-OGS-LEAGUE-AUTH`, la clé d'API | **identique** |
| `league.member.salt` | Le sel du `member_id` OGS. **Ne change jamais** : il porte l'identité OGS de chaque joueur | **identique**, obligatoirement |
| `league.test.players` | Le bac à sable : les seuls Discord id autorisés dans la ligue. Vide = aucune restriction | `236813095207436289,453473841252007937` en dev, **vide** en prod |

Trois clés identiques sur quatre, l'inverse des autres blocs de config — c'est le prix de la ligue unique.
`league.member.salt` **doit** être identique : un sel différent en dev donnerait aux deux comptes de test un second
`member_id` dans la même ligue, donc deux appartenances pour une seule personne. Il ne reste donc que
`league.test.players` pour séparer les deux environnements.

Elle est la deuxième clé du projet lue par `Config.getOrNull` plutôt que `Config.get`, après `house.period.override`, et
pour la même raison : elle doit pouvoir rester vide sans faire tomber un service. Comme elle, elle est journalisée au
démarrage quand elle est renseignée — et ici cette ligne de log est le dernier rempart avant la production.

**Documentation** — `CLAUDE.md` : `league` dans l'ordre d'`init()` (avant `api`, qui en dépend), les intervalles au
paragraphe sur l'étalement des ticks, les quatre clés à l'énumération — dont `league.test.players` au paragraphe des
clés optionnelles, à côté de `house.period.override` —, un point sur la ligue dans le flux de données, la nouvelle capacité MP de `DiscordBot`, et un mot
sur `league.member.salt` : c'est la première clé du projet dont la **perte** détruit une donnée métier plutôt que de
faire tomber un service. `doc/schema.sql` gagne les six tables. ⚠ Au passage : `CLAUDE.md` renvoie à `doc/changelog.txt`, qui **n'existe pas**
dans le dépôt — à créer ou à retirer de la doc.

**Version** — `fulgurogo.version.name` passe de 9.0 à 9.1, et `./gradlew :app:shadowJar` doit produire
`app-9.1-all.jar`.

**Vérification** : `./gradlew build`, `./gradlew :app:run`, et `/gold/api/health` à 200.

---

## Ce qui reste en suspens

Les vingt questions du plan sont tranchées, la clé d'API est en main, et le journal complet suit. **Rien ne bloque plus
l'écriture du code.** Il reste une vérification et trois risques à connaître.

**À constater sur la première partie terminée**

1. **Le type réel des champs de résultat** — `outcome`, `black_lost`, `white_lost`. La spec OpenAPI les annonce en
   `string`, ce qui est douteux pour deux champs dont le nom dit un booléen. Un seul endroit du code en dépend : la
   fonction qui convertit une réponse OGS en `result`.

   ⚠ Et il y a un piège, vu le 11 août sur la partie **en cours** : sur l'API des parties, `black_lost` **et**
   `white_lost` valent tous deux `true` tant que la partie n'est pas finie, `outcome` restant `""`. Lire les deux
   drapeaux sans vérifier d'abord que la rencontre est terminée désignerait donc deux perdants. `OgsApiGame.result()`
   teste `outcome` en premier, ce qui le protège déjà ; `OgsLeagueMatch.loser()` ne rend un côté que si **exactement** un
   des deux a perdu, ce qui le protège aussi. Les deux garde-fous existent : ne pas les retirer.

**Constaté le 11 août, et donc réglé**

- **Le `speed` et le caractère classé.** Aucun n'est dans le payload de création, et la partie 89632834 sort avec
  `"speed": "live"` et `"ranked": true`. Le bonus `long_game` des maisons est donc acquis (`isLongGame()` veut `live`
  **et** `main_time >= 1200`), l'ingestion voit la partie, et `ranked` crédite maisons comme FGC.
- **Ce que contient `game`** — l'`id` de la partie OGS, un entier, distinct de l'`id` de la rencontre : `13688` pour la
  rencontre, `89632834` pour la partie. `gold_id = "OGS_<game>"` est donc juste.

**Les trois risques à connaître**

3. **La ligue OGS est partagée entre dev et prod**, et c'est le seul endroit du projet où un run local peut toucher la
   production. `league.test.players` est l'unique garde-fou : renseignée en dev, **vide en prod**, et journalisée au
   démarrage. Le préfixe `db.name` sur `league_match_id` est la seconde moitié de cette précaution.
4. **`league.member.salt` porte l'identité OGS de tous les joueurs**, vit dans un fichier gitignoré, et n'est donc pas
   dans les sauvegardes de la base. Le perdre ou le changer réinscrit tout le monde sous de nouveaux `member_id`. À
   sauvegarder comme `bot.token`.
5. **Deux angles morts hérités**, qui n'appartiennent pas à la ligue mais l'atteignent. Les **parties annulées** après
   ingestion, que ni `OgsService` ni `OgsRealTimeService` ne défont : la ligue s'en sort, puisqu'OGS le lui dit sur la
   rencontre, mais `house_points` gardera les points d'une partie annulée. Et `gold_ranks.error`, qui **somme** les
   drapeaux d'erreur par plateforme — un scraper KGS cassé peut sortir d'un tirage tous les joueurs ayant un compte KGS,
   et avec la décision 6 leur coûter le bonus de la saison. Les jours de tirage, regarder `/gold/api/health`.

---

## Journal des décisions

**Règles de jeu**

1. ~~**Le bonus de 10 points**~~ — tranché : **16 sessions jouées ou exemptées**. Un joueur que le tirage laisse sans
   adversaire n'est pas pénalisé, mais ne gagne rien non plus ; un joueur inscrit en cours de saison, ou qui a quitté
   son académie, ne peut pas l'avoir. Coûte la table `league_exemptions` (étape 0), écrite par le tirage.
2. ~~**Jigo et parties annulées**~~ — tranché : le jigo est rendu impossible par le komi à demi-point de
   `rules: japanese` — 6,5, mesuré, et non 7,5 comme d'abord écrit —, et
   traité au minimum s'il survenait (jouée, sans victoire). Les parties annulées, elles, sont **proprement traitées** —
   la sonde a révélé qu'OGS renseigne `annulled` et `moderator_annulled` sur la rencontre. ⚠ Reste l'angle mort du reste
   de l'application, qui ne défait pas une annulation postérieure dans `house_points` — voir le barème.
3. ~~**Réglages de partie**~~ — tranché **et corrigé par la sonde** : ils ne sont pas configurés sur la ligue côté OGS,
   ils partent dans le payload de chaque `POST /matches/`. `rules: japanese`, `handicap: 0`, `19×19`,
   `time_control: byoyomi`, `main_time: 2400`, `periods: 5`, `period_time: 30`. Chaque valeur est contrainte par le code
   existant plutôt que choisie — voir le tableau en tête de plan. Plus rien à demander à OGS. ✅ Et les deux inconnues que
   le payload laissait sont levées le 11 août : la partie sort `ranked` et `live`. Une seule correction au tableau — le
   komi est **6,5**, pas 7,5 ; le jigo reste impossible parce que c'est un demi-point, et 6,5 passe encore la fenêtre de
   FGC, avec 0,5 de marge au lieu de 1,5.
4. ~~**Rating poussé chez OGS**~~ — tranché : une constante neutre identique pour tous, `1500`, poussée une fois à
   l'inscription. Les deux classements — celui d'OGS et notre rating gold, qui sert au tirage — restent séparés.
   Supprime la colonne `ogs_rating` de `league_players`.
5. ~~**`REPEAT_PENALTY` et algorithme**~~ — tranché : pénalité de **400**, soit deux paliers de `gold_tiers`, et
   **glouton puis 2-opt**. Pas de garantie d'optimalité, assumée ; l'invariant testable est que le total ne peut que
   baisser.
6. ~~**Cas de bord du tirage**~~ — tranché : **pas de bye** (l'exemption neutralise, elle ne crédite pas) ; le banc
   revient au candidat **le moins souvent exempté** de la saison, départagé au sort ; les **couleurs sont tirées au
   sort** ; un joueur sans rating exploitable (`rating = 0` ou `error = 1`) n'est **ni candidat ni exempté**, et perd
   donc son bonus — seule règle où une panne serveur coûte quelque chose au joueur.
7. ~~**Heure du tirage**~~ — tranché : **fenêtre 7h-9h**, comme le classement quotidien des maisons, avec les MP et
   l'annonce dans la foulée. Condition sur « session en cours et pas encore tirée », pas sur « premier jour », ce qui
   rend le tirage auto-réparant après une coupure.
8. ~~**Points de maison et validité FGC**~~ — tranché : **oui aux deux**, et c'est ce qui se produit sans écrire une
   ligne. Une victoire en ligue vaut 7 de renommée et 11 de maison, le maximum du barème. ⚠ Le bonus `ranked` et le
   comptage `total_ranked_games` dépendent du fait que les parties soient classées, ce que le payload ne permet pas de
   demander — à constater.
9. ~~**Le match qui ne se termine jamais**~~ — tranché : la partie doit être **lancée** avant minuit le dernier jour et
   **terminée** au règlement, fixé à la première fenêtre 7h-9h après la fin de la session — donc en pratique le tirage
   suivant, sauf pour les sessions 6 et 16 qui n'en ont pas. Tout ce qui reste sans résultat à ce moment passe
   `'unplayed'`, sans distinguer si la partie avait commencé. Plus aucun match ne peut rester en suspens.

**Implémentation et contrat**

10. ~~**`member_id` OGS**~~ — tranché : **`sha256(discordId + sel)` tronqué à 32 caractères**, calculé et non stocké,
    le sel dans `league.member.salt`. Supprime la colonne `ogs_member_id`. Le sel devient un secret à ne jamais
    changer et à sauvegarder avec les autres, puisqu'il porte l'identité OGS de chaque joueur.
11. ~~**Idempotence de la création de rencontre**~~ — **répondu par la sonde du 10 août : `POST /matches/` est
    idempotent sur `league_match_id`.** 201 à la création, 200 ensuite, même `id`, mêmes liens, corps identique. Donc
    pas de `findMatch`, pas de lecture préalable, et reprendre un tirage interrompu se réduit à rejouer le `POST`. Le
    filtre `?league_match_id=` existe aussi et est honoré, mais n'a plus d'usage. La garde `ogs_match_id IS NULL` reste,
    par sécurité et non plus par nécessité — un re-`POST` renvoyant les mêmes liens, il n'y a plus de lien à écraser.
12. ~~**Nommage**~~ — tranché : module `league`, TAG `LGE`, tables `league_*`, comme `house` / `HSE`. Le français
    reste aux messages Discord et aux libellés servis au site.
13. ~~**Authentifier `join` / `leave`**~~ — tranché : **non**, comme `link` et `house/join`. Exposition assumée : un
    tiers peut désinscrire un joueur et lui coûter le bonus de la saison. Atténué par le fait qu'un `join` avant le
    tirage suivant restaure tout, et par la ligne de log du `leave`.
14. ~~**Déliaison OGS**~~ — tranché : **réconciliation périodique seule**, une requête au tick qui désactive tout membre
    actif sans `ogs_user_info` ou sans `house_members`. **Aucun parcours de déliaison n'est prévu** côté site, ni dans
    ce plan ni plus tard. La réconciliation reste utile pour autant : `CleanService.removeDeletedAccounts()` supprime
    déjà les `ogs_user_info` des comptes OGS supprimés sans toucher au reste, donc le cas arrive en production.
15. ~~**Purge de `league_players`**~~ — tranché : **purgé**, comme les autres tables du joueur. Le hash de la décision 10
    redérive à l'identique, donc le retour ne coûte qu'un `PUT member` rejoué — **mesuré idempotent** par la sonde, 201
    puis 200. ⚠ Correction au passage : `DELETE /member/{id}` **existe**, contrairement à ce que ce plan affirmait. On
    choisit de ne pas l'utiliser, conformément au brief, mais l'argument « aucun endpoint ne retire un membre » était
    faux.
16. ~~**Client OGS**~~ — tranché : **étendre `OgsApiClient`** avec un `put` et un paramètre d'en-têtes optionnel, par
    défaut vide pour ne rien changer aux deux services existants. `ensureSpamDelay` reste par instance : le rendre
    statique ralentirait `OgsService`, qui tourne toutes les 15 s.
17. ~~**Tirage réclamé puis interrompu**~~ — tranché : **rattrapage automatique conditionné à l'appariabilité** — une
    session tirée sans match est retirée seulement si le tirage rend au moins une paire aujourd'hui, ce qui distingue le
    crash du tirage légitimement vide sans colonne supplémentaire et sans bruit. Garde-fou journalier `claimRedraw`.
    Effet de bord accepté : un tirage vide devient rattrapable en cours de session.
18. ~~**L'`id` du callback**~~ — devenu sans objet : **il n'y a pas de callback**. La question était « OGS substitue-t-il
    son `id` ou notre `league_match_id` ? », et la réponse choisie était de chercher sur les deux colonnes indexées, pour
    ne pas parier sur une convention inconnue dont l'erreur aurait été silencieuse. Le callback ayant été écarté — il ne
    transportait aucune donnée, donc il ne faisait que déclencher l'appel que le balayage fait déjà —, l'inconnue
    disparaît avec lui, ainsi que le second `SELECT`. Voir l'étape 8.
19. ~~**Contrat avec le site**~~ — tranché : le serveur **pose** la forme, comme pour les maisons, en reprenant à
    l'identique ce que `/gold/api/houses` sert déjà. Écrite à l'étape 9. Le chemin est `/league`, des deux côtés — API et
    page du site.
20. ~~**Channel Discord des annonces**~~ — tranché : `bot.notification.channel.id`, celui des maisons. Aucune clé
    nouvelle, aucun channel à créer.
