# Cartes à collectionner — spécification

Ce document décrit le système de cartes à collectionner de FulguroGo : contenu, tirage, économie, équilibre, et ce que
sa mise en place demande à fulguro-server. C'est la base du futur plan d'implémentation.

Les chiffres d'équilibre viennent d'un calcul exact quand il existe, sinon d'une simulation Monte-Carlo (500
collections simulées, graine fixe). L'activité des joueurs est mesurée sur la base de dev (§9.1).

---

## 1. Principe

- Un album d'étiquettes thématique Go. Pas de gameplay compétitif ni de carte « overpower » : l'objectif est la
  collection.
- L'album est **permanent** : une collection se garde d'une saison à l'autre, et une **extension** par an ajoute des
  cartes.
- La progression s'affiche en **taux de complétion**, global, par rareté et par catégorie. Il n'y a pas de badges.
- Les points qui achètent les packs se gagnent **uniquement en jouant des parties gold** (§8.1).
- Objectif de durée : un joueur actif médian complète l'album en **plus de deux ans**, de l'ordre de trois ans.

## 2. Raretés et effectifs

| Rareté       | Couleur | Symbole | Cartes  | % du set |
|--------------|---------|---------|--------:|---------:|
| Commune      | Gris    | ⚪      | 106     | 44,7 %   |
| Inhabituelle | Vert    | 🟢      | 65      | 27,4 %   |
| Rare         | Bleu    | 🔵      | 36      | 15,2 %   |
| Épique       | Violet  | 🟣      | 19      | 8,0 %    |
| Mythique     | Gold    | 🟡      | 11      | 4,6 %    |
| **Total**    |         |         | **237** |          |

Les effectifs ne sont pas des quotas. Un joueur va dans la rareté que lui donne son palmarès (§6) et le tirage s'adapte
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
  Les chiffres de ce document valent pour le set de 237 cartes et sont à refaire à chaque extension.

### 3.2 Structure d'un pack

Un pack contient **5 cartes** :

- **4 slots standards**, tirés indépendamment parmi toutes les cartes ;
- **1 slot garanti**, tiré parmi les cartes Vert ou mieux, avec **anti-doublon** : la rareté est tirée au prorata des
  poids, puis la carte est choisie parmi celles de cette rareté **que le joueur n'a pas encore**. S'il les a toutes,
  n'importe laquelle de la rareté.

L'ordre d'affichage des cartes est mélangé.

Avec les effectifs actuels, les poids donnent :

| Rareté | Part d'un slot standard | Part du slot garanti |
|--------|------------------------:|---------------------:|
| Gris   | 68,7 %                  | —                    |
| Vert   | 21,1 %                  | 67,4 %               |
| Bleu   | 7,0 %                   | 22,4 %               |
| Violet | 2,5 %                   | 7,9 %                |
| Gold   | 0,7 %                   | 2,3 %                |

### 3.3 Ce que contient un pack

| Rareté | Cartes par pack | Au moins une dans le pack | Une carte donnée (hors anti-doublon) |
|--------|----------------:|--------------------------:|-------------------------------------:|
| Gris   | 2,75            | 99,0 %                    | tous les 39 packs                    |
| Vert   | 1,52            | 87,4 %                    | tous les 43 packs                    |
| Bleu   | 0,50            | 42,0 %                    | tous les 71 packs                    |
| Violet | 0,18            | 16,6 %                    | tous les 107 packs                   |
| Gold   | 0,05            | 5,0 %                     | tous les 214 packs                   |

En moyenne, une Bleue tous les 2 packs, une Violette tous les 5 à 6 packs, une Gold tous les 20 packs.

### 3.4 Pity sur carte manquante

Un filet de sécurité, réglé pour ne se déclencher que dans environ 5 % des cas, et qui donne toujours une carte **que
le joueur n'a pas**.

- **Gold** : après **300 cartes** tirées sans Gold, la carte suivante est une Gold manquante (déclenchement : ~4,5 %).
- **Violet** : après **65 cartes** tirées sans Violette ni Gold, la carte suivante est une Violette manquante
  (déclenchement : ~4,6 %).

Règles de détail :

- Les compteurs courent d'un pack à l'autre et sont propres à chaque joueur.
- Une Gold remet les deux compteurs à zéro, une Violette seulement le compteur Violet. Un double compte aussi.
- Le pity s'applique au slot qui arrive, standard ou garanti. Si les deux sont dus en même temps, la Gold passe
  d'abord.
- Si le joueur possède déjà toutes les cartes de la rareté, le pity donne une carte quelconque de cette rareté.

Sur une collection complète, 0,26 % des cartes tirées viennent du pity.

## 4. Algorithme (pseudo-code)

```kotlin
import kotlin.random.Random

enum class Rarity(val weight: Double, val recycleValue: Int) {
    GRIS(10.0, 10), VERT(5.0, 25), BLEU(3.0, 50), VIOLET(2.0, 150), GOLD(1.0, 500)
}

data class Card(
    val slug: String,
    val name: String,
    val category: String,
    val rarity: Rarity,
    val description: String,
)

data class PityCounters(val sinceGold: Int, val sinceViolet: Int)

class PackOpener(private val catalog: List<Card>, private val random: Random = Random.Default) {
    private val guaranteedPool = catalog.filter { it.rarity != Rarity.GRIS }

    fun open(owned: Set<String>, start: PityCounters): Pair<List<Card>, PityCounters> {
        val pack = mutableListOf<Card>()
        var pity = start
        repeat(5) { slot ->
            val card = when {
                pity.sinceGold >= GOLD_PITY -> missingOf(Rarity.GOLD, owned + pack.slugs())
                pity.sinceViolet >= VIOLET_PITY -> missingOf(Rarity.VIOLET, owned + pack.slugs())
                slot < 4 -> weighted(catalog)
                else -> missingOf(weighted(guaranteedPool).rarity, owned + pack.slugs())
            }
            pack.add(card)
            pity = when (card.rarity) {
                Rarity.GOLD -> PityCounters(0, 0)
                Rarity.VIOLET -> PityCounters(pity.sinceGold + 1, 0)
                else -> PityCounters(pity.sinceGold + 1, pity.sinceViolet + 1)
            }
        }
        return pack.shuffled(random) to pity
    }

    // Une carte de la rareté que le joueur n'a pas ; n'importe laquelle s'il les a toutes.
    private fun missingOf(rarity: Rarity, owned: Set<String>): Card {
        val pool = catalog.filter { it.rarity == rarity }
        return pool.filter { it.slug !in owned }.ifEmpty { pool }.random(random)
    }

    private fun weighted(pool: List<Card>): Card {
        var x = random.nextDouble(pool.sumOf { it.rarity.weight })
        for (card in pool) {
            x -= card.rarity.weight
            if (x < 0) return card
        }
        return pool.last()
    }

    private fun List<Card>.slugs() = mapTo(mutableSetOf()) { it.slug }

    companion object {
        const val GOLD_PITY = 300
        const val VIOLET_PITY = 65
    }
}
```

