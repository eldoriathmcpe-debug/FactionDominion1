FACTION DOMINION 1.0.0 — BASE DE DÉVELOPPEMENT
Serveur cible : Paper 1.20.1 / Java 17

CONTENU INCLUS
- Système Faction intégré : create, invite, join, leave, disband, info, claim, unclaim, who.
- Protection des chunks claimés et anti-dégâts entre membres d'une même faction.
- Trois minerais générés dans les nouveaux chunks : Aetherium, Pyrium, Sylvarium.
- Lingots, sets d'armures et outils personnalisés pour les trois minerais.
- Effets de set : Aetherium = Vitesse II ; Pyrium = Force ; Sylvarium = Régénération.
- Armure de farm avec Hâte et Vitesse.
- Hammer 3x3, EndGlider, Cave Block, Unclaim Finder I/II/III.
- Graine de Vitalis, pomme de soin, orbes de vitesse et de puissance.
- Sauvegarde des factions, claims et emplacements des minerais dans plugins/FactionDominion/data.yml.

COMPILATION
1. Installer Java 17 et Maven.
2. Ouvrir ce dossier dans IntelliJ IDEA comme projet Maven.
3. Lancer : mvn clean package
4. Copier target/faction-dominion-1.0.0.jar dans le dossier plugins du serveur Paper 1.20.1.
5. Redémarrer le serveur.

COMMANDES
/f create <nom>
/f invite <joueur>
/f join <nom>
/f leave
/f disband
/f info
/f claim
/f unclaim
/f who <joueur>
/dominion list
/dominion give <joueur> <objet> [quantité]
/dominion reload

SETS À DONNER : aetherium_set, pyrium_set, sylvarium_set, farm_set.
OUTILS : aetherium_sword/pickaxe/axe/shovel/hoe (idem pyrium et sylvarium).

IMPORTANT
Cette livraison est le code source initial complet du socle, pas un JAR compilé/testé. L'environnement de création actuel n'a pas accès au dépôt Maven Paper, donc la compilation ne peut pas être exécutée ici. Il faut compiler puis tester sur une copie de ton serveur avant le serveur public. Le système Faction intégré est volontairement autonome et n'est pas compatible avec les commandes/API d'un plugin tiers sans adaptation.

SUR WINDOWS
Tu peux aussi double-cliquer sur build.bat après avoir installé Maven et Java.
