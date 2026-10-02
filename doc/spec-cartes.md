# Cartes à collectionner — spécification

Ce document décrit le système de cartes à collectionner de FulguroGo : contenu, tirage, économie, équilibre, et ce que
sa mise en place demande à fulguro-server. C'est la base du futur plan d'implémentation.

Les chiffres d'équilibre viennent d'un calcul exact quand il existe, sinon d'une simulation Monte-Carlo à graine fixe
(1 000 collections simulées pour la durée de complétion, 500 pour la comparaison des mécanismes du §8.3). L'activité
des joueurs est mesurée sur la base de dev (§8.1).

---

## 1. Principe

- Un album d'étiquettes thématique Go. Pas de gameplay compétitif ni de carte « overpower » : l'objectif est la
  collection.
- L'album est **permanent** : une collection se garde d'une saison à l'autre, et une **extension** par an ajoute des
  cartes.
- La progression s'affiche en **taux de complétion**, global, par rareté et par catégorie. Il n'y a pas de badges.
- Les points qui achètent les packs se gagnent **uniquement en jouant des parties gold** (§7.1).
- Objectif de durée : un joueur actif médian complète l'album en **trois ans et neuf mois** (45 mois), et le nombre
  de packs qu'il faut pour finir reste **aléatoire** : un joueur malchanceux en ouvre près de deux fois plus qu'un
  chanceux.

## 2. Raretés et effectifs

| Rareté       | Couleur | Symbole | Cartes  | % du set |
|--------------|---------|---------|--------:|---------:|
| Commune      | Gris    | ⚪      | 127     | 47,0 %   |
| Inhabituelle | Vert    | 🟢      | 69      | 25,6 %   |
| Rare         | Bleu    | 🔵      | 37      | 13,7 %   |
| Épique       | Violet  | 🟣      | 23      | 8,5 %    |
| Mythique     | Gold    | 🟡      | 14      | 5,2 %    |
| **Total**    |         |         | **270** |          |

Les effectifs ne sont pas des quotas. Un joueur va dans la rareté que lui donne son palmarès (§5) et le tirage s'adapte
seul, grâce aux poids par carte (§3).

## 3. Tirage

### 3.1 Poids par carte

Chaque carte porte un poids fixé par sa rareté, et le tirage choisit **une carte** parmi l'ensemble, au prorata des
poids. On ne tire pas d'abord une couleur.

| Rareté | Poids |
|--------|------:|
| Gris   | 10    |
| Vert   | 5     |
| Bleu   | 3     |
| Violet | 2     |
| Gold   | 1     |

Ce choix a deux conséquences :

- **La rareté par carte suit toujours l'ordre des couleurs**, quels que soient les effectifs : une Gold donnée est
  toujours plus rare qu'une Violette donnée, elle-même plus rare qu'une Bleue donnée.
- **Une extension ne rend pas les cartes existantes plus rares les unes par rapport aux autres.** En revanche, la part
  de chaque couleur dans un pack dépend des effectifs, et le temps de complétion s'allonge avec chaque carte ajoutée.
  Les chiffres de ce document valent pour le set de 270 cartes et sont à refaire à chaque extension.

### 3.2 Structure d'un pack

Un pack contient **5 cartes** :

- **4 slots standards**, tirés indépendamment parmi toutes les cartes ;
- **1 slot Vert+**, tiré parmi les cartes Vert ou mieux au prorata des poids. Si la rareté tirée est Violet ou Gold, la
  carte est choisie parmi celles de cette rareté **que le joueur n'a pas encore** (anti-doublon), et n'importe laquelle
  de la rareté s'il les a toutes. Sur Vert ou Bleu, le double est possible.

L'ordre d'affichage des cartes est mélangé.

L'anti-doublon est réservé aux Violettes et aux Gold. Étendu à tout le slot, il complète les Vertes vers le 70ᵉ pack,
puis ne donne plus que des doubles, pendant que les Grises restent la rareté la plus lente à finir. Les Grises, Vertes
et Bleues manquantes sont l'affaire du pity sur carte nouvelle (§3.4).

Avec les effectifs actuels, les poids donnent :

| Rareté | Part d'un slot standard | Part du slot Vert+ |
|--------|------------------------:|-------------------:|
| Gris   | 71,1 %                  | —                  |
| Vert   | 19,3 %                  | 66,9 %             |
| Bleu   | 6,2 %                   | 21,5 %             |
| Violet | 2,6 %                   | 8,9 %              |
| Gold   | 0,8 %                   | 2,7 %              |

### 3.3 Ce que contient un pack

| Rareté | Cartes par pack | Au moins une dans le pack | Une carte donnée (hors anti-doublon et pity) |
|--------|----------------:|--------------------------:|---------------------------------------------:|
| Gris   | 2,84            | 99,3 %                    | tous les 45 packs                            |
| Vert   | 1,44            | 86,0 %                    | tous les 48 packs                            |
| Bleu   | 0,46            | 39,3 %                    | tous les 80 packs                            |
| Violet | 0,19            | 17,9 %                    | tous les 120 packs                           |
| Gold   | 0,06            | 5,7 %                     | tous les 239 packs                           |

En moyenne, une Bleue tous les 2 packs, une Violette tous les 5 packs, une Gold tous les 17 packs.

### 3.4 Pity sur carte nouvelle

Si les **2 derniers packs** ouverts par le joueur ne lui ont apporté aucune carte nouvelle, le premier slot standard du
pack suivant donne une carte **Grise, Verte ou Bleue que le joueur n'a pas**, choisie parmi les manquantes au prorata
des poids. S'il les a toutes, le slot reste un slot standard.

- Le compteur est propre à chaque joueur et court d'une ouverture à l'autre. Un pack qui apporte au moins une carte
  nouvelle, quelle qu'elle soit, le remet à zéro.
- Les Violettes et les Gold en sont exclues exprès. Un pity qui puise dans toutes les cartes manquantes finit par
  donner lui-même les dernières Gold, et le nombre de packs pour finir devient presque fixe (±8 % autour de la médiane,
  quel que soit le seuil). Les exclure laisse la fin de collection au hasard : c'est voulu, et le recyclage (§7.2)
  amortit la malchance.
- Le seuil compte peu : à 3 packs au lieu de 2, la médiane passe de 304 à 302 packs.

Sur une collection complète, le pity donne en moyenne 17 cartes, soit 1,2 % des cartes tirées.

### 3.5 Pity Gold et Violet

Un filet de sécurité, réglé pour ne se déclencher que dans environ 5 % des cas, et qui donne toujours une carte **que
le joueur n'a pas**.

- **Gold** : après **255 cartes** tirées sans Gold, la carte suivante est une Gold manquante (déclenchement : ~4,9 %).
- **Violet** : après **60 cartes** tirées sans Violette ni Gold, la carte suivante est une Violette manquante
  (déclenchement : ~4,4 %).

Règles de détail :

- Les compteurs courent d'un pack à l'autre et sont propres à chaque joueur.
- Une Gold remet les deux compteurs à zéro, une Violette seulement le compteur Violet. Un double compte aussi.
- Le pity s'applique au slot qui arrive, standard ou Vert+. Si les deux sont dus en même temps, la Gold passe
  d'abord. Il passe aussi avant le pity sur carte nouvelle s'ils tombent sur le même slot.
- Si le joueur possède déjà toutes les cartes de la rareté, le pity donne une carte quelconque de cette rareté.

Sur une collection complète, 0,28 % des cartes tirées viennent de ce pity.

Pas d'argent réel : `kotlin.random.Random` suffit, un RNG cryptographique n'apporte rien. Le tirage se fait côté
serveur, jamais sur le site.

## 4. Catalogue

Chaque carte porte :

| Champ | Rôle |
|-------|------|
| `id` | Numéro de la carte dans l'album, entier à partir de 1 (§6.2). |
| `slug` | Identifiant stable, jamais modifié, même si le nom est corrigé : c'est lui que référencent les collections. |
| `name` | Nom affiché. Deux cartes peuvent porter le même (« Tengen » Ouverture et « Tengen » Tournoi). |
| `category` | Catégorie d'album. |
| `rarity` | Rareté, qui fixe le poids et la valeur de recyclage. |
| `description` | Texte de la carte. C'est elle qui distingue les homonymes. |
| visuel | Côté site. Les joueurs réels sont illustrés, pas photographiés. |

Chaque carte est unique dans l'album : un exemplaire suffit à la compter.

## 5. Classement des joueurs

La rareté d'un joueur part de son **palmarès**, quelle que soit l'époque. La grille ci-dessous donne le repère ; ce
n'est pas un barème, et un joueur peut être classé un cran au-dessus ou au-dessous, selon sa place dans l'histoire du
go. Un titre mondial isolé, par exemple, ne fait pas à lui seul une Bleue.

| Rareté | Repère |
|--------|--------|
| Gris   | Professionnel ou amateur fort, sans titre majeur. |
| Vert   | Au moins un titre national majeur. |
| Bleu   | Un titre mondial, ou domination d'un circuit national. |
| Violet | Plusieurs titres mondiaux, ou domination nationale doublée d'un titre mondial. |
| Gold   | Domination de son époque. |

Équivalences :

- **Titres féminins**, décalés d'un cran : titre féminin national = Vert, titre féminin mondial = Bleu, plusieurs
  titres féminins mondiaux = Violet. Un titre open gagné par une joueuse compte comme un titre open.