Pas d'argent réel : `kotlin.random.Random` suffit, un RNG cryptographique n'apporte rien. Le tirage se fait côté
serveur, jamais sur le site.

## 5. Catalogue

Chaque carte porte :

| Champ | Rôle |
|-------|------|
| `slug` | Identifiant stable, jamais modifié, même si le nom est corrigé : c'est lui que référencent les collections. |
| `name` | Nom affiché. Deux cartes peuvent porter le même (« Tengen » Ouverture et « Tengen » Tournoi). |
| `category` | Catégorie d'album. |
| `rarity` | Rareté, qui fixe le poids et la valeur de recyclage. |
| `description` | Texte de la carte. C'est elle qui distingue les homonymes. |
| visuel | Côté site. Les joueurs réels sont illustrés, pas photographiés. |

Chaque carte est unique dans l'album : un exemplaire suffit à la compter.

## 6. Classement des joueurs

La rareté d'un joueur se fixe **au palmarès**, quelle que soit l'époque.

| Rareté | Critère |
|--------|---------|
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

Le classement de la liste (§7) a été fait de mémoire. Chaque joueur porte un niveau de certitude :

- ✓ : palmarès connu, classement sûr ;
- ~ : classement probable, à confirmer ;
- ? : palmarès inconnu ou incertain. Le joueur reste dans sa rareté d'origine en attendant vérification.

Les ~ et les ? sont à vérifier avant publication (§12).

## 7. Liste des cartes

### 7.1 Catégories

| Catégorie | Contenu | Gris | Vert | Bleu | Violet | Gold | Total |
|-----------|---------|-----:|-----:|-----:|-------:|-----:|------:|
| Joueurs | Joueurs professionnels et amateurs, classés au palmarès (§6) | 30 | 43 | 25 | 14 | 8 | **120** |
| Commu | Maisons, compétitions, vainqueurs de la FGC, lieux du lore et figures de la communauté | 7 | 9 | 9 | 4 | 3 | **32** |
| Formes complexes | Formes de plusieurs pierres, bonnes ou mauvaises | 9 | 2 | — | 1 | — | **12** |
| Tournois pro | Tournois mondiaux et grands titres japonais | 5 | 7 | — | — | — | **12** |
| Fuseki | Stratégies d'ouverture, classiques ou non | 6 | 2 | 2 | — | — | **10** |
| Meta | Concepts et vocabulaire du jeu | 10 | — | — | — | — | **10** |
| Institutions & organisation | Fédérations, associations et organisation du go | 9 | — | — | — | — | **9** |
| Formes simples | Coups élémentaires entre deux pierres | 8 | — | — | — | — | **8** |
| Ouvertures | Points de coin et premiers coups | 7 | — | — | — | — | **7** |
| Matériel | Objets du joueur de go | 6 | — | — | — | — | **6** |
| Variantes | Autres façons de jouer au go | 4 | 2 | — | — | — | **6** |
| Serveurs | Serveurs de go en ligne | 5 | — | — | — | — | **5** |
| **Total** | | **106** | **65** | **36** | **19** | **11** | **237** |

Joueurs et Commu sont les deux seules catégories présentes dans toutes les raretés ; six catégories n'ont que des
cartes Gris.

### 7.2 Cartes

Le niveau de certitude du classement (§6) suit la rareté des joueurs. ⚠ dans une description : texte à compléter ou à
confirmer.

#### ⚪ Gris — 106 cartes

