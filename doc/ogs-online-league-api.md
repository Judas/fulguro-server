# L'API Custom OGS Online Leagues, telle qu'elle est

Référence de l'API que la Ligue d'Aurak utilise (`doc/plan-ligue.md`). Elle existe parce que les deux sources
publiques sont incomplètes ou trompeuses prises séparément, et parce qu'une partie de ce qui suit a été **mesurée**,
pas lue.

**Sources**

| Source | Ce qu'elle donne | Ce qu'elle ne donne pas |
|---|---|---|
| [Wiki : Custom OGS Online Leagues](https://github.com/online-go/online-go.com/wiki/Custom-OGS-Online-Leagues) | Le mode d'emploi, l'authentification, les quatre appels du parcours normal, le contact pour obtenir une clé | 4 champs sur 35 en réponse, rien sur l'idempotence, les méthodes, la pagination, le filtrage |
| [Doc d'API](https://online-go.com/api-docs/) — spec brute sur [`/api-docs/schema/?format=json`](https://online-go.com/api-docs/schema/?format=json) | La spec OpenAPI 3.0.3 complète : tous les champs, lesquels sont `readOnly`, tous les endpoints | Aucun exemple, aucune sémantique — et elle expose des endpoints dangereux sans le dire |
| Le dépôt [`online-go/online-go.com`](https://github.com/online-go/online-go.com) | Les types du frontend (`src/models/onlineleague.d.ts`), le parcours joueur | Le backend est **fermé** : 8 dépôts publics dans l'organisation, aucun n'est le serveur |

La page `/api-docs/` est rendue en JavaScript : un `curl` dessus ne rend que l'en-tête. La spec exploitable est sur
`/api-docs/schema/?format=json`, ~590 Ko.

**Vérifié les 10 et 11 août 2026**, contre la ligue de production `FulguroGo`. Ce qui est marqué ✅ a été exécuté ; le
reste est lu dans la spec. Le 11 août ajoute ce qu'une partie réellement lancée est seule à pouvoir dire — voir « La
partie qu'une rencontre crée ».

---

## Authentification

Deux en-têtes sur chaque requête :

```
X-OGS-LEAGUE:      <ogs.league.id>
X-OGS-LEAGUE-AUTH: <ogs.league.auth>
```

Les valeurs sont dans `config.properties` (gitignoré), sous ces deux clés. ✅ Sans les en-têtes, les endpoints
d'organisateur répondent **403**. Conséquence utile : les liens d'invitation, qui sont des secrets de joueur, ne
peuvent pas être moissonnés par un tiers via l'API.

L'hôte est `https://online-go.com/api/v1/online_league`. ✅ Le wiki documente ses exemples sur
`beta.online-go.com`, mais la production répond aussi bien — `ogs.api.url` de `config.properties` convient donc comme
base, le code ajoutant `/online_league`.

⚠ **Deux régimes d'authentification cohabitent.** `/matches/...` (pluriel) est l'API de l'organisateur et attend les
deux en-têtes. `/match/{id}` (singulier) est l'API du joueur et attend un compte utilisateur OGS : ✅ un
`DELETE /match/13688` avec les en-têtes de ligue répond **401** « Authentication credentials were not provided ». Ne
pas confondre les deux en lisant le frontend, qui n'utilise que le singulier.

---

## Surface complète

Méthodes confirmées par `OPTIONS` ✅ et par la spec.

| Endpoint | Méthodes | Usage |
|---|---|---|
| `GET,PUT,DELETE /member/{member_id}` | ✅ GET, PUT, DELETE | Inscrire un membre, lire son état, le retirer |
| `GET,POST /matches/` | ✅ GET, POST | Lister et créer des rencontres |
| `PUT,DELETE /matches/` | spec seulement | ⚠ voir les dangers plus bas |
| `GET /matches/{match_id}` | ✅ GET, HEAD, OPTIONS | Lire une rencontre. **Ni PUT ni DELETE ici** |
| `GET,PUT,PATCH /callback` | ✅ GET, PUT, PATCH | Le callback de fin de partie |
| `GET,POST,DELETE /leagues/` | spec seulement | Créer et supprimer des ligues |
| `GET,PUT /commence` | spec seulement | Le parcours joueur, appelé par le navigateur du joueur — **jamais par nous** |
| `GET /match/{id}` | ✅ GET | Le même objet, côté joueur |

---

## `PUT /member/{member_id}`

Le `member_id` est **choisi par l'appelant** : il est dans l'URL, pas retourné. L'API ne demande jamais l'identifiant
OGS du joueur.

```json
{"rating": 1500}
```

Réponse :

```json
{"membership_id": "<le member_id envoyé>",
 "league_rating": null,
 "ogs_player": null,
 "pending_rating_change": {"rating": 1500}}
```

✅ **Idempotent, et le code HTTP distingue les deux cas** : **201** à la création, **200** aux appels suivants, corps
identique. Un `PUT` rejoué est donc sans effet de bord — ce qui autorise à ne rien mémoriser de plus qu'un booléen
« déjà inscrit » de son côté, et à le perdre sans dommage.

✅ **`ogs_player` reste `null` après inscription.** Le lien avec un vrai compte OGS se fait quand le joueur clique son
lien d'invitation et se connecte, pas ici. C'est la propriété qui rend inutile de connaître l'identifiant OGS des
joueurs.

✅ **`league_rating` reste `null`** et le rating envoyé apparaît dans `pending_rating_change` : il ne s'applique donc
qu'à la liaison du compte. OGS entretient ensuite son propre classement de ligue.

✅ **Et voici les deux mêmes champs une fois le compte lié**, mesuré le 10 août au soir, après que JudasImov ait cliqué
son lien d'invitation :

```json
{"membership_id": "a84de96992186b8ae3528bbb083fe951",
 "league_rating": {"rating": 1500.0, "deviation": 350.0, "volatility": 0.06},
 "ogs_player": "JudasImov",
 "pending_rating_change": {"rating": 1500}}
```

Deux choses utiles. `ogs_player` porte le **pseudo OGS** du joueur, donc un `GET /member/{id}` suffit à savoir si un
membre a réellement connecté son compte — sans rien demander à l'API des parties. Et `league_rating` est un objet
Glicko complet, pas un entier : c'est bien OGS qui tient ce classement.

⚠ En revanche `pending_rating_change` **reste renseigné après la liaison**, et rien ne dit quand il est consommé. Un
`PUT /member` rejoué sur un membre déjà lié laisse donc un changement de rating en attente. Tant que la question n'est
pas tranchée, ne re-`PUT`er que les membres dont on n'a pas encore l'inscription — ce que fait `ogs_registered`.

`DELETE /member/{member_id}` existe (spec, non essayé). Un membre **peut** donc être retiré d'une ligue, contrairement
à ce que le wiki laisse croire.

---

## `POST /matches/`

Champs modifiables, d'après la spec. Tout le reste de l'objet est `readOnly`.

| Champ | Type | Note |
|---|---|---|
| `league_match_id` | string, nullable | L'identifiant de l'appelant. C'est la clé de l'idempotence |
| `name` | string, nullable | Aussi utilisé comme nom de la partie |
| `black_member_id` | string, **requis** | |
| `white_member_id` | string, **requis** | |
| `rules` | string, nullable | `japanese`, … |
| `handicap` | int, nullable | `0` pour une partie à égalité |
| `height`, `width` | int, nullable | `19` |
| `time_control` | string, nullable | `byoyomi`, `canadian`, `fischer`, `simple`, `absolute` |
| `main_time`, `periods`, `period_time` | int, nullable | Paramètres de `byoyomi` |
| `stones_per_period` | int, nullable | `canadian` |
| `time_increment`, `initial_time`, `max_time` | int, nullable | `fischer` |
| `per_move` | int, nullable | `simple` |
| `total_time` | int, nullable | `absolute` |
| `game`, `started`, `finished`, `cancelled` | int / bool, nullable | ⚠ modifiables, voir les dangers |

✅ **Les réglages de partie sont donc ceux de l'appelant, rencontre par rencontre**, et non une configuration figée sur
la ligue. Le wiki ne documente que `handicap` et laisse croire le contraire.

✅ **La validation est réelle et croisée.** `time_control: "byoyomi"` sans `periods` répond **400** :
`{"error": "Missing parameters for byoyomi time control (periods, period_time, main_time)"}`.

✅ **Idempotent sur `league_match_id`**, avec la même sémantique que `PUT /member` : **201** à la création, **200**
ensuite, **même `id`, mêmes liens d'invitation, corps strictement identique**. Reprendre une création interrompue se
réduit donc à rejouer le `POST`.

✅ **Mais l'idempotence porte sur le payload entier, pas sur le seul `league_match_id`.** Mesuré le 10 août au soir, en
rejouant `probe_idempotence_01` champ par champ : **tout champ fourni qui diffère de la rencontre stockée est un 400**,
et le message nomme le champ.

```
{"error":"that league_match already exists, with different name"}
{"error":"that league_match already exists, with different handicap"}
{"error":"that league_match already exists, with different height"}
{"error":"that league_match already exists, with different rules"}
{"error":"that league_match already exists, with different main_time"}
{"error":"that league_match already exists, with different black_member_id"}
```

Un champ **absent** n'est en revanche pas comparé : le même `POST` sans `name` répond 200 et renvoie la rencontre avec
son nom stocké. La règle est donc « chaque champ envoyé doit correspondre », pas « le corps doit être identique ».

Trois conséquences :

- **Rejouer une création interrompue reste sûr**, à condition que le payload soit déterministe. Le nôtre l'est : les
  réglages sont des constantes, et le nom se déduit de la saison et de la session.
- **Les réglages d'une rencontre sont donc gelés pour sa durée de vie.** Changer une constante en cours de saison —
  `main_time`, la taille du plateau, le format du nom — ferait échouer en 400 tout rejeu portant sur une rencontre déjà
  créée, et pas seulement la prochaine création.
- ⚠ **Une version précédente de ce document affirmait le contraire** : que deux appels de même `league_match_id` avec des
  joueurs différents renverraient silencieusement la première rencontre. C'est faux, et dans le bon sens : OGS répond
  400 en nommant `black_member_id`. Le cas réellement silencieux est plus étroit — **deux payloads identiques**, ce qui
  arrive exactement si dev et prod apparient les mêmes joueurs, dans la même session, avec les mêmes couleurs. Les deux
  environnements se partageraient alors une seule rencontre et ses liens, sans rien voir. C'est ce qui justifie encore
  le préfixe `db.name` dans `league_match_id`.

Exemple de réponse, ✅ celle de la sonde `13688` (liens d'invitation tronqués, ce sont des secrets de joueur) :

```json
{ "id": 13688,
  "league_match_id": "probe_idempotence_01",
  "name": "Test Match Ligue #01",
  "black_member_id": "a84de96992186b8ae3528bbb083fe951",
  "black_invite": "https://online-go.com/online-league/league-player?side=black&id=<clé 22 car.>",
  "white_member_id": "ee78396dd886726c7292e259389f180a",
  "white_invite": "https://online-go.com/online-league/league-player?side=white&id=<clé 22 car.>",
  "spectator_link": "https://online-go.com/online-league/league-game/13688",
  "league": "FulguroGo",
  "rules": "japanese", "handicap": 0, "height": 19, "width": 19,
  "time_control": "byoyomi", "main_time": 2400, "periods": 5, "period_time": 30,
  "stones_per_period": null, "time_increment": null, "initial_time": null,
  "max_time": null, "per_move": null, "total_time": null,
  "game": null, "started": false, "finished": false, "cancelled": false,
  "outcome": null, "black_lost": null, "white_lost": null,
  "annulled": null, "moderator_annulled": null, "annulment_reason": null,
  "rating_complete": false, "black_member_rating": null, "white_member_rating": null }
```

`id` est un **entier**. Les deux liens joueurs portent une clé courte de 22 caractères ; le lien spectateur ne contient
que l'`id` de la rencontre, donc lui seul est publiable.

**Les neuf champs `readOnly` du résultat** — `outcome`, `black_lost`, `white_lost`, `annulled`,
`moderator_annulled`, `annulment_reason`, `rating_complete`, `black_member_rating`, `white_member_rating` — sont
écrits par OGS. Ils suffisent à connaître l'issue d'une rencontre **et son annulation** sans passer par
`/api/v1/games/{id}` ni par une ingestion de parties. ✅ Leurs types réels sont mesurés, voir « Une rencontre
terminée » plus bas — et la spec se trompe sur deux d'entre eux.

---

## `GET /matches/`

✅ Pagination DRF : `{"count": n, "next": null, "previous": null, "results": [...]}`. Paramètres `page`, `page_size`,
`ordering`.

✅ **`results` contient l'objet complet**, les 35 champs, `finished`, `started`, `game`, `outcome`, `black_lost`,
`annulled` compris. La collection donne donc tout ce que donne `GET /matches/{id}`, pour toutes les rencontres à la fois.
C'est ce qui rend un balayage en **un seul appel** possible, et le callback superflu.

✅ **N'importe quel champ du modèle est un filtre**, et la spec n'en déclare aucun — elle ne liste que `page`,
`page_size` et `ordering`. Mesuré sur une ligue ne contenant qu'une rencontre non terminée :

| Requête | `count` | Lecture |
|---|---|---|
| `?finished=true` | 0 | filtre honoré |
| `?finished=false` | 1 | filtre honoré |
| `?started=true`, `?cancelled=true` | 0 | honorés |
| `?league_match_id=probe_idempotence_01` | 1 | honoré |
| `?league_match_id=zzz_inexistant` | 0 | honoré |
| `?league_match_id__startswith=probe` | 1 | **lookup `__startswith` honoré** |
| `?league_match_id__startswith=zzz` | 0 | honoré |
| `?game__isnull=true` | 1 | lookup `__isnull` honoré |
| `?nawak=xyz` | — | **400** `{"detail":"OnlineLeagueMatches has no field named 'nawak'"}` |
| `?league_match_id__contains=…`, `?…__in=…` | — | 400, même message : les lookups sont une liste blanche |

✅ **Le point rassurant, c'est la dernière ligne** : un champ inconnu est une **erreur**, pas un filtre ignoré. Le mode de
panne redouté — construire un balayage sur `?finished=false`, voir le filtre silencieusement ignoré et croire avoir
filtré — n'existe pas. `ordering`, en revanche, est permissif : `?ordering=nawak` répond 200 et ne trie rien.

⚠ `?page=2` sur un résultat d'une seule page répond **400** `{"detail":"Invalid page."}`, pas une page vide. Une boucle
de pagination doit suivre `next` et jamais incrémenter `page` à l'aveugle.

Conséquence pour la ligue : puisque `league_match_id` est préfixé `<db.name>_<saison>_<session>_`, un
`?league_match_id__startswith=fg_prod_2026-2027_8_` rend **exactement les rencontres d'une session, d'une saison et d'un
environnement**, objets complets. Un appel par balayage, borné pour toujours — il ne grossit pas avec l'historique de la
ligue — et dev ne voit par construction que ses propres rencontres. Le préfixe, introduit pour éviter les collisions
d'identifiants, sert donc aussi de clé de requête.

`GET /matches/{match_id}` ✅ renvoie **exactement le même objet** que le `POST` — un seul modèle à mapper pour les
trois appels.

---

## La partie qu'une rencontre crée

✅ Mesuré le 11 août 2026 sur la partie issue de la rencontre `13688`, une fois lancée par les deux joueurs.

**`game` porte l'`id` de la partie OGS**, un entier, et il est **différent** de l'`id` de la rencontre :

```
rencontre 13688  ->  "game": 89632834
```

C'est ce qui fonde `gold_id = "OGS_<game>"`. Ne pas confondre les deux : `https://online-go.com/api/v1/games/13688`
existe et désigne une partie sans rapport. `game` reste `null` jusqu'à ce que les deux joueurs aient accepté, et
`started` passe alors à `true`.

**Ce que la partie déclare**, alors que rien de tout cela n'est dans le payload de création :

| Champ | Valeur | Pourquoi ça compte |
|---|---|---|
| `ranked` | ✅ **`true`** | Il n'existe aucun champ `ranked` à la création, donc c'était une inconnue. Les parties de ligue sont classées : le bonus `ranked` des maisons et `total_ranked_games` de FGC s'appliquent |
| `speed` | ✅ **`live`** | Décide de tout : `isLongGame()` exige `live`, `OgsService` écarte la correspondance et le WebSocket ne voit que le live. Une ligue en correspondance aurait été invisible du pipeline entier |
| `komi` | ⚠ **`6.50`** | Et non 7,5 comme supposé. Le jigo reste impossible — c'est le demi-point qui compte — et 6,5 passe la fenêtre `komi > 6 AND komi < 9` de FGC, mais avec 0,5 de marge au lieu de 1,5 |
| `name` | reprend le nom de la rencontre | Le `name` du payload devient le nom de la partie, comme le wiki l'annonce |
| `source` | `"play"` | |
| `time_control_parameters` | une **chaîne** contenant du JSON | `{"main_time": 2400, "period_time": 30, "periods": 5, "speed": "live", "system": "byoyomi", …}`. C'est une chaîne, pas un objet — `OgsApiGame` le mappe déjà ainsi, et le parser comme un objet lève une exception |

⚠ **Le piège des deux perdants.** Sur une partie **en cours**, l'API des parties renvoie `black_lost: true` **et**
`white_lost: true`, avec `outcome: ""` et `ended: null`. Lire les deux drapeaux sans vérifier d'abord que la partie est
terminée désigne donc deux perdants. Deux garde-fous existent déjà et ne doivent pas être retirés :
`OgsApiGame.result()` teste `outcome` en premier, et `OgsLeagueMatch.loser()` ne rend un côté que si **exactement** un
des deux a perdu.

---

## Une rencontre terminée

✅ Mesuré le 11 août 2026 sur la rencontre `13688`, dont la partie a été jouée puis **annulée**.

```json
{"finished": true, "cancelled": false,
 "outcome": "Cancellation", "black_lost": true, "white_lost": false,
 "annulled": true, "moderator_annulled": null, "annulment_reason": null,
 "rating_complete": true, "black_member_rating": 1500.0, "white_member_rating": 1500.0}
```

**Les types, enfin connus, et la spec se trompe deux fois :**

| Champ | Type réel | Ce que la spec dit |
|---|---|---|
| `outcome` | `String`, lisible par un humain — `"Cancellation"` | string ✅ |
| `black_lost`, `white_lost` | **`Boolean`** | string ❌ |
| `annulled`, `moderator_annulled` | `Boolean`, nullable | — |
| `annulment_reason` | resté `null` malgré l'annulation | string |
| `black_member_rating`, `white_member_rating` | **`Double`** (Glicko), `1500.0` | — |

⚠⚠ **Le piège : une rencontre annulée désigne quand même un vainqueur.** Celle-ci est revenue `finished: true`,
`annulled: true`, `outcome: "Cancellation"` **et** `black_lost: true, white_lost: false`. Lire les deux drapeaux sans
vérifier l'annulation d'abord transforme une partie qui ne compte pas en victoire pour blanc. C'est pourquoi
`OgsLeagueMatch.loser()` teste `isAnnulled()` **en premier** et rend `null` : la règle « une rencontre annulée n'est pas
une victoire » est appliquée au seul endroit où ces drapeaux sont lus, plutôt que laissée à la mémoire de chaque appelant.

⚠ Trois autres détails de cette réponse :

- `moderator_annulled` et `annulment_reason` sont **null** alors que `annulled` est `true`. `annulled` seul est donc le
  signal, et il ne faut pas compter sur une raison lisible — le plan la promettait, elle n'est pas venue.
- `cancelled` vaut **`false`** sur une rencontre annulée : ce champ parle d'autre chose, ne pas s'en servir pour
  détecter une annulation.
- Les deux ratings valent `1500.0`, donc **`Double` et non `Int`** — et c'est une correction, pas un détail. Gson lit
  `1500.0` dans un champ `Int` sans broncher et **lève** sur `1523.7`, en perdant **l'objet entier** et pas seulement le
  champ. Comme le client répond null ou liste vide en cas d'échec, un seul rating fractionnaire aurait rendu un balayage
  vide, silencieusement, tant qu'il restait fractionnaire. Mesuré aussi : `rating_complete` passe à `true` ici, après
  avoir valu `false` à la création puis `null` en cours de partie.

---

## `GET|PUT|PATCH /callback`

```json
{"callback_url_template": "https://<host>/<chemin>/{id}"}
```

OGS fait un `GET` sur cette URL à la fin de chaque partie, en substituant l'`id`. L'endpoint appelé **doit répondre
200**, y compris pour un identifiant inconnu : OGS teste l'URL avec `id=0` au moment de l'enregistrement.

✅ **`GET /callback` permet de lire la configuration en place sans la modifier.** À utiliser avant tout `PUT` : le
`callback_url_template` est **global à la ligue**, donc un `PUT` malencontreux coupe les callbacks de tout le monde.
Au 10 août 2026, il vaut `{"callback_url_template": null}` — aucun callback n'est enregistré.

**Sur quoi repose « global à la ligue »**, puisque tout le traitement du callback en dépend :

- Le schéma `OnlineLeagueCallback` de la spec n'a **qu'une seule propriété**, `callback_url_template`, une chaîne
  nullable de 255 caractères au plus. Il n'y a donc aucun endroit où un second template pourrait vivre.
- Rien dans la requête ne désigne un environnement : la seule clé est le couple d'en-têtes, qui identifie la ligue.
- ✅ Le `GET` mesuré renvoie bien un scalaire unique, pas une collection.

⚠ Ce qui n'est **pas** mesuré : aucun `PUT` ni `PATCH` n'a jamais été passé sur cet endpoint. « Un `PUT` depuis un poste
de dev écraserait le template de la production » est donc une déduction — la seule lecture raisonnable d'un champ scalaire
unique —, pas une observation. Elle n'a pas été vérifiée exprès : la vérifier demanderait précisément d'écraser le
template de production, et la panne serait silencieuse.

⚠ **Deux pièges de la spec sur cet endpoint.** Son bloc `security` autorise l'anonyme sur le `GET` (un `{}` dans la
liste) : c'est faux, ✅ un `GET` sans en-têtes répond **403**. Les blocs `security` de cette spec ne sont donc pas fiables
— classique d'un schéma généré par DRF quand les classes de permission sont personnalisées. Et le chemin y figure comme
`/api/v1/online_league/{var}callback`, avec un paramètre `{var}` requis et sans séparateur : c'est un artefact, `{var}`
vide est ce qui fonctionne (✅ `/xcallback` répond 404).

---

## `POST|DELETE /leagues/`

```json
{"name": "<nom>", "auth_key": "<clé>"}
```

Une ligue **peut** être créée par l'API (spec, non essayé), contrairement à ce que le wiki laisse entendre en
renvoyant vers anoek ou GreenAsJade. `member_count` est annoncé requis mais `readOnly`, ce qui est une bizarrerie de
sérialisation DRF. `DELETE /leagues/` existe aussi.

Non essayé, volontairement : créer ou supprimer une ligue avec la clé d'une ligue existante est le genre d'appel dont
on ne veut pas découvrir la portée par l'expérience.

---

## Les dangers

Trois endpoints à ne jamais appeler sans savoir précisément ce qu'ils font, tous absents du wiki :

1. ⚠ **`DELETE /matches/`** — sur la **collection**, et **sans corps de requête** d'après la spec. Rien ne dit ce
   qu'il supprime. L'hypothèse la plus naturelle, pour une collection, est « toutes les rencontres de la ligue ».
   **Ne pas essayer, ne pas coder.** À noter que `DELETE /matches/{match_id}` sur l'élément, lui, répond ✅ **405** :
   la suppression d'une rencontre précise n'existe pas.
2. ⚠ **`PUT /matches/`** — sur la collection, avec le même corps qu'un `POST`. Permet vraisemblablement de modifier
   une rencontre existante, y compris ses champs `started`, `finished` et `cancelled`, qui sont modifiables. C'est
   probablement la façon d'annuler une rencontre — et probablement aussi celle de fausser un résultat.
3. ⚠ **`DELETE /leagues/`** — voir plus haut.

Une rencontre créée est donc, en pratique et sauf à explorer `PUT /matches/`, **définitive** : `/matches/{match_id}`
n'accepte que la lecture.

---

## Ce qui reste inconnu

- La forme d'`outcome` sur une partie **gagnée** : `"Cancellation"` est la seule valeur vue. Une victoire normale porte
  vraisemblablement quelque chose comme `"Resignation"` ou `"12.5 points"`, mais ce n'est pas mesuré. Sans importance
  pour la ligue, qui lit `black_lost` / `white_lost` et non `outcome`.
- Quand `pending_rating_change` est consommé, et si un `PUT /member` rejoué après la liaison peut réinitialiser le
  `league_rating` d'un joueur en cours de saison.
- Ce que valent `annulment_reason` et `moderator_annulled` sur une annulation **par un modérateur** — sur celle du
  11 août, faite autrement, les deux sont restés `null`.
- Ce que `game` contient exactement, et le `speed` que la partie créée déclare — ce qui décide si une partie de ligue
  est vue comme `live` par les consommateurs de `ogs_games`.
- Ce que fait `PUT /matches/`, et sur quelle clé il retrouve la rencontre.
- Ce que supprime `DELETE /matches/`.
- Si `PATCH /callback` diffère de `PUT`.

---

## Journal de la sonde du 10 août 2026

Exécutée contre la ligue de production `FulguroGo`, qui ne contenait aucune rencontre avant.

| # | Appel | Résultat |
|---|---|---|
| 1 | `PUT /member/ee78396dd886726c7292e259389f180a` (Drooxi) | 201 |
| 2 | `PUT /member/a84de96992186b8ae3528bbb083fe951` (JudasImov) | 201 |
| 3 | `PUT /member/…` Drooxi, à nouveau | **200**, corps identique |
| 4 | `POST /matches/` sans `periods` | **400**, message de validation |
| 5 | `POST /matches/` complet | **201**, rencontre `13688` |
| 6 | `POST /matches/` identique | **200**, `13688`, corps identique |
| 7 | `GET /matches/13688` | 200, même objet que le `POST` |
| 8 | `GET /matches/` | `count: 1` |
| 9 | `GET /matches/?league_match_id=zzz_inexistant` | `count: 0` |
| 10 | `GET /matches/13688` sans en-têtes | **403** |
| 11 | `DELETE /matches/13688` | **405** |
| 12 | `DELETE /match/13688` | **401** |
| 13 | `OPTIONS` sur les quatre endpoints | cf. la surface plus haut |
| 14 | `GET /callback` | `{"callback_url_template": null}` |

**Ce que la sonde laisse derrière elle, définitivement** : deux membres — Drooxi et JudasImov, dont les `member_id`
sont ceux que la production utilisera — et la rencontre `13688`, `probe_idempotence_01`, jamais jouée. Les deux
`member_id` dérivent de `league.member.salt`, donc ils resteront valides tant que ce sel ne change pas.