- **Titres occidentaux** : un titre continental (champion d'Europe, titre professionnel européen ou nord-américain)
  vaut un titre national majeur, soit Vert. Plusieurs titres continentaux donnent Bleu, jamais plus.
- **Historiques** (avant les titres modernes) : Meijin-Godokoro ou chef d'une des quatre maisons vaut un titre
  mondial (Bleu), et une domination longue donne Violet. Dosaku, Shusaku et Go Seigen sont Gold par domination de leur
  époque.

Écarts voulus par rapport au repère :

| Joueur | Rareté | Repère | Palmarès en cause |
|--------|--------|--------|-------------------|
| Liao Yuanhe | Gris | Bleu | Une Samsung Cup (2025) |
| Dang Yifei | Vert | Bleu | Une LG Cup (2017) |
| Wang Xinghao | Vert | Bleu | Une LG Cup (2026) |
| Byun Sangil | Vert | Violet | Une Chunlan Cup (2023) et une LG Cup (2025) |
| Park Younghun | Vert | Violet | Deux Fujitsu Cup |
| Xie Ke | Bleu | Vert | Aucun titre mondial, finaliste de la MLily Cup et de l'Ing Cup |
| Artem Kachanovskyi | Gris | Vert | Champion de la ligue professionnelle européenne (2020) |
| Cho Seungah | Gris | Vert | Une Nanseolheon Cup (2021), titre féminin national |
| Oh Jeonga | Gris | Vert | Une Dasan Cup (2017), titre féminin national |
| Suzuki Ayumi | Gris | Vert | Un Kisei féminin (2020) |
| Tang Jiawen | Gris | Vert | Un Guoshou féminin (2024) |

Le classement de la liste (§6) a été vérifié joueur par joueur.

## 6. Liste des cartes

### 6.1 Catégories

| Catégorie | Contenu | Gris | Vert | Bleu | Violet | Gold | Total |
|-----------|---------|-----:|-----:|-----:|-------:|-----:|------:|
| Joueurs | Joueurs professionnels et amateurs, classés au palmarès (§5) | 30 | 34 | 21 | 14 | 9 | **108** |
| Communauté | Maisons, compétitions, vainqueurs de la FGC, émissions, événements, lieux du lore et figures de la communauté | 16 | 14 | 12 | 6 | 4 | **52** |
| Tournois pro | Tournois mondiaux, grands titres japonais, coréens, chinois et taïwanais, tournois rapides télévisés | 19 | 12 | — | — | — | **31** |
| Formes complexes | Formes de plusieurs pierres, bonnes ou mauvaises | 8 | 2 | — | 1 | — | **11** |
| Meta | Concepts et vocabulaire du jeu | 10 | — | — | — | — | **10** |
| Fuseki | Stratégies d'ouverture, classiques ou non | 5 | 2 | 2 | — | — | **9** |
| Institutions | Fédérations, associations et organisation du go | 9 | — | — | — | — | **9** |
| Formes simples | Coups élémentaires entre deux pierres | 8 | — | — | — | — | **8** |
| Parties historiques | Parties célèbres de l'histoire du go, du XVIIIᵉ siècle à AlphaGo | — | 3 | 2 | 2 | 1 | **8** |
| Ouvertures | Points de coin et premiers coups | 7 | — | — | — | — | **7** |
| Matériel | Objets du joueur de go | 6 | — | — | — | — | **6** |
| Variantes | Autres façons de jouer au go | 4 | 2 | — | — | — | **6** |
| Serveurs | Serveurs de go en ligne | 5 | — | — | — | — | **5** |
| **Total** | | **127** | **69** | **37** | **23** | **14** | **270** |

Joueurs et Communauté sont les deux seules catégories présentes dans toutes les raretés ; six catégories n'ont que des
cartes Gris.

### 6.2 Cartes

Toutes les cartes, classement et description, ont été vérifiées.

Les deux dernières colonnes renvoient aux pages Wikipedia (en français, à défaut en anglais) et Sensei's Library qui
décrivent le sujet de la carte. « § » signale un lien vers une section d'une page plus large, faute de page dédiée.

Chaque carte porte un `id` entier, de 1 à 270, attribué en triant les cartes par rareté décroissante (Gold d'abord),
puis, dans chaque rareté, par catégorie et par titre, dans l'ordre alphabétique sans tenir compte des accents.

#### 🟡 Gold — 14 cartes

| Id | Catégorie | Titre | Rareté | Description | Wikipedia | Sensei's Library |
|---:|-----------|-------|--------|-------------|-----------|-------------------|
| 1 | Communauté | Gold l'Incréé | Gold | L'entité divine qui façonna le premier plateau et posa la première pierre. Sa disparition brisa l'unité des Quatre. |  |  |
| 2 | Communauté | HisokaH l'Hermite | Gold | Le maître de la communauté, son professeur et son créateur. |  |  |
| 3 | Communauté | HisokaH le Lutin | Gold | Le maître de la communauté, sa véritable identité. |  |  |
| 4 | Communauté | JTGO | Gold | Feu l'émission vidéo sur l'actualité du go. |  |  |
| 5 | Joueurs | Cho Chikun | Gold | Le joueur le plus titré de l'histoire du go japonais, l'un des « Six Supers ». Professionnel coréen affilié au Japon depuis 1968, as du tesuji. | [fr](https://fr.wikipedia.org/wiki/Cho_Chihun) | [SL](https://senseis.xmp.net/?ChoChikun) |
| 6 | Joueurs | Cho Hunhyun | Gold | Premier vainqueur de l'Ing Cup, en 1989, dominateur du go coréen pendant des décennies. | [fr](https://fr.wikipedia.org/wiki/Cho_Hunhyun) | [SL](https://senseis.xmp.net/?ChoHunhyun) |
| 7 | Joueurs | Go Seigen | Gold | Tenu pour le plus grand joueur du XXᵉ siècle, invaincu dans ses jubango, co-inventeur du shinfuseki. | [fr](https://fr.wikipedia.org/wiki/Go_Seigen) | [SL](https://senseis.xmp.net/?GoSeigen) |
| 8 | Joueurs | Honinbo Dosaku | Gold | Le « saint du go » du XVIIᵉ siècle, qui posa les bases de la théorie moderne. | [fr](https://fr.wikipedia.org/wiki/Hon'inb%C5%8D_D%C5%8Dsaku) | [SL](https://senseis.xmp.net/?HoninboDosaku) |
| 9 | Joueurs | Honinbo Shusaku | Gold | Invaincu en dix-neuf parties du Château, auteur du « coup aux oreilles rouges ». | [fr](https://fr.wikipedia.org/wiki/Hon'inb%C5%8D_Sh%C5%ABsaku) | [SL](https://senseis.xmp.net/?HoninboShusaku) |
| 10 | Joueurs | Ke Jie | Gold | Professionnel chinois, plusieurs fois champion du monde, adversaire d'AlphaGo en 2017. | [fr](https://fr.wikipedia.org/wiki/Ke_Jie) | [SL](https://senseis.xmp.net/?KeJie) |
| 11 | Joueurs | Lee Changho | Gold | « Le Bouddha de pierre », dominateur mondial des années 1990, élève de Cho Hunhyun. | [fr](https://fr.wikipedia.org/wiki/Lee_Chang-ho) | [SL](https://senseis.xmp.net/?LeeChangho) |
| 12 | Joueurs | Lee Sedol | Gold | Dominateur des années 2000, seul humain à avoir battu AlphaGo en match officiel. | [fr](https://fr.wikipedia.org/wiki/Lee_Sedol) | [SL](https://senseis.xmp.net/?LeeSedol) |
| 13 | Joueurs | Shin Jinseo | Gold | Numéro un mondial des années 2020, plusieurs fois champion du monde. | [fr](https://fr.wikipedia.org/wiki/Shin_Jinseo) | [SL](https://senseis.xmp.net/?ShinJinseo) |
| 14 | Parties historiques | AlphaGo vs Lee Sedol (YiSeTol) | Gold | Le match de mars 2016, gagné 4-1 par AlphaGo. Lee Sedol remporta la quatrième partie grâce à un coup inattendu au centre, le 78ᵉ. | [fr](https://fr.wikipedia.org/wiki/Match_AlphaGo_-_Lee_Sedol) |  |

#### 🟣 Violet — 23 cartes

| Id | Catégorie | Titre | Rareté | Description | Wikipedia | Sensei's Library |
|---:|-----------|-------|--------|-------------|-----------|-------------------|
| 15 | Communauté | Examen Hunter | Violet | Bâtiment en ruine. On y décernait autrefois des titres aux joueurs qui s'illustraient sur le goban. |  |  |
| 16 | Communauté | Mode Étude | Violet | Comprendre l'intérêt de chaque coup d'une séquence. HisokaH l'explique pour tous les niveaux. |  |  |
| 17 | Communauté | Mont Tengen | Violet | Le sommet où se dressait le Temple des 361 Voies, là où le ciel touche la terre. |  |  |
| 18 | Communauté | Stage d'Automne 2016 | Violet | Le premier stage organisé par FulguroGo, en 2016 : une semaine bien chargée, avec des activités du matin au soir. |  |  |
| 19 | Communauté | Temple des 361 Voies | Violet | Le temple des Synalithes, qui jouaient pour comprendre et non pour vaincre, avant la disparition de Gold. |  |  |
| 20 | Communauté | Tour céleste | Violet | Tour délabrée. Jadis théâtre d'affrontements entre les joueurs qui voulaient en atteindre le sommet. |  |  |
| 21 | Formes complexes | B2 bomber | Violet | La surconcentration faite forme : beaucoup de pierres, presque aucune efficacité. |  | [SL](https://senseis.xmp.net/?B2Bomber) |
| 22 | Joueurs | Choi Jeong | Violet | Professionnelle coréenne, reine des tournois féminins mondiaux, première femme en finale de la Samsung Cup. | [fr](https://fr.wikipedia.org/wiki/Choi_Jeong) | [SL](https://senseis.xmp.net/?ChoiJeong) |
| 23 | Joueurs | Fujisawa Hideyuki | Violet | Légende japonaise, six fois Kisei d'affilée, génie de l'ouverture, connu aussi sous le nom de Fujisawa Shuko. | [fr](https://fr.wikipedia.org/wiki/Fujisawa_Hideyuki) | [SL](https://senseis.xmp.net/?FujisawaHideyuki) |
| 24 | Joueurs | Gu Li | Violet | Professionnel chinois depuis 1994, plusieurs fois champion du monde, grand rival de Lee Sedol. | [fr](https://fr.wikipedia.org/wiki/Gu_Li) | [SL](https://senseis.xmp.net/?GuLi) |
| 25 | Joueurs | Honinbo Shusai | Violet | Dernier Honinbo héréditaire, qui céda le titre pour en faire un tournoi. Sa partie d'adieu inspira *Le Maître ou le tournoi de go* de Kawabata. | [fr](https://fr.wikipedia.org/wiki/Hon'inb%C5%8D_Sh%C5%ABsai) | [SL](https://senseis.xmp.net/?HoninboShusai) |
| 26 | Joueurs | Honinbo Shuwa | Violet | Chef de la maison Honinbo au XIXᵉ siècle, dont Shusaku fut l'héritier désigné. | [en](https://en.wikipedia.org/wiki/Hon'inb%C5%8D_Sh%C5%ABwa) | [SL](https://senseis.xmp.net/?HoninboShuwa) |
| 27 | Joueurs | Ichiriki Ryo | Violet | Professionnel japonais, vainqueur de l'Ing Cup 2023, l'un des meilleurs joueurs japonais du XXIᵉ siècle. | [fr](https://fr.wikipedia.org/wiki/Ichiriki_Ryo) | [SL](https://senseis.xmp.net/?IchirikiRyo) |
| 28 | Joueurs | Inoue Genan Inseki | Violet | Chef de la maison Inoue à l'époque d'Edo, adversaire de Shusaku dans la partie du « coup aux oreilles rouges ». | [fr](https://fr.wikipedia.org/wiki/Inoue_Genan_Inseki) | [SL](https://senseis.xmp.net/?InoueGenanInseki) |
| 29 | Joueurs | Kim Eunji | Violet | Professionnelle coréenne, prodige des tournois féminins. | [fr](https://fr.wikipedia.org/wiki/Kim_Eunji) | [SL](https://senseis.xmp.net/?KimEunji) |
| 30 | Joueurs | Nie Weiping | Violet | Le « saint du go », héros chinois qui redynamisa le go en Chine dans les années 1980. Il fonda la Nie Weiping Go Academy dans les années 1990. | [fr](https://fr.wikipedia.org/wiki/Nie_Weiping) | [SL](https://senseis.xmp.net/?NieWeiping) |
| 31 | Joueurs | Park Junghwan | Violet | Professionnel coréen depuis 2006, plusieurs fois champion du monde, 9ᵉ dan à 17 ans. | [fr](https://fr.wikipedia.org/wiki/Park_Junghwan) | [SL](https://senseis.xmp.net/?ParkJunghwan) |
| 32 | Joueurs | Rui Naiwei | Violet | Première femme à remporter un grand titre open, le Guksu coréen, et reine des tournois féminins mondiaux. | [fr](https://fr.wikipedia.org/wiki/Rui_Naiwei) | [SL](https://senseis.xmp.net/?RuiNaiwei) |
| 33 | Joueurs | Sakata Eio | Violet | Dominateur du go japonais des années 1960, surnommé « le Rasoir ». | [fr](https://fr.wikipedia.org/wiki/Sakata_Eio) | [SL](https://senseis.xmp.net/?SakataEio) |
| 34 | Joueurs | Takemiya Masaki | Violet | Professionnel japonais depuis 1965, l'un des « Six Supers », père du « go cosmique » tourné vers le centre. Excellent joueur de backgammon. | [fr](https://fr.wikipedia.org/wiki/Takemiya_Masaki) | [SL](https://senseis.xmp.net/?TakemiyaMasaki) |
| 35 | Joueurs | Yoda Norimoto | Violet | Professionnel japonais depuis 1980, surnommé « Tigre Yoda » et « héros du combat acharné ». Vainqueur de plusieurs grands titres et de la première Samsung Cup. | [fr](https://fr.wikipedia.org/wiki/Norimoto_Yoda) | [SL](https://senseis.xmp.net/?YodaNorimoto) |
| 36 | Parties historiques | Blood Vomiting Game | Violet | Honinbo Jowa contre Akaboshi Intetsu, élève d'Inoue Genan Inseki, en 1835. Jowa aurait reçu trois coups de fantômes ; Intetsu, malade, cracha du sang à la fin de la partie et mourut peu après. | [en](https://en.wikipedia.org/wiki/Blood-vomiting_game) | [SL](https://senseis.xmp.net/?BloodVomitingGame) |
| 37 | Parties historiques | Ear-Reddening Game | Violet | Shusaku, 17 ans, contre Inoue Genan Inseki, en 1846. Au coup 127, les oreilles de Genan rougirent : un médecin qui suivait la partie sut alors qu'il allait perdre. | [en](https://en.wikipedia.org/wiki/Ear-reddening_game) | [SL](https://senseis.xmp.net/?EarReddeningGame) |

#### 🔵 Bleu — 37 cartes

| Id | Catégorie | Titre | Rareté | Description | Wikipedia | Sensei's Library |
|---:|-----------|-------|--------|-------------|-----------|-------------------|
| 38 | Communauté | Cassis0 le Poulpe Impérial | Bleu | Vainqueur de la première FGC, en 2018, alors jouée en une seule catégorie. |  |  |
| 39 | Communauté | Deodred la Marmotte Impériale | Bleu | Vainqueur de la FGC 2019, catégorie libre. |  |  |
| 40 | Communauté | Échelle et Ligue Grottesque 2017 | Bleu | L'événement Grottesque de 2017 : des animaux à cinq pattes et des affrontements dantesques. Qui était là ? |  |  |
| 41 | Communauté | Hikaru no Go Series | Bleu | Les parties de Hikaru no Go sont pour la plupart tirées de vraies parties professionnelles, et HisokaH les analyse. |  |  |
| 42 | Communauté | R0n1n le Loup Impérial | Bleu | Vainqueur de la FGC 2022, catégorie libre. |  |  |
| 43 | Communauté | Rikikilord l'Ours Impérial | Bleu | Vainqueur de la FGC 2026, catégorie libre. |  |  |
| 44 | Communauté | SilverOreo la Salamandre Impériale | Bleu | Vainqueur de la FGC 2020, catégorie libre. |  |  |
| 45 | Communauté | SilverOreo le Hérisson Impérial | Bleu | Vainqueur de la FGC 2021, catégorie libre. |  |  |
| 46 | Communauté | Sun Tzu la Chauve-Souris Impériale | Bleu | Vainqueur de la FGC 2023, catégorie libre. |  |  |
| 47 | Communauté | Tilwen le Mammouth Impérial | Bleu | Vainqueur de la FGC 2025, catégorie libre. |  |  |
| 48 | Communauté | Tilwen le Papillon Impérial | Bleu | Vainqueur de la FGC 2024, catégorie libre. |  |  |
| 49 | Communauté | VS Fighting | Bleu | HisokaH joue au go et commente ses propres parties. |  |  |
| 50 | Fuseki | Anar | Bleu | Le fuseki trollesque de la communauté : Tengen, un coup sur la colonne R, puis O11. T, R, O11 : TROLL. |  |  |
| 51 | Fuseki | Mirror go | Bleu | Blanc imite chaque coup de Noir en symétrie centrale, jusqu'à ce que Noir brise le miroir. | [en](https://en.wikipedia.org/wiki/Mirror_Go) | [SL](https://senseis.xmp.net/?MirrorGo) |
| 52 | Joueurs | Chang Hao | Bleu | Professionnel chinois depuis 1986, plusieurs fois champion du monde dans les années 2000. | [fr](https://fr.wikipedia.org/wiki/Chang_Hao_%28joueur_de_go%29) | [SL](https://senseis.xmp.net/?ChangHao) |
| 53 | Joueurs | Cho U | Bleu | Professionnel taïwanais de la Nihon Ki-in, dominateur des années 2000, avec 38 titres nationaux. | [fr](https://fr.wikipedia.org/wiki/Cho_U) | [SL](https://senseis.xmp.net/?ChoU) |
| 54 | Joueurs | Choi Cheolhan | Bleu | Professionnel coréen depuis 1997, vainqueur de l'Ing Cup en 2009. Surnommé « The Viper » pour son style agressif. | [fr](https://fr.wikipedia.org/wiki/Choi_Cheol-han) | [SL](https://senseis.xmp.net/?ChoiCheolhan) |
| 55 | Joueurs | Ding Hao | Bleu | Professionnel chinois depuis 2013, double vainqueur de la Samsung Cup. | [fr](https://fr.wikipedia.org/wiki/Ding_Hao) | [SL](https://senseis.xmp.net/?DingHao) |
| 56 | Joueurs | Fan Hui | Bleu | Trois fois champion d'Europe, premier professionnel battu par AlphaGo, en 2015. | [fr](https://fr.wikipedia.org/wiki/Fan_Hui) | [SL](https://senseis.xmp.net/?FanHui) |
| 57 | Joueurs | Fan Tingyu | Bleu | Professionnel chinois depuis 2009, vainqueur de l'Ing Cup en 2013. | [en](https://en.wikipedia.org/wiki/Fan_Tingyu) | [SL](https://senseis.xmp.net/?FanTingyu) |
| 58 | Joueurs | Gu Zihao | Bleu | Professionnel chinois depuis 2010, vainqueur de la Samsung Cup en 2017. | [fr](https://fr.wikipedia.org/wiki/Gu_Zihao) | [SL](https://senseis.xmp.net/?GuZihao) |
| 59 | Joueurs | Ilya Shikshin | Bleu | Joueur russe, professionnel depuis 2015, plusieurs fois champion d'Europe, amateur puis professionnel. | [fr](https://fr.wikipedia.org/wiki/Ilya_Shikshin) | [SL](https://senseis.xmp.net/?IlyaShikshin) |
| 60 | Joueurs | Iyama Yuta | Bleu | Seul joueur à avoir détenu les sept grands titres japonais à la fois, et par deux fois. | [fr](https://fr.wikipedia.org/wiki/Iyama_Y%C5%ABta) | [SL](https://senseis.xmp.net/?IyamaYuta) |
| 61 | Joueurs | Jiang Weijie | Bleu | Professionnel chinois depuis 2005, vainqueur de la LG Cup en 2012. | [en](https://en.wikipedia.org/wiki/Jiang_Weijie) | [SL](https://senseis.xmp.net/?JiangWeijie) |
| 62 | Joueurs | Kang Dongyun | Bleu | Professionnel coréen depuis 2002, champion du monde en 2009 en battant Lee Changho. | [en](https://en.wikipedia.org/wiki/Kang_Dong-yun) | [SL](https://senseis.xmp.net/?KangDongyun) |
| 63 | Joueurs | Kato Masao | Bleu | Professionnel japonais, l'un des « Six Supers », surnommé « le Tueur » pour son jeu d'attaque, dominateur du début des années 1980. | [fr](https://fr.wikipedia.org/wiki/Kato_Masao) | [SL](https://senseis.xmp.net/?KatoMasao) |
| 64 | Joueurs | Kobayashi Koichi | Bleu | Dominateur du go japonais à la fin des années 1980, l'un des « Six Supers » et rival de Cho Chikun. | [fr](https://fr.wikipedia.org/wiki/K%C5%8Dichi_Kobayashi) | [SL](https://senseis.xmp.net/?KobayashiKoichi) |
| 65 | Joueurs | Ma Xiaochun | Bleu | Professionnel chinois, champion du monde dans les années 1990, premier Chinois à remporter un titre international. | [fr](https://fr.wikipedia.org/wiki/Ma_Xiaochun) | [SL](https://senseis.xmp.net/?MaXiaochun) |
| 66 | Joueurs | Otake Hideo | Bleu | Professionnel japonais de 1956 à 2021, l'un des « Six Supers », surnommé « l'esthète du go » pour son jeu élégant. | [fr](https://fr.wikipedia.org/wiki/Otake_Hideo) | [SL](https://senseis.xmp.net/?OtakeHideo) |
| 67 | Joueurs | Rin Kaiho | Bleu | Professionnel taïwanais de la Nihon Ki-in, l'un des « Six Supers », surnommé « l'homme aux deux ventres » pour sa résilience. | [fr](https://fr.wikipedia.org/wiki/Rin_Kaiho) | [SL](https://senseis.xmp.net/?RinKaiho) |
| 68 | Joueurs | Shin Minjun | Bleu | Professionnel coréen depuis 2012, double vainqueur de la LG Cup. Souvent comparé à Shin Jinseo, son alter ego, devenu pro lors du même tournoi. | [fr](https://fr.wikipedia.org/wiki/Shin_Minjun) | [SL](https://senseis.xmp.net/?ShinMinjun) |
| 69 | Joueurs | Takagawa Kaku | Bleu | Neuf fois Honinbo d'affilée, dans les années 1950. | [fr](https://fr.wikipedia.org/wiki/Kaku_Takagawa) | [SL](https://senseis.xmp.net/?TakagawaKaku) |
| 70 | Joueurs | Xie Ke | Bleu | Professionnel chinois depuis 2013. | [en](https://en.wikipedia.org/wiki/Xie_Ke) | [SL](https://senseis.xmp.net/?XieKe) |
| 71 | Joueurs | Yang Dingxin | Bleu | Professionnel chinois depuis 2008, vainqueur de la LG Cup en 2019. | [fr](https://fr.wikipedia.org/wiki/Yang_Dingxin) | [SL](https://senseis.xmp.net/?YangDingxin) |
| 72 | Joueurs | Yu Zhiying | Bleu | Professionnelle chinoise depuis 2010, trois fois vainqueure de la Senko Cup. | [en](https://en.wikipedia.org/wiki/Yu_Zhiying) | [SL](https://senseis.xmp.net/?YuZhiying) |
| 73 | Parties historiques | Atomic Bomb Game | Bleu | Hashimoto Utaro contre Iwamoto Kaoru, en 1945, pour le titre de Honinbo. Le 6 août, la bombe d'Hiroshima, tombée à une dizaine de kilomètres, souffla la salle de jeu ; la partie reprit l'après-midi même. | [en §](https://en.wikipedia.org/wiki/List_of_Go_games#Atomic_bomb_game) | [SL](https://senseis.xmp.net/?AtomicBombGame) |
| 74 | Parties historiques | Game of the Century | Bleu | Go Seigen contre Honinbo Shusai, en 1933-1934, ouverte au 3-3, au hoshi puis au tengen. Ajournée treize fois au gré de Shusai, qui gagna de deux points grâce au coup 160, soufflé, dit-on, par son élève Maeda Nobuaki. | [en §](https://en.wikipedia.org/wiki/List_of_Go_games#The_Game_of_the_Century) | [SL](https://senseis.xmp.net/?GameOfTheCentury) |

#### 🟢 Vert — 69 cartes

| Id | Catégorie | Titre | Rareté | Description | Wikipedia | Sensei's Library |
|---:|-----------|-------|--------|-------------|-----------|-------------------|
| 75 | Communauté | AlphaGo Series | Vert | Première série de commentaires de parties professionnelles sur FulguroGo : AlphaGo y affronte en cachette de nombreux professionnel·les, avant 2016. |  |  |
| 76 | Communauté | Ashruidan la Chenille Impériale | Vert | Vainqueur de la FGC 2024, catégorie Novice-Elite. |  |  |
| 77 | Communauté | Goban sur Écoute | Vert | Des débats autour du go. Le premier, en 2019 : « L'IA, nécessaire aujourd'hui ». |  |  |
| 78 | Communauté | Hebus le Salamandron Impérial | Vert | Vainqueur de la FGC 2020, catégorie Novice-Elite. |  |  |
| 79 | Communauté | Hyôga le Mammouthon Impérial | Vert | Vainqueur de la FGC 2025, catégorie Novice-Elite. |  |  |
| 80 | Communauté | Kaigito le Chauve-Souriceau Impérial | Vert | Vainqueur de la FGC 2023, catégorie Novice-Elite. |  |  |
| 81 | Communauté | L'été dans la Grotte de l'Hermite 2017 | Vert | Plusieurs tournois organisés pendant l'été 2017. Les prémices de la FulguroGo Cup ? |  |  |
| 82 | Communauté | Lilanlu le Louveteau Impérial | Vert | Vainqueur de la FGC 2022, catégorie Novice-Elite. |  |  |
| 83 | Communauté | Nakamura Sumire en Corée | Vert | Analyses des parties de Nakamura Sumire, jeune prodige japonaise partie en Corée pour devenir encore plus forte. |  |  |
| 84 | Communauté | Qualification Pro | Vert | Commentaires des parties de qualification pour devenir professionnel de go en Europe. |  |  |
| 85 | Communauté | Savagnin le Choupisson Impérial | Vert | Vainqueur de la FGC 2021, catégorie Novice-Elite. |  |  |
| 86 | Communauté | Soku le Marmotton Impérial | Vert | Vainqueur de la FGC 2019, catégorie Novice-Elite. |  |  |
| 87 | Communauté | Tournoi du Mont Tengen | Vert | Le tournoi de fin de saison entre les leaders des quatre maisons. Son vainqueur accède directement à la finale de la FGC. |  |  |
| 88 | Communauté | Yanae l'Oursonne Impériale | Vert | Vainqueur de la FGC 2026, catégorie Novice-Elite. |  |  |
| 89 | Formes complexes | Dos de tortue | Vert | Le kame no kō : la forme laissée par la capture de deux pierres, un double ponnuki d'une grande solidité. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Formes_des_pierres) | [SL](https://senseis.xmp.net/?TortoiseShell) |
| 90 | Formes complexes | Tête de cheval (uma no kao) | Vert | Un ogeima joué depuis deux pierres en ikken tobi. |  | [SL](https://senseis.xmp.net/?HorseHead) |
| 91 | Fuseki | Grande muraille | Vert | Une ouverture expérimentale qui bâtit un mur d'un bord à l'autre, contre toute stratégie classique. |  | [SL](https://senseis.xmp.net/?GreatWall) |
| 92 | Fuseki | Trou noir | Vert | Noir joue les quatre points 5-7 : une ouverture tournée tout entière vers le centre, qui aspire toutes les pensées adverses. |  |  |
| 93 | Joueurs | Alexander Qi | Vert | Professionnel nord-américain depuis 2022. |  | [SL](https://senseis.xmp.net/?AlexanderQi) |
| 94 | Joueurs | Andrii Kravets | Vert | Joueur ukrainien, professionnel européen depuis 2017. Il finit toujours sur le podium, et souvent premier. | [en](https://en.wikipedia.org/wiki/Andrij_Kravets) | [SL](https://senseis.xmp.net/?AndriiKravets) |
| 95 | Joueurs | Byun Sangil | Vert | Professionnel coréen depuis 2012, l'un des plus forts joueurs du monde. | [fr](https://fr.wikipedia.org/wiki/Byun_Sangil) | [SL](https://senseis.xmp.net/?ByunSangil) |
| 96 | Joueurs | Dang Yifei | Vert | Professionnel chinois depuis 2007, vainqueur de la LG Cup en 2017. | [fr](https://fr.wikipedia.org/wiki/Dang_Yifei) | [SL](https://senseis.xmp.net/?DangYifei) |
| 97 | Joueurs | Fujisawa Rina | Vert | Professionnelle japonaise depuis 2010, titrée dans les tournois féminins, petite-fille de Fujisawa Shuko. | [fr](https://fr.wikipedia.org/wiki/Rina_Fujisawa) | [SL](https://senseis.xmp.net/?FujisawaRina) |
| 98 | Joueurs | Fukuoka Kotaro | Vert | Professionnel japonais depuis 2019, Honinbo en 2026. | [fr](https://fr.wikipedia.org/wiki/K%C5%8Dtar%C5%8D_Fukuoka) | [SL](https://senseis.xmp.net/?FukuokaKotaro) |
| 99 | Joueurs | Hane Naoki | Vert | Professionnel japonais depuis 1991, vainqueur du Kisei et du Honinbo. L'un des plus rapides à atteindre le 9ᵉ dan, en 2002. | [fr](https://fr.wikipedia.org/wiki/Hane_Naoki) | [SL](https://senseis.xmp.net/?HaneNaoki) |
| 100 | Joueurs | Hashimoto Utaro | Vert | Professionnel japonais depuis 1922, trois fois Honinbo et fondateur de la Kansai Ki-in. | [fr](https://fr.wikipedia.org/wiki/Hashimoto_Utar%C5%8D) | [SL](https://senseis.xmp.net/?HashimotoUtaro) |
| 101 | Joueurs | Iwamoto Kaoru | Vert | Honinbo Kunwa, président de la Nihon Ki-in, qui consacra sa fortune à diffuser le go en Occident. L'un des deux joueurs de la partie de la bombe atomique. | [fr](https://fr.wikipedia.org/wiki/Iwamoto_Kaoru) | [SL](https://senseis.xmp.net/?IwamotoKaoru) |
| 102 | Joueurs | Kim Chaeyoung | Vert | Professionnelle coréenne depuis 2011, deux fois vainqueure du Guksu féminin, à dix ans d'intervalle. |  | [SL](https://senseis.xmp.net/?KimChaeyoung) |
| 103 | Joueurs | Kishimoto Saichiro | Vert | Professionnel japonais du XIXᵉ siècle, célèbre pour son recueil de tesuji publié en 1848. |  | [SL](https://senseis.xmp.net/?KishimotoSaichiro) |
| 104 | Joueurs | Kitani Minoru | Vert | Rival et ami de Go Seigen, avec qui il inventa le shinfuseki. Son école forma une génération de champions. | [fr](https://fr.wikipedia.org/wiki/Minoru_Kitani) | [SL](https://senseis.xmp.net/?KitaniMinoru) |
| 105 | Joueurs | Kobayashi Satoru | Vert | Professionnel japonais depuis 1974, vainqueur du Kisei et Gosei. | [fr](https://fr.wikipedia.org/wiki/Satoru_Kobayashi) | [SL](https://senseis.xmp.net/?KobayashiSatoru) |
| 106 | Joueurs | Kono Rin | Vert | Professionnel japonais depuis 1996, vainqueur du Tengen, élève de Kobayashi Koichi. | [fr](https://fr.wikipedia.org/wiki/Rin_K%C5%8Dno) | [SL](https://senseis.xmp.net/?KonoRin) |
| 107 | Joueurs | Kyo Kagen | Vert | Professionnel japonais depuis 2013, d'origine taïwanaise. L'un des « trois corbeaux de l'ère Reiwa », avec Shibano Toramaru et Ichiriki Ryo. | [fr](https://fr.wikipedia.org/wiki/Hsu_Chia-yuan) | [SL](https://senseis.xmp.net/?KyoKagen) |
| 108 | Joueurs | Lai Junfu | Vert | Professionnel taïwanais depuis 2016, vainqueur du Guksu en 2024. |  | [SL](https://senseis.xmp.net/?LaiJunfu) |
| 109 | Joueurs | Mukai Chiaki | Vert | Professionnelle japonaise depuis 2004, 32ᵉ Honinbo féminin. | [en](https://en.wikipedia.org/wiki/Chiaki_Mukai_%28Go_player%29) | [SL](https://senseis.xmp.net/?MukaiChiaki) |
| 110 | Joueurs | Murakawa Daisuke | Vert | Professionnel japonais depuis 2002, vainqueur de titres majeurs. | [fr](https://fr.wikipedia.org/wiki/Daisuke_Murakawa) | [SL](https://senseis.xmp.net/?MurakawaDaisuke) |
| 111 | Joueurs | Nakamura Sumire | Vert | Plus jeune professionnelle de l'histoire du Japon, devenue pro à 10 ans en 2019, titrée chez les femmes. Partie en Corée pour devenir encore plus forte. | [fr](https://fr.wikipedia.org/wiki/Sumire_Nakamura) | [SL](https://senseis.xmp.net/?NakamuraSumire) |
| 112 | Joueurs | Nyu Eiko | Vert | Professionnelle japonaise depuis 2015, deux fois vainqueure de la Senko Cup. |  | [SL](https://senseis.xmp.net/?NyuEiko) |
| 113 | Joueurs | O Meien | Vert | Professionnel taïwanais de la Nihon Ki-in depuis 1977, double Honinbo, auteur de livres sur le fuseki. | [fr](https://fr.wikipedia.org/wiki/O_Meien) | [SL](https://senseis.xmp.net/?OMeien) |
| 114 | Joueurs | Oh Yujin | Vert | Professionnelle coréenne depuis 2012, vainqueure du Guksu et du Kisung en 2021. |  | [SL](https://senseis.xmp.net/?OhYujin) |
| 115 | Joueurs | Park Younghun | Vert | Professionnel coréen depuis 1999, double vainqueur de la Fujitsu Cup. Il détient le record coréen du passage le plus rapide du 1ᵉʳ au 9ᵉ dan, à 19 ans. | [en](https://en.wikipedia.org/wiki/Park_Yeong-hun) | [SL](https://senseis.xmp.net/?ParkYounghun) |
| 116 | Joueurs | Ryan Li | Vert | Professionnel nord-américain depuis 2015, docteur en sciences atmosphériques. |  | [SL](https://senseis.xmp.net/?RyanLi) |
| 117 | Joueurs | Seki Kotaro | Vert | Professionnel japonais depuis 2017, vainqueur du Tengen. Le plus rapide à avoir remporté un grand titre japonais. | [fr](https://fr.wikipedia.org/wiki/K%C5%8Dtar%C5%8D_Seki) | [SL](https://senseis.xmp.net/?SekiKotaro) |
| 118 | Joueurs | Sekiyama Riichi | Vert | Premier vainqueur du Honinbo en tournoi, en 1941, quand le titre cessa d'être héréditaire. | [en](https://en.wikipedia.org/wiki/Riichi_Sekiyama) | [SL](https://senseis.xmp.net/?SekiyamaRiichi) |
| 119 | Joueurs | Shibano Toramaru | Vert | Professionnel japonais depuis 2014, Meijin en 2019 à 19 ans : le premier à remporter un grand titre japonais à cet âge. | [fr](https://fr.wikipedia.org/wiki/Toramaru_Shibano) | [SL](https://senseis.xmp.net/?ShibanoToramaru) |
| 120 | Joueurs | Takao Shinji | Vert | Professionnel japonais depuis 1991, Honinbo et Meijin, élève de Fujisawa Shuko. | [fr](https://fr.wikipedia.org/wiki/Takao_Shinji) | [SL](https://senseis.xmp.net/?TakaoShinji) |
| 121 | Joueurs | Ueno Asami | Vert | Professionnelle japonaise depuis 2016, titrée dans les tournois féminins. Surnommée « le Marteau » pour son style de combat agressif. | [fr](https://fr.wikipedia.org/wiki/Ueno_Asami) | [SL](https://senseis.xmp.net/?UenoAsami) |
| 122 | Joueurs | Ueno Risa | Vert | Professionnelle japonaise depuis 2019, sœur cadette d'Ueno Asami. Elle remporte le Kisei féminin 2024 en battant sa rivale Nakamura Sumire. |  | [SL](https://senseis.xmp.net/?UenoRisa) |
| 123 | Joueurs | Wang Xinghao | Vert | Professionnel chinois depuis 2016, vainqueur de la LG Cup en 2026. | [fr](https://fr.wikipedia.org/wiki/Wang_Xinghao) | [SL](https://senseis.xmp.net/?WangXinghao) |
| 124 | Joueurs | Wu Yiming | Vert | Professionnelle chinoise depuis 2018, championne du tournoi féminin des Jeux asiatiques, surnommée « Little Witch ». |  | [SL](https://senseis.xmp.net/?WuYiming) |
| 125 | Joueurs | Xie Yimin | Vert | Professionnelle taïwanaise de la Nihon Ki-in depuis 2004, longtemps reine des titres féminins japonais. | [fr](https://fr.wikipedia.org/wiki/Shei_Imin) | [SL](https://senseis.xmp.net/?XieYimin) |
| 126 | Joueurs | Yamashita Keigo | Vert | Professionnel japonais depuis 1993, plusieurs fois Kisei. | [fr](https://fr.wikipedia.org/wiki/Keigo_Yamashita) | [SL](https://senseis.xmp.net/?YamashitaKeigo) |
| 127 | Parties historiques | Famous Killing Game of 1926 | Vert | Honinbo Shusai contre Karigane Junichi, en 1926, duel entre la Nihon Ki-in et la Kiseisha. Un combat de plus de 150 coups, conclu par la capture d'un grand groupe. |  | [SL](https://senseis.xmp.net/?FamousKillingGameOf1926) |
| 128 | Parties historiques | Nine Dragons Playing with a Pearl | Vert | Partie chinoise du XVIIIᵉ siècle entre Shi Xiangxia et Cheng Lanru, célèbre pour ses combats à grande échelle où le sort de plusieurs grands groupes reste longtemps incertain. |  | [SL](https://senseis.xmp.net/?NineDragonsPlayingWithAPearl) |
| 129 | Parties historiques | Sixteen Soldiers Game | Vert | Kosugi Tei contre Go Seigen, en 1933, à l'Oteai. L'une des ouvertures les plus déroutantes de l'ère du shinfuseki. |  | [SL](https://senseis.xmp.net/?SixteenSoldiers) |
| 130 | Tournois pro | Gosei | Vert | Titre japonais, « le sage du go ». Première édition en 1976. Le vainqueur reçoit 8 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Gosei_%28go%29) | [SL](https://senseis.xmp.net/?Gosei) |
| 131 | Tournois pro | Guoshou | Vert | Titre chinois, « le maître national », joué à Kaifeng tous les deux ans. Disputé de 1981 à 1987, il renaît en 2021. Le vainqueur reçoit 400 000 yuans. |  | [SL](https://senseis.xmp.net/?GuoshouTournament) |
| 132 | Tournois pro | Honinbo | Vert | Le plus ancien titre japonais, du nom de la grande maison de go de l'époque d'Edo. Première édition en 1941. Le vainqueur reçoit 8,5 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Hon'inb%C5%8D) | [SL](https://senseis.xmp.net/?Honinbo) |
| 133 | Tournois pro | Judan | Vert | Titre japonais, « dixième dan ». Première édition en 1962. Le vainqueur reçoit 7 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Judan_%28go%29) | [SL](https://senseis.xmp.net/?Judan) |
| 134 | Tournois pro | Kisei | Vert | Le titre japonais le mieux doté, « le saint du go ». Première édition en 1977. Le vainqueur reçoit 43 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Kisei_%28jeu_de_go%29) | [SL](https://senseis.xmp.net/?Kisei) |
| 135 | Tournois pro | Meijin | Vert | Titre japonais, héritier du rang suprême de l'époque d'Edo. Première édition en 1961-1962. Le vainqueur reçoit 33 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Meijin_%28jeu_de_go%29) | [SL](https://senseis.xmp.net/?Meijin) |
| 136 | Tournois pro | Mingren | Vert | Titre chinois, équivalent du Meijin japonais. Première édition en 1988. Ma Xiaochun le remporta treize fois d'affilée, de 1989 à 2001. | [fr](https://fr.wikipedia.org/wiki/Mingren) | [SL](https://senseis.xmp.net/?Mingren) |
| 137 | Tournois pro | Myeongin | Vert | Titre coréen, équivalent du Meijin japonais. Première édition en 1968. Le vainqueur reçoit 70 millions de wons. | [fr](https://fr.wikipedia.org/wiki/Myungin) | [SL](https://senseis.xmp.net/?Myeongin) |
| 138 | Tournois pro | Oza | Vert | Titre japonais, « le trône ». Première édition en 1953. Le vainqueur reçoit 14 millions de yens. | [en](https://en.wikipedia.org/wiki/%C5%8Cza_%28Go%29) | [SL](https://senseis.xmp.net/?Oza) |
| 139 | Tournois pro | Qisheng | Vert | Titre chinois, équivalent du Kisei japonais. Première édition en 1999, joué par intermittence depuis. Le vainqueur reçoit 600 000 yuans. | [en](https://en.wikipedia.org/wiki/Qisheng) | [SL](https://senseis.xmp.net/?Qisheng) |
| 140 | Tournois pro | Tengen | Vert | Titre japonais, du nom du point central du goban. Première édition en 1975. Le vainqueur reçoit 12 à 14 millions de yens. | [fr](https://fr.wikipedia.org/wiki/Tengen_%28go%29) | [SL](https://senseis.xmp.net/?TengenTitle) |
| 141 | Tournois pro | Tianyuan | Vert | Titre chinois, équivalent du Tengen japonais, dont le vainqueur affronte chaque année le Tengen japonais. Première édition en 1987. Le vainqueur reçoit 400 000 yuans. | [fr](https://fr.wikipedia.org/wiki/Tianyuan_%28go%29) | [SL](https://senseis.xmp.net/?Tianyuan) |
| 142 | Variantes | Sunjang | Vert | Le baduk traditionnel coréen, qui commence avec des pierres déjà posées sur le plateau, populaire dès le XVIᵉ siècle. | [en §](https://en.wikipedia.org/wiki/Go_variants#Sunjang_baduk) | [SL](https://senseis.xmp.net/?SunjangBaduk) |
| 143 | Variantes | Torique | Vert | Le go sans bords : chaque côté du plateau se prolonge sur le côté opposé. | [en §](https://en.wikipedia.org/wiki/Go_variants#Borderless_Go) | [SL](https://senseis.xmp.net/?ToroidalGo) |

#### ⚪ Gris — 127 cartes

| Id | Catégorie | Titre | Rareté | Description | Wikipedia | Sensei's Library |
|---:|-----------|-------|--------|-------------|-----------|-------------------|
| 144 | Communauté | Ateliers | Gris | HisokaH revoit les parties des joueureuses de la grotte, par tranche de niveau, pour tous les niveaux. |  |  |
| 145 | Communauté | Challenges mensuels 2016, 2017 | Gris | Un mois, un challenge. Arriverez-vous à atteindre l'objectif ? |  |  |
| 146 | Communauté | European Pro Series | Gris | Analyses vidéo de parties de joueureuses professionnel·les européen·nes. |  |  |
| 147 | Communauté | Fils du Froid | Gris | Maison des combattants, exilée vers le nord : « Le meilleur coup est celui qui brise. » |  |  |
| 148 | Communauté | FulguroGo Cup | Gris | La série de tournois saisonnière de la communauté, en catégories libre et Novice-Elite. |  |  |
| 149 | Communauté | Game of Stones | Gris | Un match en quatre victoires, dont les deux joueurs analysent chaque partie ensemble. |  |  |
| 150 | Communauté | History Pro Player | Gris | Analyses vidéo de parties professionnelles d'un autre siècle. |  |  |
| 151 | Communauté | Ligue d'Aurak | Gris | La ligue de la communauté, entre membres de maisons adverses pour apporter de la renommée à sa maison. |  |  |
| 152 | Communauté | Lunaires d'Æther | Gris | Maison des inventeurs, partie vers les îles célestes : « Pourquoi jouer comme hier ? » |  |  |
| 153 | Communauté | Maisons d'Aurak | Gris | La compétition des quatre maisons, nées de la Partie des Ruptures sur la plaine d'Aurak. |  |  |
| 154 | Communauté | Nexus Alpha | Gris | Maison des calculateurs, retranchée dans les souterrains de quartz : « Chaque coup est une équation. » |  |  |
| 155 | Communauté | On discute de livres | Gris | Découverte de divers livres sur le jeu de go. |  |  |
| 156 | Communauté | Pro Series | Gris | Analyses vidéo de parties professionnelles, pour rendre le compliqué simple. |  |  |
| 157 | Communauté | Retransmission de tournois | Gris | Commentaires des parties retransmises lors de divers tournois amateurs. |  |  |
| 158 | Communauté | Sabre Silencieux | Gris | Maison du bushido, retirée dans les forêts de brume : « Un coup, un destin ! » |  |  |
| 159 | Communauté | Tutoriels | Gris | Les tutoriels vidéo d'HisokaH sur le jeu de go. |  |  |
| 160 | Formes complexes | Double gueule de tigre | Gris | Deux connexions en gueule de tigre, côte à côte, qui protègent deux points de coupe à la fois. |  |  |
| 161 | Formes complexes | Double hane | Gris | Deux hane joués coup sur coup : ambitieux, souvent risqué, parfois payant. | [en §](https://en.wikipedia.org/wiki/List_of_Go_terms#Double_hane) | [SL](https://senseis.xmp.net/?DoubleHane) |
| 162 | Formes complexes | Équerre | Gris | La forme en bouche : cinq pierres autour d'un point vide, pensées pour faire un œil plus que pour connecter. |  | [SL](https://senseis.xmp.net/?MouthShape) |
| 163 | Formes complexes | Gueule du chien (inu no kao) | Gris | Aussi appelée « bouteille de saké » : un keima joué depuis deux pierres en ikken tobi. |  | [SL](https://senseis.xmp.net/?DogsHead) |
| 164 | Formes complexes | Gueule du tigre (neko no kao) | Gris | Trois pierres reliées par deux kosumi opposés, la base de la connexion pendante. |  | [SL](https://senseis.xmp.net/?TigersMouth) |
| 165 | Formes complexes | Nœud de bambou | Gris | Deux paires de pierres parallèles séparées d'une ligne : une connexion impossible à couper. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Formes_des_pierres) | [SL](https://senseis.xmp.net/?BambooJoint) |
| 166 | Formes complexes | Ponnuki | Gris | Le losange de quatre pierres laissé par la capture d'une pierre. « Un ponnuki vaut trente points. » | [en](https://en.wikipedia.org/wiki/Ponnuki) | [SL](https://senseis.xmp.net/?Ponnuki) |
| 167 | Formes complexes | Table | Gris | Quatre pierres proches de l'Équerre, qui restent connectées tout en gardant un potentiel d'œil. Moins solide que le nœud de bambou. |  | [SL](https://senseis.xmp.net/?TableShape) |
| 168 | Formes simples | Hane | Gris | Un coup en diagonale qui contourne une pierre adverse au contact. | [fr](https://fr.wikipedia.org/wiki/Hane_%28go%29) | [SL](https://senseis.xmp.net/?Hane) |
| 169 | Formes simples | Hazama tobi | Gris | Le saut en diagonale, qui laisse une intersection vide entre deux pierres. On l'appelle aussi « pas d'éléphant ». |  | [SL](https://senseis.xmp.net/?HazamaTobi) |
| 170 | Formes simples | Ikken tobi | Gris | Le saut d'un espace en ligne droite, aussi appelé « tobi ». | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#Tobi_ou_Ikken-tobi_%28%E4%B8%80%E9%96%93%E3%83%88%E3%83%93%29) | [SL](https://senseis.xmp.net/?OneSpaceJump) |
| 171 | Formes simples | Keima | Gris | Le saut du cavalier : léger et rapide, mais coupable. | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#Keima_%28%E6%A1%82%E9%A6%AC%29) | [SL](https://senseis.xmp.net/?Keima) |
| 172 | Formes simples | Kosumi | Gris | Le coup en diagonale : lent, mais presque impossible à couper. | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#Kosumi_%28%E3%82%B3%E3%82%B9%E3%83%9F%29) | [SL](https://senseis.xmp.net/?Kosumi) |
| 173 | Formes simples | Niken tobi | Gris | Le saut de deux espaces en ligne droite, plus rapide et plus fragile que l'ikken tobi. | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#Niken-tobi) | [SL](https://senseis.xmp.net/?TwoSpaceJump) |
| 174 | Formes simples | Nobi | Gris | Prolonger en ligne droite, pierre contre pierre : le coup le plus solide qui soit. | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#Nobi) | [SL](https://senseis.xmp.net/?Nobi) |
| 175 | Formes simples | Ogeima | Gris | Le grand cavalier, un saut plus étendu que le keima. | [fr §](https://fr.wikipedia.org/wiki/Formes_du_go#%C5%8Cgeima_%28%E5%A4%A7%E3%82%B2%E3%82%A4%E3%83%9E%29) | [SL](https://senseis.xmp.net/?LargeKnightsMove) |
| 176 | Fuseki | Chinois | Gris | Hoshi, komoku et une extension basse ou haute sur le côté : l'ouverture popularisée par les joueurs chinois. | [en](https://en.wikipedia.org/wiki/Chinese_opening) | [SL](https://senseis.xmp.net/?ChineseOpening) |
| 177 | Fuseki | Kobayashi | Gris | L'ouverture du style de Kobayashi Koichi, bâtie autour d'un komoku et d'une approche rapide du coin adverse. | [en](https://en.wikipedia.org/wiki/Kobayashi_opening) | [SL](https://senseis.xmp.net/?KobayashiOpening) |
| 178 | Fuseki | Orthodoxe | Gris | L'ouverture classique : un hoshi ou un komoku et un shimari qui le regarde. |  | [SL](https://senseis.xmp.net/?OrthodoxFuseki) |
| 179 | Fuseki | Sanrensei | Gris | Trois hoshi alignés sur un même côté, pour un jeu d'influence tourné vers le centre depuis un bord. |  | [SL](https://senseis.xmp.net/?SanrenseiFuseki) |
| 180 | Fuseki | Shusaku | Gris | L'ouverture de Honinbo Shusaku : trois komoku et le célèbre kosumi de Shusaku. | [en](https://en.wikipedia.org/wiki/Shusaku_opening) | [SL](https://senseis.xmp.net/?ShusakuFuseki) |
| 181 | Institutions | American Go Association (AGA) | Gris | La fédération des États-Unis. | [fr](https://fr.wikipedia.org/wiki/American_Go_Association) | [SL](https://senseis.xmp.net/?AmericanGoAssociation) |
| 182 | Institutions | Chinese Weiqi Association (Zhōngguó Wéiqí Xiéhuì) | Gris | L'association qui organise le go professionnel en Chine. | [fr](https://fr.wikipedia.org/wiki/Association_chinoise_de_weiqi) | [SL](https://senseis.xmp.net/?ChineseWeiqiAssociation) |
| 183 | Institutions | Échelle kyu/dan | Gris | Le système de grades du go : les kyu pour progresser, les dan pour les joueurs confirmés. | [en](https://en.wikipedia.org/wiki/Go_ranks_and_ratings) | [SL](https://senseis.xmp.net/?Rank) |
| 184 | Institutions | European Go Federation (EGF) | Gris | La fédération européenne, qui réunit les associations d'Europe et délivre un statut professionnel européen. | [fr](https://fr.wikipedia.org/wiki/F%C3%A9d%C3%A9ration_europ%C3%A9enne_de_go) | [SL](https://senseis.xmp.net/?EuropeanGoFederation) |
| 185 | Institutions | Fédération Française de Go (FFG) | Gris | La fédération française. | [fr](https://fr.wikipedia.org/wiki/F%C3%A9d%C3%A9ration_fran%C3%A7aise_de_go) | [SL](https://senseis.xmp.net/?FrenchGoFederation) |
| 186 | Institutions | Insei | Gris | Élève d'une école professionnelle, en formation pour devenir pro. | [fr](https://fr.wikipedia.org/wiki/Insei_%28go%29) | [SL](https://senseis.xmp.net/?Insei) |
| 187 | Institutions | International Go Federation | Gris | La fédération internationale, qui réunit les associations nationales du monde entier. | [fr](https://fr.wikipedia.org/wiki/F%C3%A9d%C3%A9ration_internationale_de_go) | [SL](https://senseis.xmp.net/?InternationalGoFederation) |
| 188 | Institutions | Japanese Go Association (Nihon Ki-in) | Gris | La principale organisation du go professionnel japonais, fondée en 1924. | [fr](https://fr.wikipedia.org/wiki/Nihon_Ki-in) | [SL](https://senseis.xmp.net/?NihonKiin) |
| 189 | Institutions | Korean Baduk Association (Hanguk Kiwon) | Gris | L'association qui organise le baduk professionnel en Corée. | [fr](https://fr.wikipedia.org/wiki/Hanguk_Kiwon) | [SL](https://senseis.xmp.net/?HankukKiwon) |
| 190 | Joueurs | Ali Jabarin | Gris | Professionnel européen depuis 2014, parmi les tout premiers. |  | [SL](https://senseis.xmp.net/?AliJabarin) |
| 191 | Joueurs | Antti Törmänen | Gris | Professionnel finlandais de la Nihon Ki-in depuis 2016. | [fr](https://fr.wikipedia.org/wiki/Antti_T%C3%B6rm%C3%A4nen_%28joueur_de_go%29) | [SL](https://senseis.xmp.net/?AnttiTormanen) |
| 192 | Joueurs | Artem Kachanovskyi | Gris | Joueur ukrainien, professionnel depuis 2016, champion de la ligue professionnelle européenne en 2020. | [fr](https://fr.wikipedia.org/wiki/Artem_Katchanovskyi) | [SL](https://senseis.xmp.net/?ArtemKachanovskyi) |
| 193 | Joueurs | Benjamin Dréan-Guénaïzia | Gris | Joueur français, professionnel européen depuis 2025, connu aussi sous le pseudonyme Ben0. |  | [SL](https://senseis.xmp.net/?BenjaminDreanGuenaizia) |
| 194 | Joueurs | Chen Qirui | Gris | Professionnel taïwanais depuis 2013. |  | [SL](https://senseis.xmp.net/?ChenQirui) |
| 195 | Joueurs | Cho Seungah | Gris | Professionnelle coréenne depuis 2016, première vainqueure de la Nanseolheon Cup, en 2021. |  | [SL](https://senseis.xmp.net/?ChoSeungah) |
| 196 | Joueurs | Dai Junfu | Gris | Amateur chinois installé en France, auteur de plusieurs livres sur la prise de décision, tirés de l'analyse de positions de chuban. |  | [SL](https://senseis.xmp.net/?DaiJunfu) |
| 197 | Joueurs | Hoshiai Shiho | Gris | Professionnelle japonaise depuis 2013, souvent finaliste des grands titres féminins. |  | [SL](https://senseis.xmp.net/?HoshiaiShiho) |
| 198 | Joueurs | Inseong Hwang | Gris | Joueur coréen installé en France, maître du Yunguseng Dojang depuis 2010. |  | [SL](https://senseis.xmp.net/?InseongHwang) |
| 199 | Joueurs | Jan Simara | Gris | Joueur tchèque, professionnel européen depuis 2023. | [en](https://en.wikipedia.org/wiki/Jan_%C5%A0imara) | [SL](https://senseis.xmp.net/?JanSimara) |
| 200 | Joueurs | Kim Dohyup | Gris | Amateur coréen, souvent le boss final des grands tournois européens. Il collectionne les trophées. |  |  |
| 201 | Joueurs | Kim Myeong-hun | Gris | Professionnel coréen depuis 2014. |  | [SL](https://senseis.xmp.net/?KimMyeongHun) |
| 202 | Joueurs | Lee Jihyun | Gris | Professionnel coréen depuis 2010, vainqueur à deux reprises de la Maxim Cup. |  | [SL](https://senseis.xmp.net/?LeeJihyun) |
| 203 | Joueurs | Liao Yuanhe | Gris | Professionnel chinois depuis 2013, vainqueur de la 30ᵉ Samsung Cup. |  | [SL](https://senseis.xmp.net/?LiaoYuanhe) |
| 204 | Joueurs | Maeda Nobuaki | Gris | Professionnel japonais du XXᵉ siècle, surnommé le « dieu du tsumego » pour ses recueils de problèmes. | [en](https://en.wikipedia.org/wiki/Nobuaki_Maeda) | [SL](https://senseis.xmp.net/?MaedaNobuaki) |
| 205 | Joueurs | Mateusz Surma | Gris | Joueur polonais, professionnel européen depuis 2015. Tel Dracula, il absorbe le sang de ses ennemis sur le plateau. |  | [SL](https://senseis.xmp.net/?MateuszSurma) |
| 206 | Joueurs | Michael Redmond | Gris | Américain, premier Occidental 9ᵉ dan professionnel au Japon, commentateur des parties d'AlphaGo. | [fr](https://fr.wikipedia.org/wiki/Michael_Redmond_%28joueur_de_go%29) | [SL](https://senseis.xmp.net/?MichaelRedmond) |
| 207 | Joueurs | Motoki Noguchi | Gris | Joueur japonais installé en France, figure du go français. | [fr](https://fr.wikipedia.org/wiki/Motoki_Noguchi) | [SL](https://senseis.xmp.net/?MotokiNoguchi) |
| 208 | Joueurs | Oh Jeonga | Gris | Professionnelle coréenne depuis 2011, vainqueure de la Dasan Cup en 2017. Elle a entraîné l'équipe nationale coréenne. |  | [SL](https://senseis.xmp.net/?OhJeonga) |
| 209 | Joueurs | Park Mingyu | Gris | Professionnel coréen depuis 2013. |  | [SL](https://senseis.xmp.net/?ParkMingyu) |
| 210 | Joueurs | Pavol Lisy | Gris | Joueur slovaque, premier joueur devenu professionnel européen, en 2014. | [en](https://en.wikipedia.org/wiki/Pavol_Lis%C3%BD) | [SL](https://senseis.xmp.net/?PavolLisy) |
| 211 | Joueurs | Sada Atsushi | Gris | Professionnel japonais de la Kansai Ki-in depuis 2012. |  | [SL](https://senseis.xmp.net/?SadaAtsushi) |
| 212 | Joueurs | Stanislaw Frejlak | Gris | Joueur polonais, professionnel européen depuis 2021. |  | [SL](https://senseis.xmp.net/?StanislawFrejlak) |
| 213 | Joueurs | Suzuki Ayumi | Gris | Professionnelle japonaise depuis 2001, Kisei féminin en 2020. |  | [SL](https://senseis.xmp.net/?SuzukiAyumi) |
| 214 | Joueurs | Tang Jiawen | Gris | Professionnelle chinoise depuis 2017, vainqueure du Guoshou féminin en 2024. |  | [SL](https://senseis.xmp.net/?TangJiawen) |
| 215 | Joueurs | Tanguy Le Calvé | Gris | Joueur français, professionnel depuis 2019, parmi les meilleurs du pays. |  | [SL](https://senseis.xmp.net/?TanguyLeCalve) |
| 216 | Joueurs | Tong Mengcheng | Gris | Professionnel chinois depuis 2008. |  | [SL](https://senseis.xmp.net/?TongMengcheng) |
| 217 | Joueurs | Wang Yuanjun | Gris | Professionnel taïwanais depuis 2007, commentateur et pédagogue. |  | [SL](https://senseis.xmp.net/?WangYuanjun) |
| 218 | Joueurs | Yu Zhengqi | Gris | Professionnel taïwanais affilié à la Kansai Ki-in, au Japon, sous le nom de Yo Seiki. |  | [SL](https://senseis.xmp.net/?YuZhengqi) |
| 219 | Joueurs | Zhou Hongyu | Gris | Professionnelle chinoise depuis 2015, vainqueure de la 10ᵉ Huang Longshi Shuang Deng Cup. |  | [SL](https://senseis.xmp.net/?ZhouHongyu) |
| 220 | Matériel | Bols (goke) | Gris | Les deux bols, souvent en bois, qui contiennent les pierres de chaque joueur. | [en §](https://en.wikipedia.org/wiki/Go_equipment#Bowls) | [SL](https://senseis.xmp.net/?Goke) |
| 221 | Matériel | Éventail | Gris | L'éventail que tiennent les professionnels japonais pendant leurs parties. |  | [SL](https://senseis.xmp.net/?Sensu) |
| 222 | Matériel | Horloge | Gris | La pendule qui décompte le temps de réflexion, jusqu'au byo-yomi. | [fr](https://fr.wikipedia.org/wiki/Pendule_de_jeu) | [SL](https://senseis.xmp.net/?Clock) |
| 223 | Matériel | Kifu | Gris | La feuille où l'on note les coups d'une partie, numéro par numéro. | [fr](https://fr.wikipedia.org/wiki/Kifu) | [SL](https://senseis.xmp.net/?Kifu) |
| 224 | Matériel | Pierre (ishi) | Gris | Les pierres noires et blanches, convexes ou biconvexes ; les plus belles sont en ardoise et en coquillage. | [fr](https://fr.wikipedia.org/wiki/Pierre_%28go%29) | [SL](https://senseis.xmp.net/?GoStones) |
| 225 | Matériel | Plateau (goban) | Gris | Le plateau de 19 × 19 lignes, traditionnellement taillé dans le kaya. | [fr](https://fr.wikipedia.org/wiki/Goban) | [SL](https://senseis.xmp.net/?Goban) |
| 226 | Meta | Chuban | Gris | Le milieu de partie, là où se livrent les combats. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#D%C3%A9roulement_de_la_partie) | [SL](https://senseis.xmp.net/?Chuban) |
| 227 | Meta | Fuseki | Gris | L'ouverture, quand les joueurs se partagent le plateau à grands traits. | [fr](https://fr.wikipedia.org/wiki/Fuseki) | [SL](https://senseis.xmp.net/?Fuseki) |
| 228 | Meta | Geta | Gris | Le filet : une capture à distance dont la pierre adverse ne peut plus sortir. | [fr](https://fr.wikipedia.org/wiki/Geta_%28go%29) | [SL](https://senseis.xmp.net/?Geta) |
| 229 | Meta | Glissade du singe | Gris | Le saut sur la première ligne, sous des pierres adverses, pour entamer un territoire par le bord. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Coups_utilis%C3%A9s_au_combat) | [SL](https://senseis.xmp.net/?MonkeyJump) |
| 230 | Meta | Joseki | Gris | Une séquence, en général de coin, jugée localement équilibrée pour les deux joueurs. | [fr](https://fr.wikipedia.org/wiki/Joseki) | [SL](https://senseis.xmp.net/?Joseki) |
| 231 | Meta | Komi | Gris | Les points donnés à Blanc pour compenser l'avantage du premier coup de Noir. | [fr](https://fr.wikipedia.org/wiki/Komi_%28go%29) | [SL](https://senseis.xmp.net/?Komi) |
| 232 | Meta | Point vital | Gris | Le point décisif d'une forme, qui fait vivre ou mourir un groupe. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Coups_utilis%C3%A9s_au_combat) | [SL](https://senseis.xmp.net/?VitalPoint) |
| 233 | Meta | Shicho | Gris | L'échelle : une poursuite en atari successifs qui traverse le plateau en zigzag. | [fr](https://fr.wikipedia.org/wiki/Shich%C5%8D) | [SL](https://senseis.xmp.net/?Shicho) |
| 234 | Meta | Triangle de politesse | Gris | La zone du coin supérieur droit où, par politesse, on joue traditionnellement son premier coup. |  |  |
| 235 | Meta | Yose | Gris | La fin de partie, phase souvent sous-estimée et souvent la plus longue d'une partie. | [fr](https://fr.wikipedia.org/wiki/Yose) | [SL](https://senseis.xmp.net/?Yose) |
| 236 | Ouvertures | Hoshi | Gris | Le point étoile 4-4 : rapide et tourné vers l'influence, mais il laisse l'invasion au 3-3. | [fr](https://fr.wikipedia.org/wiki/Hoshi_%28jeu_de_go%29) | [SL](https://senseis.xmp.net/?Hoshi) |
| 237 | Ouvertures | Komoku | Gris | Le 3-4, l'ouverture de coin classique, équilibrée entre territoire et influence. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Points_particuliers_du_goban) | [SL](https://senseis.xmp.net/?Komoku) |
| 238 | Ouvertures | Mokuhazushi | Gris | Le 3-5, qui vise le côté plutôt que le coin. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Points_particuliers_du_goban) | [SL](https://senseis.xmp.net/?Mokuhazushi) |
| 239 | Ouvertures | Sansan | Gris | Le 3-3, qui prend le coin d'un coup, au prix de l'influence. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Points_particuliers_du_goban) | [SL](https://senseis.xmp.net/?Sansan) |
| 240 | Ouvertures | Shimari | Gris | Deux pierres qui ferment un coin et rendent l'invasion difficile. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Formes_des_pierres) | [SL](https://senseis.xmp.net/?Shimari) |
| 241 | Ouvertures | Takamoku | Gris | Le 4-5, orienté vers l'influence. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Points_particuliers_du_goban) | [SL](https://senseis.xmp.net/?Takamoku) |
| 242 | Ouvertures | Tengen | Gris | Le ciel : le point central du goban. | [fr §](https://fr.wikipedia.org/wiki/Glossaire_du_go#Points_particuliers_du_goban) | [SL](https://senseis.xmp.net/?Tengen) |
| 243 | Serveurs | FOX Weiqi | Gris | Le serveur chinois, l'un des plus fréquentés du monde. |  | [SL](https://senseis.xmp.net/?FoxGoServer) |
| 244 | Serveurs | Go Quest | Gris | L'application des parties rapides sur petits plateaux. |  | [SL](https://senseis.xmp.net/?GoQuest) |
| 245 | Serveurs | IGS Pandanet | Gris | Le serveur japonais Internet Go Server, l'un des tout premiers serveurs de go en ligne. | [en](https://en.wikipedia.org/wiki/Pandanet) | [SL](https://senseis.xmp.net/?IGS) |
| 246 | Serveurs | KGS | Gris | Le serveur historique de la communauté occidentale, où est née la Grotte de l'Hermite. | [fr](https://fr.wikipedia.org/wiki/KGS) | [SL](https://senseis.xmp.net/?KGS) |
| 247 | Serveurs | OGS | Gris | L'Online Go Server, où se joue la Ligue d'Aurak. |  | [SL](https://senseis.xmp.net/?OGS) |
| 248 | Tournois pro | Agon Cup | Gris | Tournoi chinois en parties rapides créé en 1999, l'Ahan Tongshan Cup. Son vainqueur affrontait celui de l'Agon-Kiriyama Cup japonaise. | [en](https://en.wikipedia.org/wiki/Ahan_Tongshan_Cup) | [SL](https://senseis.xmp.net/?ChineseAgonCup) |
| 249 | Tournois pro | GS Caltex Cup | Gris | Tournoi coréen créé en 1996 sous le nom de Techron Cup, finale en cinq parties. Le vainqueur reçoit 70 millions de wons. | [en](https://en.wikipedia.org/wiki/GS_Caltex_Cup) | [SL](https://senseis.xmp.net/?GSCaltexCup) |
| 250 | Tournois pro | Guoshou | Gris | Titre taïwanais, « le maître national ». La série actuelle date de 2005. Le vainqueur reçoit 500 000 dollars taïwanais. |  | [SL](https://senseis.xmp.net/?TaiwanGuoshou) |
| 251 | Tournois pro | Ing Cup | Gris | Tournoi mondial joué tous les quatre ans depuis 1988, surnommé les « Jeux olympiques du go ». | [fr](https://fr.wikipedia.org/wiki/Coupe_Ing) | [SL](https://senseis.xmp.net/?IngCup) |
| 252 | Tournois pro | Japan-China-Korea Ryusei | Gris | Tournoi télévisé en parties rapides regroupant les vainqueurs du Ryusei, Longxing et Ryongsang. |  | [SL](https://senseis.xmp.net/?JapanChinaKoreaRyusei) |
| 253 | Tournois pro | LG Cup | Gris | Tournoi mondial coréen créé en 1996. | [fr](https://fr.wikipedia.org/wiki/Coupe_LG) | [SL](https://senseis.xmp.net/?LGCup) |
| 254 | Tournois pro | Longxing | Gris | Tournoi chinois télévisé en parties rapides, équivalent du Ryusei japonais et du Ryongsang coréen. |  | [SL](https://senseis.xmp.net/?Longxing) |
| 255 | Tournois pro | Maxim Cup | Gris | Tournoi coréen télévisé en parties rapides, créé en 2000 et réservé aux 9ᵉ dan. | [en](https://en.wikipedia.org/wiki/Maxim_Cup) | [SL](https://senseis.xmp.net/?MaximCup) |
| 256 | Tournois pro | Mingren | Gris | Titre taïwanais, le mieux doté du pays. Disputé de 1974 à 2010, il est repris en 2020 par la Haifong Go Academy. Le vainqueur reçoit 1,8 million de dollars taïwanais. |  | [SL](https://senseis.xmp.net/?TaiwanMingren) |
| 257 | Tournois pro | MLily Cup | Gris | Tournoi mondial chinois créé en 2013, joué tous les deux ans. Sa 3ᵉ édition fut la première à inviter un programme, DeepZenGo. Le vainqueur reçoit 1,8 million de yuans. | [en](https://en.wikipedia.org/wiki/MLily_Cup) | [SL](https://senseis.xmp.net/?MLilyCup) |
| 258 | Tournois pro | Nongshim Cup | Gris | Tournoi mondial par équipes créé en 1999 entre la Chine, la Corée et le Japon : le vainqueur de chaque partie reste en jeu. Lee Changho y gagna 14 parties sans une défaite. | [fr](https://fr.wikipedia.org/wiki/Coupe_Nongshim) | [SL](https://senseis.xmp.net/?NongshimCup) |
| 259 | Tournois pro | Qiwang | Gris | Titre taïwanais, « le roi du go ». Première édition en 2008. Le vainqueur reçoit 1,2 million de dollars taïwanais. |  | [SL](https://senseis.xmp.net/?TaiwanQiwang) |
| 260 | Tournois pro | Quzhou-Lanke Cup | Gris | Tournoi chinois créé en 2006, joué tous les deux ans. Il tient son nom du mont Lanke, où la légende veut qu'un bûcheron, en regardant deux immortels jouer au go, vit pourrir le manche de sa hache. | [en](https://en.wikipedia.org/wiki/Quzhou-Lanke_Cup) | [SL](https://senseis.xmp.net/?QuzhouLankeCup) |
| 261 | Tournois pro | Ryongsang | Gris | Tournoi coréen télévisé en parties rapides, équivalent du Ryusei japonais et du Longxing chinois. |  | [SL](https://senseis.xmp.net/?Ryongsang) |
| 262 | Tournois pro | Ryusei | Gris | Tournoi japonais télévisé en parties rapides, équivalent du Longxing chinois et du Ryongsang coréen. |  | [SL](https://senseis.xmp.net/?Ryusei) |
| 263 | Tournois pro | Samsung Cup | Gris | Tournoi mondial coréen, l'un des plus prestigieux. | [fr](https://fr.wikipedia.org/wiki/Coupe_Samsung) | [SL](https://senseis.xmp.net/?SamsungCup) |
| 264 | Tournois pro | Senko Cup | Gris | Tournoi mondial féminin organisé au Japon. |  | [SL](https://senseis.xmp.net/?SenkoCup) |
| 265 | Tournois pro | Supreme Player | Gris | Tournoi coréen créé en 2020 : une ligue désigne le challenger du tenant, en cinq parties. Le vainqueur reçoit 70 millions de wons. |  | [SL](https://senseis.xmp.net/?SupremePlayer) |
| 266 | Tournois pro | Tianyuan | Gris | Titre taïwanais, équivalent du Tengen japonais. Première édition en 2002. Le vainqueur reçoit 1 million de dollars taïwanais. |  | [SL](https://senseis.xmp.net/?TaiwanTianyuan) |
| 267 | Variantes | Atarigo | Gris | Le premier qui capture gagne : la variante d'initiation. | [en](https://en.wikipedia.org/wiki/Capture_go) | [SL](https://senseis.xmp.net/?Atarigo) |
| 268 | Variantes | Petango | Gris | Le mélange de la pétanque et du go : on lance les pierres sur le goban. |  |  |
| 269 | Variantes | Rengo | Gris | Le go en équipes : les partenaires jouent à tour de rôle, sans se concerter. | [en §](https://en.wikipedia.org/wiki/Go_variants#Rengo) | [SL](https://senseis.xmp.net/?Rengo) |
| 270 | Variantes | Unicolor | Gris | Les deux joueurs jouent avec des pierres de même couleur, et doivent se souvenir de qui est qui. | [en §](https://en.wikipedia.org/wiki/Go_variants#One_Color_Go) | [SL](https://senseis.xmp.net/?OneColourGo) |

### 6.3 Notes de contenu

- Les cartes Communauté Vert et Bleu des animaux impériaux sont les vainqueurs de la FulguroGo Cup. Il y a un animal
  par saison : l'adulte (Bleu) est le vainqueur de la catégorie libre, le petit (Vert) celui de la catégorie
  Novice-Elite. La saison 2018, jouée en
  une seule catégorie, n'a que son adulte, le Poulpe.
- Dai Junfu et Lai Junfu sont deux joueurs distincts.
- Mingren, Tianyuan et Guoshou existent deux fois : le titre chinois (Vert) et le titre taïwanais (Gris). La
  description dit lequel est lequel.
- Un titre national est Vert quand il tient dans son pays le rang des sept grands titres japonais : le Myeongin en
  Corée ; le Mingren, le Tianyuan, le Qisheng et le Guoshou en Chine. Les tournois mondiaux, les tournois de sponsor,
  les tournois rapides et les titres taïwanais sont Gris.
- La carte Quzhou-Lanke Cup est le tournoi chinois créé en 2006, pas le Quzhou-Lanke Cup World Go Open créé en 2023.
- FOX et IGS sont des cartes, bien que le serveur ne suive plus ces plateformes : une carte n'est pas une intégration.
- Le consentement des membres représentés sur les cartes Communauté est acquis.

## 7. Économie

### 7.1 Gagner des points

Une seule source : les **parties gold**, définies comme pour la validité FGC. Une partie finie sur KGS ou OGS, en
**19×19**, **sans handicap**, avec un **komi strictement compris entre 6 et 9**, dont **les deux joueurs** sont des
membres ayant lié leur compte. Qu'elle soit classée ou non ne compte pas.

| Règle | Valeur |
|-------|--------|
| Gain par partie gold | **dégressif dans la journée**, à chacun des deux joueurs : `max(1, arrondi(1 600 × 0,75^(rang − 1)))` |
| Rang | Rang de la partie parmi celles du joueur ce jour-là (jour calendaire, heure de Paris), à partir de 1, dans l'ordre où elles sont créditées (§11.2) |
| Partie annulée après coup par la plateforme | les points déjà crédités restent acquis |

Il n'y a pas de plafond : chaque partie rapporte au moins 1 point, mais la valeur s'effondre vite. À partir de la
26ᵉ partie de la journée, elle ne vaut plus que 1 point.

| Rang dans la journée | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 20 | 26 et plus |
|----------------------|--:|--:|--:|--:|--:|--:|--:|--:|--:|---:|---:|-----------:|
| Points | 1 600 | 1 200 | 900 | 675 | 506 | 380 | 285 | 214 | 160 | 120 | 7 | 1 |
| Total de la journée | 1 600 | 2 800 | 3 700 | 4 375 | 4 881 | 5 261 | 5 546 | 5 760 | 5 920 | 6 040 | 6 381 | → ~6 400 |

Il n'y a ni défis, ni événements, ni autre source. La première partie du jour paie 2 packs, la deuxième 1,5, la
troisième un peu plus d'un. En pratique, une journée ne rapporte jamais beaucoup plus de 6 400 points, soit 8 packs :
la dégressivité tient le rôle d'un plafond sans en avoir le couperet, et elle ne pénalise pas les journées normales
(§8.1).

### 7.2 Dépenser

- **Un pack coûte 800 points.** Le joueur l'ouvre depuis le site, quand il veut.
- Les doubles sont proposés au recyclage :

| Rareté | Points de recyclage |
|--------|--------------------:|
| Gris   | 10                  |
| Vert   | 25                  |
| Bleu   | 50                  |
| Violet | 150                 |
| Gold   | 500                 |

Une Gold recyclée paie près des deux tiers d'un pack. En fin de collection, quand presque tout est double, un pack
recyclé en entier rapporte ~146 points, soit 18 % de son prix. Sur une collection complète, le recyclage rembourse 12 %
de ce qui est dépensé en packs, en moyenne ~98 points par pack, et allonge le budget de 14 %. C'est lui qui amortit la
malchance de fin de collection, que le pity laisse volontairement au hasard (§3.4).

## 8. Équilibre

### 8.1 Activité de référence

Mesure sur la base de dev, du 26/08/2026 au 24/09/2026 : 149 parties gold jouées en 30 jours, 72 joueurs actifs (au
moins une partie gold) sur 304 membres liés.

| Parties gold par joueur actif sur 30 jours | Valeur |
|--------------------------------------------|-------:|
| Médiane                                    | 3      |
| Moyenne                                    | 4,1    |
| 75ᵉ centile                                | 5      |
| Maximum                                    | 28     |

Par jour et par joueur, 98 % des journées comptent une ou deux parties. Le seul cas au-delà de 3 est une série de
13 parties entre deux joueurs le même jour, dont la dégressivité (§7.1) ramène les dernières à quelques points.

⚠ Un mois de début de saison ne dit rien de l'été ni du creux de l'hiver. La mesure est à refaire sur une saison
complète, et le barème du §7.1 à recaler si la médiane bouge.

### 8.2 Durée de complétion

Avec les poids du §3, l'anti-doublon, les deux pity et le recyclage réinvesti en packs :

| | 10ᵉ centile | Médiane | 90ᵉ centile |
|---|---:|---:|---:|
| Packs ouverts | 218 | 304 | 407 |
| Points à gagner en parties | 154 600 | 213 500 | 282 400 |

Aux extrêmes, le 1ᵉʳ centile finit en 173 packs et le 99ᵉ en 495.

Pack auquel chaque rareté est complète, en médiane :

| Gris | Vert | Bleu | Violet | Gold |
|-----:|-----:|-----:|-------:|-----:|
| 155  | 159  | 167  | 154    | 304  |

Les Gold sont la dernière rareté complétée dans 97 % des collections : c'est sur elles que se joue la fin.

Les points dépendent de la façon dont les parties se répartissent dans la journée (§7.1). Le tableau suppose une
partie par jour, à 1 600 points. C'est le cas le plus courant (§8.1) ; deux parties le même jour rapportent 2 800
points au lieu de 3 200, ce qui allonge un peu la durée :

| Parties gold par mois, une par jour | Mois pour finir (chanceux / médian / malchanceux) |
|------------------------------------:|--------------------------------------------------:|
| 1                                   | 97 / 134 / 177                                    |
| **3 (joueur médian)**               | **32 / 45 / 59**                                  |
| 5                                   | 19 / 27 / 35                                      |
| 10                                  | 10 / 13 / 18                                      |
| 20                                  | 5 / 7 / 9                                         |

Le joueur actif médian finit en trois ans et neuf mois (45 mois). À 5 parties par mois, il faut un peu plus de deux
ans ; à 10, un an.

Un joueur qui joue tous les jours :

| Parties par jour | Points par jour | Jours pour finir (chanceux / médian / malchanceux) |
|-----------------:|----------------:|---------------------------------------------------:|
| 1                | 1 600           | 97 / 134 / 177                                     |
| 2                | 2 800           | 56 / 77 / 101                                      |
| 3                | 3 700           | 42 / 58 / 77                                       |
| 5                | 4 881           | 32 / 44 / 58                                       |
| 10               | 6 040           | 26 / 36 / 47                                       |
| 20               | 6 381           | 25 / 34 / 45                                       |

Au-delà d'une dizaine de parties par jour, jouer plus ne fait presque plus gagner de temps : même à 20 parties par
jour, il faut environ cinq semaines en médiane, et un joueur chanceux ne descend pas sous 25 jours.

### 8.3 Poids des mécanismes

| Scénario (packs ouverts) | Médiane | 90ᵉ centile |
|--------------------------|--------:|------------:|
| Tirage pondéré seul | 760 | 1 157 |
| + anti-doublon Violet et Gold sur le slot Vert+ | 376 | 512 |
| + pity sur carte nouvelle | 320 | 467 |
| + pity Gold et Violet | 304 | 407 |

L'anti-doublon fait l'essentiel : il divise par deux le nombre de packs nécessaires, parce qu'il règle les cartes les
plus longues à venir. Le pity sur carte nouvelle retire les Grises de la fin de collection. Le pity Gold et Violet
déplace peu la médiane, mais il coupe la queue de distribution (90ᵉ centile : 467 → 407), c'est-à-dire les joueurs
malchanceux. C'est exactement son rôle.

### 8.4 Extensions

À chaque extension, les poids restent les mêmes, mais les parts par couleur, le temps de complétion et le barème de
gain sont à recalculer (§3.1). Le taux de complétion affiché de chaque joueur baisse le jour de la sortie : c'est
voulu, et c'est la seule conséquence, puisqu'il n'y a pas de badges.

## 9. Membres

- **Membre banni** : ses cartes Communauté restent des cartes comme les autres, dans le set et dans les collections.
- **Membre purgé** (parti du Discord, supprimé par `CleanService` après un jour de grâce) : sa collection, son solde et
  ses registres de points sont supprimés avec le reste de ses données.

## 10. Discord

Deux annonces, sur le canal de notification :

- une **Gold obtenue**, quand elle est nouvelle pour le joueur (un double n'est pas annoncé) ;
- un **album complet**, quand un joueur atteint 100 % du set courant.

## 11. Mise en place sur fulguro-server

Le système entre dans l'architecture existante sans rien de nouveau : un module `cards` sur le modèle des autres
(`CardsModule`, `CardsDatabaseAccessor`, modèles sql2o, routes sur `Api`).

### 11.1 Données

| Table | Contenu |
|-------|---------|
| `cards` | Le catalogue (§4). Corriger une carte ou ajouter une extension, c'est un script SQL appliqué au déploiement. |
| `card_collection` | `(discord_id, slug)` → nombre d'exemplaires. |
| `card_ledger` | Un mouvement de points par ligne : gain de partie (avec son rang dans la journée), achat de pack, recyclage. |
| `card_wallet` | Le solde matérialisé et les trois compteurs de pity, par joueur : packs sans carte nouvelle, cartes depuis la dernière Gold, cartes depuis la dernière Violette. |
| `card_openings` | Le journal des ouvertures : joueur, date, cartes, compteurs de pity avant et après. |

Le solde est **stocké et vérifiable** : il doit toujours égaler la somme du registre. Le stockage permet le débit
atomique, le registre permet de répondre à une contestation avec des faits.

### 11.2 Crédit des points

Un service périodique parcourt les parties gold qui n'ont pas encore été créditées, et écrit un gain par (partie, joueur)
dans `card_ledger`. La clé `(gold_id, discord_id)` rend l'écriture idempotente sans curseur, sur le modèle de
`house_points`.

Trois contraintes :

- **Le rang d'une partie** se compte sur la date de la partie en heure de Paris (`DATE_ZONE`) et **dans l'ordre de
  crédit** : c'est 1 + le nombre de gains déjà écrits pour ce joueur à cette date. Un gain écrit ne change donc jamais.
  Ranger par heure de la partie serait plus juste, mais KGS est scrapé avec du retard : une partie arrivée tard
  s'intercalerait avant des parties déjà créditées, et il faudrait réécrire leurs gains dans le registre. Les ticks ne
  se chevauchent pas (`PeriodicFlowService`), donc deux crédits ne peuvent pas prendre le même rang.
- **Le registre ne dépend pas de la survie de la partie.** `CleanService` supprime les parties de plus de 32 jours et
  `OgsService` celles qu'OGS annule. Aucune de ces suppressions ne doit toucher `card_ledger`, puisque les points
  d'une partie annulée restent acquis.
- **La sélection ne peut pas réutiliser la vue `fgc_validity_games` telle quelle**, qui est bornée à 30 jours et ne
  sert qu'à compter. Elle en reprend les critères. Le service doit passer au moins une fois dans les 32 jours de
  rétention des parties, ce que n'importe quel intervalle de l'ordre de la minute garantit.

### 11.3 Ouverture d'un pack et recyclage

- **Routes authentifiées.** Sans authentification, n'importe qui pourrait dépenser les points d'un autre et recycler
  ses cartes. `api/auth/SessionResolver.kt` résout déjà la session Discord du navigateur pour les routes admin, et les
  routes cartes passent par lui.
- **Une seule transaction** par ouverture : débit conditionnel (`UPDATE card_wallet SET points = points - 800 WHERE
  discord_id = :id AND points >= 800`, puis contrôle du nombre de lignes touchées), écriture des cartes, des compteurs
  de pity, du registre et du journal. Sinon un double clic ouvre deux packs pour un seul débit. Le rate limit de l'API
  ne protège pas de ça.
- Le recyclage suit le même schéma : décrément de l'exemplaire, crédit du registre et du solde dans la même
  transaction.

### 11.4 Purge et annonces

- `CleanService` ajoute les tables `card_collection`, `card_ledger`, `card_wallet` et `card_openings` à ce qu'il
  supprime pour un membre purgé.
- Les annonces du §10 passent par `DiscordBot.sendMessageEmbeds`, sur le modèle de `HouseNotifier`, en best-effort :
  une annonce ratée coûte une ligne de log, jamais une ouverture.
- dev et prod sont isolés : un tirage en local écrit dans `fg_dev` et passe par le bot de test. Aucune ressource
  externe n'est partagée.

### 11.5 Avant publication

- Refaire la mesure d'activité du §8.1 sur une saison complète.
- Tests : simuler des ouvertures en masse pour vérifier les parts réelles, que le slot Vert+ est toujours Vert ou
  mieux, que l'anti-doublon et les deux pity rendent bien une carte manquante, et que deux ouvertures concurrentes ne
  débitent jamais sous zéro.