| Catégorie | Titre | Rareté | Description |
|-----------|-------|--------|-------------|
| Commu | Saison FGC | Gris | Une saison de la FulguroGo Cup, la série de tournois de la communauté, en catégories libre et Novice-Elite. |
| Commu | Maisons d'Aurak | Gris | La compétition des quatre maisons, nées de la Partie des Ruptures sur la plaine d'Aurak. |
| Commu | Ligue d'Aurak | Gris | La ligue de la communauté, un match par quinzaine contre un membre d'une autre maison. Son vainqueur accède directement à la finale de la FGC. |
| Commu | Fils du Froid | Gris | Maison des combattants, exilée vers le nord : « Le meilleur coup est celui qui brise. » |
| Commu | Nexus Alpha | Gris | Maison des calculateurs, retranchée dans les souterrains de quartz : « Chaque coup est une équation. » |
| Commu | Sabre Silencieux | Gris | Maison du bushido, retirée dans les forêts de brume : « Un coup, un destin ! » |
| Commu | Lunaires d'Æther | Gris | Maison des inventeurs, partie vers les îles célestes : « Pourquoi jouer comme hier ? » |
| Formes complexes | Double hane | Gris | Deux hane joués coup sur coup, souvent au bord pour réduire un territoire. |
| Formes complexes | Double hanging connection | Gris | Deux connexions pendantes côte à côte, qui protègent deux points de coupe à la fois. |
| Formes complexes | Équerre | Gris | La forme en bouche : cinq pierres autour d'un point vide, pensées pour faire un œil plus que pour connecter. |
| Formes complexes | Hanging connection | Gris | La connexion pendante : une pierre adverse qui viendrait couper tomberait aussitôt en atari. |
| Formes complexes | Inu no kao | Gris | La « tête de chien », ou bouteille de saké : un keima joué depuis deux pierres en ikken tobi. Le proverbe la dit mauvaise, à l'inverse de la tête de cheval. |
| Formes complexes | Gueule du tigre (neko no kao) | Gris | Trois pierres reliées par deux diagonales opposées, la base de la connexion pendante. |
| Formes complexes | Nœud de bambou | Gris | Deux paires de pierres parallèles séparées d'une ligne : une connexion impossible à couper. |
| Formes complexes | Ponnuki | Gris | Le losange de quatre pierres laissé par la capture d'une pierre. « Un ponnuki vaut trente points. » |
| Formes complexes | Table | Gris | Quatre pierres proches de l'Équerre, qui restent connectées tout en gardant un potentiel d'œil. Moins solide que le nœud de bambou. |
| Formes simples | Hane | Gris | Un coup en diagonale qui contourne une pierre adverse au contact. |
| Formes simples | Hazama tobi | Gris | Le saut en diagonale, qui laisse une intersection vide entre deux pierres. |
| Formes simples | Ikken tobi | Gris | Le saut d'un espace en ligne droite. « L'ikken tobi n'est jamais un mauvais coup. » |
| Formes simples | Keima | Gris | Le saut du cavalier : léger et rapide, mais coupable. |
| Formes simples | Kosumi | Gris | Le coup en diagonale : lent, mais presque impossible à couper. |
| Formes simples | Niken tobi | Gris | Le saut de deux espaces en ligne droite, plus rapide et plus fragile que l'ikken tobi. |
| Formes simples | Nobi | Gris | Prolonger en ligne droite, pierre contre pierre : le coup le plus solide qui soit. |
| Formes simples | Ogeima | Gris | Le grand cavalier, un saut plus étendu que le keima. |
| Fuseki | Chinois | Gris | Hoshi, komoku et une extension basse sur le côté : l'ouverture popularisée par les joueurs chinois. |
| Fuseki | Kobayashi | Gris | L'ouverture du style de Kobayashi Koichi, bâtie autour d'un komoku et d'une approche rapide du coin adverse. |
| Fuseki | Orthodoxe | Gris | L'ouverture classique : un hoshi et un shimari qui le regarde. |
| Fuseki | Petit chinois | Gris | La variante moderne du chinois, avec une extension plus proche du coin. |
| Fuseki | Sanrensei | Gris | Trois hoshi alignés sur un même côté, pour un jeu d'influence tourné vers le centre. |
| Fuseki | Shusaku | Gris | L'ouverture de Honinbo Shusaku : trois komoku et le célèbre kosumi de Shusaku. |
| Institutions & organisation | AGA | Gris | L'American Go Association, la fédération des États-Unis. |
| Institutions & organisation | Chinese Weiqi Association (Zhōngguó Wéiqí Xiéhuì) | Gris | L'association qui organise le go professionnel en Chine. |
| Institutions & organisation | EGF | Gris | La European Go Federation, qui fédère les associations d'Europe et délivre un statut professionnel européen. |
| Institutions & organisation | Échelle kyu/dan | Gris | Le système de grades du go : les kyu pour progresser, les dan pour les joueurs confirmés. |
| Institutions & organisation | FFG | Gris | La Fédération française de go. |
| Institutions & organisation | IGF | Gris | L'International Go Federation, qui fédère les associations nationales du monde entier. |
| Institutions & organisation | Insei | Gris | Élève d'une école professionnelle, en formation pour devenir pro. |
| Institutions & organisation | Japanese Go Association (Nihon Ki-in) | Gris | La principale organisation du go professionnel japonais, fondée en 1924. |
| Institutions & organisation | Korean Baduk Association (Hanguk Kiwon) | Gris | L'organisation du baduk professionnel coréen. |
| Joueurs | Michael Redmond | Gris ✓ | Américain, premier Occidental 9ᵉ dan professionnel au Japon, commentateur des parties d'AlphaGo. |
| Joueurs | Antti Törmänen | Gris ✓ | Finlandais devenu professionnel à la Nihon Ki-in. |
| Joueurs | Motoki Noguchi | Gris ✓ | Joueur japonais installé en France, figure du go français. |
| Joueurs | Wang Yuanjun | Gris ✓ | Professionnel chinois, commentateur et pédagogue. |
| Joueurs | Maeda Nobuaki | Gris ~ | Professionnel japonais surnommé le « dieu du tsumego » pour ses recueils de problèmes. |
| Joueurs | Tanguy Le Calvé | Gris ~ | Joueur français, parmi les meilleurs du pays. |
| Joueurs | Sada Atsushi | Gris ~ | Professionnel japonais. ⚠ À compléter. |
| Joueurs | Ali Jabarin | Gris ? | Joueur israélien, parmi les premiers professionnels européens. ⚠ À compléter. |
| Joueurs | Andrii Kravets | Gris ? | Joueur ukrainien, professionnel européen. ⚠ À compléter. |
| Joueurs | Benjamin Dréan-Guénaïzia | Gris ? | Joueur français, professionnel européen. ⚠ À compléter. |
| Joueurs | Inseong Hwang | Gris ? | Joueur coréen installé en France. ⚠ À compléter. |
| Joueurs | Jan Simara | Gris ? | Joueur tchèque, professionnel européen. ⚠ À compléter. |
| Joueurs | Mateusz Surma | Gris ? | Joueur polonais, professionnel européen. ⚠ À compléter. |
| Joueurs | Pavol Lisy | Gris ? | Joueur slovaque, parmi les premiers professionnels européens. ⚠ À compléter. |
| Joueurs | Stanislaw Frejlak | Gris ? | Joueur polonais. ⚠ À compléter. |
| Joueurs | Hoshiai Shiho | Gris ? | Professionnelle japonaise. ⚠ À compléter. |
| Joueurs | Suzuki Ayumi | Gris ? | Professionnelle japonaise. ⚠ À compléter. |
| Joueurs | Cho Seungah | Gris ? | ⚠ À rédiger. |
| Joueurs | Oh Jeonga | Gris ? | ⚠ À rédiger. |
| Joueurs | Kim Dohyup | Gris ? | ⚠ À rédiger. |
| Joueurs | Kim Myeonghoon | Gris ? | Professionnel coréen. ⚠ À compléter. |
| Joueurs | Lee Jihyun | Gris ? | ⚠ À rédiger. |
| Joueurs | Park Mingyu | Gris ? | ⚠ À rédiger. |
| Joueurs | Chen Qirui | Gris ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Liao Yuanhe | Gris ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Tong Mengcheng | Gris ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Tang Jiawen | Gris ? | ⚠ À rédiger. |
| Joueurs | Yu Zhengqi | Gris ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Dai Junfu | Gris ? | ⚠ À rédiger. |
| Joueurs | Zhou Hongyu | Gris ? | Professionnelle chinoise. ⚠ À compléter. |
| Matériel | Bols (goke) | Gris | Les deux bols, souvent en bois, qui contiennent les pierres de chaque joueur. |
| Matériel | Éventail | Gris | L'éventail que tiennent les professionnels japonais pendant leurs parties. |
| Matériel | Horloge | Gris | La pendule qui décompte le temps de réflexion, jusqu'au byo-yomi. |
| Matériel | Kifu | Gris | La feuille où l'on note les coups d'une partie, numéro par numéro. |
| Matériel | Pierre (ishi) | Gris | Les pierres noires et blanches ; les plus belles sont en ardoise et en coquillage. |
| Matériel | Plateau (goban) | Gris | Le plateau de 19 × 19 lignes, traditionnellement taillé dans le kaya. |
| Meta | Chuban | Gris | Le milieu de partie, là où se livrent les combats. |
| Meta | Fuseki | Gris | L'ouverture, quand les joueurs se partagent le plateau à grands traits. |
| Meta | Joseki | Gris | Une séquence de coin jugée équilibrée pour les deux joueurs. |
| Meta | Komi | Gris | Les points donnés à Blanc pour compenser l'avantage du premier coup. |
| Meta | Yose | Gris | La fin de partie, où chaque point de frontière se dispute. |
| Meta | Geta | Gris | Le filet : une capture à distance dont la pierre adverse ne peut plus sortir. |
| Meta | Glissade du singe | Gris | Le saut sur la première ligne, sous une pierre adverse, pour entamer un territoire par le bord. |
| Meta | Point vital | Gris | Le point décisif d'une forme, qui fait vivre ou mourir un groupe. |
| Meta | Shicho | Gris | L'échelle : une poursuite en atari successifs qui traverse le plateau en zigzag. |
| Meta | Triangle de politesse | Gris | La zone du coin supérieur droit où, par politesse, on joue traditionnellement son premier coup. |
| Ouvertures | Hoshi | Gris | Le point étoile 4-4 : rapide et tourné vers l'influence, mais il laisse l'invasion au 3-3. |
| Ouvertures | Komoku | Gris | Le 3-4, l'ouverture de coin classique, équilibrée entre territoire et influence. |
| Ouvertures | Mokuhazushi | Gris | Le 3-5, qui vise le côté plutôt que le coin. |
| Ouvertures | Sansan | Gris | Le 3-3, qui prend le coin d'un coup, au prix de l'influence. |
| Ouvertures | Shimari | Gris | Deux pierres qui ferment un coin et rendent l'invasion difficile. |
| Ouvertures | Takamoku | Gris | Le 4-5, orienté vers l'influence. |
| Ouvertures | Tengen | Gris | Le point central du goban. |
| Serveurs | FOX | Gris | Le serveur chinois, l'un des plus fréquentés du monde. |
| Serveurs | Go Quest | Gris | L'application des parties rapides sur petits plateaux. |
| Serveurs | IGS Pandanet | Gris | L'Internet Go Server, l'un des tout premiers serveurs de go en ligne. |
| Serveurs | KGS | Gris | Le serveur historique de la communauté occidentale. |
| Serveurs | OGS | Gris | L'Online Go Server, où se joue la Ligue d'Aurak. |
| Tournois pro | Ing Cup | Gris | Le tournoi mondial joué tous les quatre ans aux règles Ing, surnommé les « Jeux olympiques du go ». |
| Tournois pro | LG Cup | Gris | Tournoi mondial coréen. |
| Tournois pro | Ryusei | Gris | Tournoi japonais télévisé en parties rapides. |
| Tournois pro | Samsung Cup | Gris | Tournoi mondial coréen, l'un des plus prestigieux. |
| Tournois pro | Senko Cup | Gris | Tournoi mondial féminin organisé au Japon. |
| Variantes | Atarigo | Gris | Le premier qui capture gagne : la variante d'initiation. |
| Variantes | Petango | Gris | Le mélange de la pétanque et du go : on lance les pierres sur le goban. |
| Variantes | Rengo | Gris | Le go en équipes : les partenaires jouent à tour de rôle, sans se concerter. |
| Variantes | Unicolor | Gris | Les deux joueurs jouent avec des pierres de même couleur, et doivent se souvenir de qui est qui. |

#### 🟢 Vert — 65 cartes

| Catégorie | Titre | Rareté | Description |
|-----------|-------|--------|-------------|
| Commu | Soku le Marmotton Impérial | Vert | Vainqueur de la FGC 2019, catégorie Novice-Elite. |
| Commu | Hebus le Salamandron Impérial | Vert | Vainqueur de la FGC 2020, catégorie Novice-Elite. |
| Commu | Savagning le Choupisson Impérial | Vert | Vainqueur de la FGC 2021, catégorie Novice-Elite. |
| Commu | Lilanlu le Louveteau Impérial | Vert | Vainqueur de la FGC 2022, catégorie Novice-Elite. |
| Commu | Kaigito le Chauve-Souriceau Impérial | Vert | Vainqueur de la FGC 2023, catégorie Novice-Elite. |
| Commu | Ashruidan la Chenille Impériale | Vert | Vainqueur de la FGC 2024, catégorie Novice-Elite. |
| Commu | Hyoga le Mammouthon Impérial | Vert | Vainqueur de la FGC 2025, catégorie Novice-Elite. |
| Commu | Yanae l'Oursonne Impériale | Vert | Vainqueur de la FGC 2026, catégorie Novice-Elite. |
| Commu | Tournoi du Mont Tengen | Vert | Le tournoi de fin de saison entre les leaders des quatre maisons. Son vainqueur accède directement à la finale de la FGC. |
| Formes complexes | Dos de tortue | Vert | Le kame no kō : la forme laissée par la capture de deux pierres, un double ponnuki d'une grande solidité. |
| Formes complexes | Tête de cheval (uma no kao) | Vert | Un saut efficace vers le centre, la bonne forme que le proverbe oppose à la bouteille de saké. |
| Fuseki | Grande muraille | Vert | Une ouverture expérimentale qui bâtit un mur d'un bord à l'autre, contre toute stratégie classique. |
| Fuseki | Trou noir | Vert | Noir joue les quatre points 5-7 : une ouverture tournée tout entière vers le centre. |
| Joueurs | Hashimoto Utaro | Vert ✓ | Professionnel japonais, vainqueur du Honinbo et fondateur de la Kansai Ki-in. |
| Joueurs | Iwamoto Kaoru | Vert ✓ | Double Honinbo, qui consacra sa fortune à diffuser le go en Occident. |
| Joueurs | Sekiyama Riichi | Vert ✓ | Premier vainqueur du Honinbo en tournoi, en 1941, quand le titre cessa d'être héréditaire. |
| Joueurs | O Meien | Vert ✓ | Professionnel taïwanais de la Nihon Ki-in, double Honinbo, auteur de livres sur le fuseki. |
| Joueurs | Shibano Toramaru | Vert ✓ | Jeune professionnel japonais, devenu Meijin en 2019. |
| Joueurs | Takao Shinji | Vert ✓ | Professionnel japonais, Honinbo et Meijin. |
| Joueurs | Kobayashi Satoru | Vert ✓ | Professionnel japonais, vainqueur du Kisei. |
| Joueurs | Hane Naoki | Vert ✓ | Professionnel japonais, vainqueur du Kisei et du Honinbo. |
| Joueurs | Yamashita Keigo | Vert ✓ | Professionnel japonais, plusieurs fois Kisei. |
| Joueurs | Murakawa Daisuke | Vert ✓ | Professionnel japonais, vainqueur de titres majeurs. |
| Joueurs | Xie Yimin | Vert ✓ | Professionnelle taïwanaise de la Nihon Ki-in, longtemps reine des titres féminins japonais. |
| Joueurs | Nakamura Sumire | Vert ✓ | Plus jeune professionnelle de l'histoire du Japon, devenue pro à 10 ans, titrée chez les femmes. |
| Joueurs | Fujisawa Rina | Vert ✓ | Professionnelle japonaise titrée dans les tournois féminins, petite-fille de Fujisawa Shuko. |
| Joueurs | Ueno Asami | Vert ✓ | Professionnelle japonaise titrée dans les tournois féminins. |
| Joueurs | Kishimoto Saichiro | Vert ~ | Professionnel japonais de l'après-guerre. ⚠ À compléter. |
| Joueurs | Kitani Minoru | Vert ~ | Rival et ami de Go Seigen, avec qui il inventa le shinfuseki. Son école forma une génération de champions. |
| Joueurs | Kono Rin | Vert ~ | Professionnel japonais, vainqueur du Tengen. |
| Joueurs | Byun Sangil | Vert ~ | Professionnel coréen de l'élite mondiale. |
| Joueurs | Kyo Kagen | Vert ~ | Professionnel japonais d'origine taïwanaise, vainqueur du Gosei. |
| Joueurs | Seki Kotaro | Vert ~ | Jeune professionnel japonais, vainqueur du Tengen. |
| Joueurs | Ueno Risa | Vert ~ | Professionnelle japonaise, sœur cadette d'Ueno Asami. |
| Joueurs | Mukai Chiaki | Vert ~ | Professionnelle japonaise titrée dans les tournois féminins. |
| Joueurs | Kim Chaeyoung | Vert ~ | Professionnelle coréenne titrée dans les tournois féminins. |
| Joueurs | Kim Eunji | Vert ~ | Professionnelle coréenne, prodige des tournois féminins. |
| Joueurs | Cho Hyeyeon | Vert ~ | Professionnelle coréenne titrée dans les tournois féminins. |
| Joueurs | Oh Yujin | Vert ~ | Professionnelle coréenne titrée dans les tournois féminins. |
| Joueurs | Tu Xiaoyu | Vert ~ | Professionnelle chinoise. ⚠ À compléter. |
| Joueurs | Ryan Li | Vert ~ | Professionnel nord-américain de l'AGA. |
| Joueurs | Artem Kachanovskyi | Vert ~ | Joueur ukrainien, champion d'Europe. |
| Joueurs | Dang Yifei | Vert ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Alexander Qi | Vert ? | ⚠ À rédiger. |
| Joueurs | Cho Seokbin | Vert ? | ⚠ À rédiger. |
| Joueurs | Eunkyo Do | Vert ? | ⚠ À rédiger. |
| Joueurs | Fukuoka Kotaro | Vert ? | ⚠ À rédiger. |
| Joueurs | Hasegawa Akira | Vert ? | ⚠ À rédiger. |
| Joueurs | Hayashi Hakuei | Vert ? | ⚠ À rédiger. |
| Joueurs | Inoue Naoki | Vert ? | ⚠ À rédiger. |
| Joueurs | Ito Showa | Vert ? | ⚠ À rédiger. |
| Joueurs | Lai Junfu | Vert ? | ⚠ À rédiger. |
| Joueurs | Nyu Eiko | Vert ? | ⚠ À rédiger. |
| Joueurs | Wang Xinghao | Vert ? | Jeune professionnel chinois. ⚠ À compléter. |
| Joueurs | Wu Yiming | Vert ? | ⚠ À rédiger. |
| Joueurs | Xu Haohong | Vert ? | Professionnel chinois. ⚠ À compléter. |
| Tournois pro | Gosei | Vert | Titre japonais, « le sage du go ». |
| Tournois pro | Honinbo | Vert | Le plus ancien titre japonais, du nom de la grande maison de go de l'époque d'Edo. |
| Tournois pro | Judan | Vert | Titre japonais, « dix dan ». |
| Tournois pro | Kisei | Vert | Le titre japonais le mieux doté, « le saint du go ». |
| Tournois pro | Meijin | Vert | Titre japonais, héritier du rang suprême de l'époque d'Edo. |
| Tournois pro | Oza | Vert | Titre japonais, « le trône ». |
| Tournois pro | Tengen | Vert | Titre japonais, du nom du point central du goban. |
| Variantes | Sunjang | Vert | Le baduk traditionnel coréen, qui commence avec des pierres déjà posées sur le plateau. |
| Variantes | Torique | Vert | Le go sans bords : chaque côté du plateau se prolonge sur le côté opposé. |

#### 🔵 Bleu — 36 cartes

| Catégorie | Titre | Rareté | Description |
|-----------|-------|--------|-------------|
| Commu | Cassis le Poulpe Impérial | Bleu | Vainqueur de la première FGC, en 2018, alors jouée en une seule catégorie. |
| Commu | Deodred la Marmotte Impériale | Bleu | Vainqueur de la FGC 2019, catégorie libre. |
| Commu | SilverOreo la Salamandre Impériale | Bleu | Vainqueur de la FGC 2020, catégorie libre. |
| Commu | SilverOreo le Hérisson Impérial | Bleu | Vainqueur de la FGC 2021, catégorie libre. |
| Commu | R0n1n le Loup Impérial | Bleu | Vainqueur de la FGC 2022, catégorie libre. |
| Commu | Sun Tzu la Chauve-Souris Impériale | Bleu | Vainqueur de la FGC 2023, catégorie libre. |
| Commu | Tilwen le Papillon Impérial | Bleu | Vainqueur de la FGC 2024, catégorie libre. |
| Commu | Tilwen le Mammouth Impérial | Bleu | Vainqueur de la FGC 2025, catégorie libre. |
| Commu | Rikikilord l'Ours Impérial | Bleu | Vainqueur de la FGC 2026, catégorie libre. |
| Fuseki | Anar | Bleu | Le fuseki trollesque de la communauté : Tengen, un coup sur la colonne R, puis O11. T, R, O11 : TROLL. |
| Fuseki | Mirror go | Bleu | Blanc imite chaque coup de Noir en symétrie centrale, jusqu'à ce que Noir brise le miroir. |
| Joueurs | Kato Masao | Bleu ✓ | Professionnel japonais surnommé « le Tueur » pour son jeu d'attaque, dominateur du début des années 1980. |
| Joueurs | Rin Kaiho | Bleu ✓ | Professionnel taïwanais de la Nihon Ki-in, plusieurs fois Meijin et Honinbo. |
| Joueurs | Kobayashi Koichi | Bleu ✓ | Dominateur du go japonais à la fin des années 1980, plusieurs fois Kisei et Meijin. |
| Joueurs | Fujisawa Hideyuki | Bleu ✓ | Légende japonaise, six fois Kisei d'affilée, génie de l'ouverture. |
| Joueurs | Cho U | Bleu ✓ | Professionnel taïwanais de la Nihon Ki-in, dominateur des années 2000. |
| Joueurs | Iyama Yuta | Bleu ✓ | Seul joueur à avoir détenu les sept grands titres japonais à la fois, et par deux fois. |
| Joueurs | Ichiriki Ryo | Bleu ✓ | Professionnel japonais, vainqueur de l'Ing Cup 2023. |
| Joueurs | Takagawa Kaku | Bleu ✓ | Neuf fois Honinbo d'affilée, dans les années 1950. |
| Joueurs | Inoue Genan Inseki | Bleu ✓ | Chef de la maison Inoue à l'époque d'Edo, adversaire de Shusaku dans la partie du « coup aux oreilles rouges ». |
| Joueurs | Fan Hui | Bleu ✓ | Trois fois champion d'Europe, premier professionnel battu par AlphaGo, en 2015. |
| Joueurs | Yang Dingxin | Bleu ✓ | Professionnel chinois, vainqueur de la LG Cup. |
| Joueurs | Shin Minjun | Bleu ✓ | Professionnel coréen, vainqueur de la LG Cup 2021 face à Ke Jie. |
| Joueurs | Kim Jiseok | Bleu ✓ | Professionnel coréen, vainqueur de la Samsung Cup 2014. |
| Joueurs | Mi Yuting | Bleu ✓ | Professionnel chinois, vainqueur de la Mlily Cup 2013. |
| Joueurs | Choi Cheolhan | Bleu ✓ | Professionnel coréen, vainqueur de l'Ing Cup 2009. |
| Joueurs | Otake Hideo | Bleu ~ | Professionnel japonais, maître de la belle forme, plusieurs fois Meijin. |
| Joueurs | Yoda Norimoto | Bleu ~ | Professionnel japonais, plusieurs fois Meijin. |
| Joueurs | Yasui Chitetsu | Bleu ~ | Maître de la maison Yasui à l'époque d'Edo. ⚠ À compléter. |
| Joueurs | Ilya Shikshin | Bleu ~ | Joueur russe, plusieurs fois champion d'Europe, professionnel européen. |
| Joueurs | Ding Hao | Bleu ~ | Jeune professionnel chinois, champion du monde. |
| Joueurs | Yu Zhiying | Bleu ~ | Professionnelle chinoise, titrée dans les tournois féminins mondiaux. |
| Joueurs | Fan Tingyu | Bleu ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Gu Zihao | Bleu ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Jiang Weijie | Bleu ? | Professionnel chinois. ⚠ À compléter. |
| Joueurs | Xie Ke | Bleu ? | Professionnel chinois. ⚠ À compléter. |

#### 🟣 Violet — 19 cartes

| Catégorie | Titre | Rareté | Description |
|-----------|-------|--------|-------------|
| Commu | Examen Hunter | Violet | Ancien événement de la communauté, qui décernait chaque mois un titre de Hunter aux joueurs ayant le plus joué. |
| Commu | Mont Tengen | Violet | Le sommet où se dressait le Temple des 361 Voies, là où le ciel touche la terre. |
| Commu | Temple des 361 Voies | Violet | Le temple des Synalithes, qui jouaient pour comprendre et non pour vaincre, avant la disparition de Gold. |
| Commu | Tour céleste | Violet | Ancien événement de la communauté : une tour dont les joueurs gravissaient les étages partie après partie. |
| Formes complexes | B2 bomber | Violet | La surconcentration faite forme : beaucoup de pierres, presque aucune efficacité. |
| Joueurs | Honinbo Shusai | Violet ✓ | Dernier Honinbo héréditaire, qui céda le titre pour en faire un tournoi. Sa partie d'adieu inspira *Le Maître ou le tournoi de go* de Kawabata. |
| Joueurs | Gu Li | Violet ✓ | Professionnel chinois, plusieurs fois champion du monde, grand rival de Lee Sedol. |
| Joueurs | Park Junghwan | Violet ✓ | Professionnel coréen, plusieurs fois champion du monde. |
| Joueurs | Rui Naiwei | Violet ✓ | Première femme à remporter un grand titre open, le Guksu coréen, et reine des tournois féminins mondiaux. |
| Joueurs | Ma Xiaochun | Violet ✓ | Professionnel chinois, champion du monde dans les années 1990. |
| Joueurs | Chang Hao | Violet ✓ | Professionnel chinois, plusieurs fois champion du monde dans les années 2000. |
| Joueurs | Park Younghun | Violet ✓ | Professionnel coréen, double vainqueur de la Fujitsu Cup. |
| Joueurs | Honinbo Shuwa | Violet ~ | Chef de la maison Honinbo au XIXᵉ siècle, dont Shusaku fut l'héritier désigné. |
| Joueurs | Sakata Eio | Violet ~ | Dominateur du go japonais des années 1960, surnommé « le Rasoir ». |
| Joueurs | Cho Chikun | Violet ~ | Le joueur le plus titré de l'histoire du go japonais. |
| Joueurs | Choi Jeong | Violet ~ | Professionnelle coréenne, reine des tournois féminins mondiaux, première femme en finale de la Samsung Cup. |
| Joueurs | Takemiya Masaki | Violet ~ | Professionnel japonais, père du « go cosmique » tourné vers le centre, vainqueur de la Fujitsu Cup. |
| Joueurs | Kang Dongyun | Violet ~ | Professionnel coréen, champion du monde. |
| Joueurs | Nie Weiping | Violet ~ | Héros chinois des Super Go sino-japonais des années 1980. |

#### 🟡 Gold — 11 cartes

| Catégorie | Titre | Rareté | Description |
|-----------|-------|--------|-------------|
| Commu | Gold l'Incréé | Gold | L'entité divine qui façonna le premier plateau et posa la première pierre. Sa disparition brisa l'unité des Quatre. |
| Commu | HisokaH l'Ermite | Gold | Le maître de la communauté, son professeur et son créateur. |
| Commu | HisokaH le Lutin | Gold | Le maître de la communauté, sous son titre parodique. |
| Joueurs | Cho Hunhyun | Gold ✓ | Premier vainqueur de l'Ing Cup, en 1989, dominateur du go coréen pendant des décennies. |
| Joueurs | Go Seigen | Gold ✓ | Tenu pour le plus grand joueur du XXᵉ siècle, invaincu dans ses jubango, co-inventeur du shinfuseki. |
| Joueurs | Honinbo Dosaku | Gold ✓ | Le « saint du go » du XVIIᵉ siècle, qui posa les bases de la théorie moderne. |
| Joueurs | Lee Changho | Gold ✓ | « Le Bouddha de pierre », dominateur mondial des années 1990, élève de Cho Hunhyun. |
| Joueurs | Lee Sedol | Gold ✓ | Dominateur des années 2000, seul humain à avoir battu AlphaGo en match officiel. |
| Joueurs | Honinbo Shusaku | Gold ~ | Invaincu en dix-neuf parties du Château, auteur du « coup aux oreilles rouges ». |
| Joueurs | Ke Jie | Gold ~ | Professionnel chinois, plusieurs fois champion du monde, adversaire d'AlphaGo en 2017. |
| Joueurs | Shin Jinseo | Gold ~ | Numéro un mondial des années 2020, plusieurs fois champion du monde. |

### 7.3 Notes de contenu

- Les cartes Commu Vert et Bleu sont les vainqueurs de la FulguroGo Cup. Il y a un animal par saison : l'adulte (Bleu)
  est le vainqueur de la catégorie libre, le petit (Vert) celui de la catégorie Novice-Elite. La saison 2018, jouée en
  une seule catégorie, n'a que son adulte, le Poulpe.
- Dai Junfu et Lai Junfu sont deux joueurs distincts.
- FOX et IGS sont des cartes, bien que le serveur ne suive plus ces plateformes : une carte n'est pas une intégration.
- Le consentement des membres représentés sur les cartes Commu est acquis.

## 8. Économie

### 8.1 Gagner des points

Une seule source : les **parties gold**, définies comme pour la validité FGC. Une partie finie sur KGS ou OGS, en
**19×19**, **sans handicap**, avec un **komi strictement compris entre 6 et 9**, dont **les deux joueurs** sont des
membres ayant lié leur compte. Qu'elle soit classée ou non ne compte pas.

| Règle | Valeur |
|-------|--------|
| Gain par partie gold | **1 000 points** à chacun des deux joueurs |
| Plafond | **5 parties créditées par jour et par joueur** (jour calendaire, heure de Paris) ; au-delà, rien |
| Partie annulée après coup par la plateforme | les points déjà crédités restent acquis |

Il n'y a ni défis, ni événements, ni autre source. À ce tarif, une partie gold paie 2 packs, et le plafond borne un
joueur à 10 packs par jour.

### 8.2 Dépenser

- **Un pack coûte 500 points.** Le joueur l'ouvre depuis le site, quand il veut.
- Les doubles sont proposés au recyclage :

| Rareté | Points de recyclage |
|--------|--------------------:|
| Gris   | 10                  |
| Vert   | 25                  |
| Bleu   | 50                  |
| Violet | 150                 |
| Gold   | 500                 |

Une Gold recyclée paie un pack. En fin de collection, quand presque tout est double, un pack recyclé en entier rapporte
~143 points, soit 29 % de son prix : le recyclage allonge le budget d'un peu plus d'un quart.

## 9. Équilibre

### 9.1 Activité de référence

Mesure sur la base de dev, du 26/08/2026 au 24/09/2026 : 149 parties gold jouées en 30 jours, 72 joueurs actifs (au
moins une partie gold) sur 304 membres liés.

| Parties gold par joueur actif sur 30 jours | Valeur |
|--------------------------------------------|-------:|
| Médiane                                    | 3      |
| Moyenne                                    | 4,1    |
| 75ᵉ centile                                | 5      |
| Maximum                                    | 28     |

Par jour et par joueur, 98 % des journées comptent une ou deux parties. Le seul cas au-delà de 3 est une série de
13 parties entre deux joueurs le même jour, que le plafond de 5 coupe.

⚠ Un mois de début de saison ne dit rien de l'été ni du creux de l'hiver. La mesure est à refaire sur une saison
complète, et le gain par partie à recaler si la médiane bouge.

### 9.2 Durée de complétion

Avec les poids du §3, l'anti-doublon, le pity et le recyclage réinvesti en packs :

| | 10ᵉ centile | Médiane | 90ᵉ centile |
|---|---:|---:|---:|
| Packs ouverts | 209 | 285 | 388 |
| Points à gagner en parties | 84 600 | 113 900 | 152 000 |

Soit, à 1 000 points par partie :

| Parties gold par mois | Mois pour finir (chanceux / médian / malchanceux) |
|----------------------:|--------------------------------------------------:|
| 1                     | 85 / 114 / 152                                    |
| **3 (joueur médian)** | **28 / 38 / 51**                                  |
| 5                     | 17 / 23 / 30                                      |
| 10                    | 8 / 11 / 15                                       |
| 20                    | 4 / 6 / 8                                         |

Le joueur actif médian finit en un peu plus de trois ans (38 mois), ce qui tient l'objectif de plus de deux ans. À 5
parties par mois, il faut deux ans ; à 10, un an. Même un joueur qui atteint le plafond chaque jour a besoin d'environ
115 parties, soit plus de trois semaines à 5 parties par jour.

### 9.3 Poids des mécanismes

| Scénario (packs ouverts) | Médiane | 90ᵉ centile |
|--------------------------|--------:|------------:|
| Tirage pondéré seul | 617 | 1 025 |
| + anti-doublon sur le slot garanti | 296 | 427 |
| + pity sur carte manquante | 285 | 388 |

L'anti-doublon fait l'essentiel : il divise par deux le nombre de packs nécessaires. Le pity déplace peu la médiane,
mais il coupe la queue de distribution (90ᵉ centile : 427 → 388), c'est-à-dire les joueurs malchanceux. C'est
exactement son rôle.

### 9.4 Extensions

À chaque extension, les poids restent les mêmes, mais les parts par couleur, le temps de complétion et le gain par
partie sont à recalculer (§3.1). Le taux de complétion affiché de chaque joueur baisse le jour de la sortie : c'est
voulu, et c'est la seule conséquence, puisqu'il n'y a pas de badges.

## 10. Membres

- **Membre banni** : ses cartes Commu restent des cartes comme les autres, dans le set et dans les collections.
- **Membre purgé** (parti du Discord, supprimé par `CleanService` après un jour de grâce) : sa collection, son solde et
  ses registres de points sont supprimés avec le reste de ses données.

## 11. Discord

Deux annonces, sur le canal de notification :

- une **Gold obtenue**, quand elle est nouvelle pour le joueur (un double n'est pas annoncé) ;
- un **album complet**, quand un joueur atteint 100 % du set courant.

## 12. Mise en place sur fulguro-server

Le système entre dans l'architecture existante sans rien de nouveau : un module `cards` sur le modèle des autres
(`CardsModule`, `CardsDatabaseAccessor`, modèles sql2o, routes sur `Api`).

### 12.1 Données

| Table | Contenu |
|-------|---------|
| `cards` | Le catalogue (§5). Corriger une carte ou ajouter une extension, c'est un script SQL appliqué au déploiement. |
| `card_collection` | `(discord_id, slug)` → nombre d'exemplaires. |
| `card_ledger` | Un mouvement de points par ligne : gain de partie, achat de pack, recyclage. |
| `card_wallet` | Le solde matérialisé et les deux compteurs de pity, par joueur. |
| `card_openings` | Le journal des ouvertures : joueur, date, cartes, compteurs de pity avant et après. |

Le solde est **stocké et vérifiable** : il doit toujours égaler la somme du registre. Le stockage permet le débit
atomique, le registre permet de répondre à une contestation avec des faits.

### 12.2 Crédit des points

Un service périodique parcourt les parties gold qui n'ont pas encore été créditées, et écrit un gain par (partie, joueur)
dans `card_ledger`. La clé `(gold_id, discord_id)` rend l'écriture idempotente sans curseur, sur le modèle de
`house_points`.

Trois contraintes :

- **Le plafond de 5 par jour** se compte sur la date de la partie en heure de Paris (`DATE_ZONE`). Une partie
  au-delà du plafond est enregistrée à 0 point plutôt qu'ignorée, sinon elle revient à chaque tick.
- **Le registre ne dépend pas de la survie de la partie.** `CleanService` supprime les parties de plus de 32 jours et
  `OgsService` celles qu'OGS annule. Aucune de ces suppressions ne doit toucher `card_ledger`, puisque les points
  d'une partie annulée restent acquis.
- **La sélection ne peut pas réutiliser la vue `fgc_validity_games` telle quelle**, qui est bornée à 30 jours et ne
  sert qu'à compter. Elle en reprend les critères. Le service doit passer au moins une fois dans les 32 jours de
  rétention des parties, ce que n'importe quel intervalle de l'ordre de la minute garantit.

### 12.3 Ouverture d'un pack et recyclage

- **Routes authentifiées.** Sans authentification, n'importe qui pourrait dépenser les points d'un autre et recycler
  ses cartes. `api/auth/SessionResolver.kt` résout déjà la session Discord du navigateur pour les routes admin, et les
  routes cartes passent par lui.
- **Une seule transaction** par ouverture : débit conditionnel (`UPDATE card_wallet SET points = points - 500 WHERE
  discord_id = :id AND points >= 500`, puis contrôle du nombre de lignes touchées), écriture des cartes, des compteurs
  de pity, du registre et du journal. Sinon un double clic ouvre deux packs pour un seul débit. Le rate limit de l'API
  ne protège pas de ça.
- Le recyclage suit le même schéma : décrément de l'exemplaire, crédit du registre et du solde dans la même
  transaction.

### 12.4 Purge et annonces

- `CleanService` ajoute les tables `card_collection`, `card_ledger`, `card_wallet` et `card_openings` à ce qu'il
  supprime pour un membre purgé.
- Les annonces du §11 passent par `DiscordBot.sendMessageEmbeds`, sur le modèle de `HouseNotifier`, en best-effort :
  une annonce ratée coûte une ligne de log, jamais une ouverture.
- dev et prod sont isolés : un tirage en local écrit dans `fg_dev` et passe par le bot de test. Aucune ressource
  externe n'est partagée.

### 12.5 Avant publication

- Vérifier les joueurs marqués ~ (34) et ? (41) au §7, et ajuster leur rareté selon le barème du §6. Chaque
  déplacement change les effectifs, mais pas les poids : il suffit de refaire les tableaux des §3.3 et §9.2.
- Compléter les descriptions marquées ⚠ au §7, qui ne concernent plus que des joueurs dont le palmarès reste à
  vérifier.
- Refaire la mesure d'activité du §9.1 sur une saison complète.
- Tests : simuler des ouvertures en masse pour vérifier les parts réelles, que le slot garanti est toujours Vert ou
  mieux, que l'anti-doublon et le pity rendent bien une carte manquante, et que deux ouvertures concurrentes ne
  débitent jamais sous zéro.
