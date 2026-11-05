# Nuvio Plus et Nuvio Pro

Le même code donne deux applis :

| Version | Nom | Licence | Paquet Android (debug) |
|---------|-----|---------|------------------------|
| Gratuite | Nuvio Plus | Aucune | `com.nuviodebug.com` |
| Payante | Nuvio Pro | Clé obligatoire | `com.nuviodebug.com.pro` |

Les deux s'installent côte à côte sur le même téléphone.

Au lancement, Nuvio Pro demande une clé d'accès : un champ, un bouton Activer, et en petit le code du téléphone. Une fois la clé acceptée, il marche exactement comme Nuvio Plus. Une clé marche sur un seul téléphone et s'arrête à la date prévue. Pas de serveur, rien à payer.

## Fichiers

| Fichier | Rôle |
|---------|------|
| `licence/generateur.html` | Page pour créer les clés |
| `androidApp/src/pro/res/values/licence.xml` | Réglages de Nuvio Pro : clé publique, liste de blocage |
| `androidApp/src/pro/res/values*/strings.xml` | Nom « Nuvio Pro » |
| `composeApp/src/androidMain/kotlin/com/nuvio/app/core/licence/` | Vérification des clés et écran d'abonnement |
| `licence/test/` | Tests du format des clés |

## 1. Créer ta clé secrète

1. Ouvre le générateur et appuie sur **Créer ma clé secrète**.
2. Copie la **clé secrète** dans un endroit privé. Si tu la perds, tu ne pourras plus créer de clés pour les Nuvio Pro déjà installés.
3. Copie la **clé publique**.

La clé secrète reste dans ton navigateur. Ne la mets jamais sur GitHub.

## 2. Configurer Nuvio Pro

Dans `androidApp/src/pro/res/values/licence.xml` (modifiable depuis le site GitHub, bouton crayon) :

- `licence_public_key` : colle la clé publique à la place de `COLLE_ICI_TA_CLE_PUBLIQUE`.
- `licence_blocklist_url` : optionnel, voir plus bas.

Tant que la clé publique n'est pas collée, GitHub construit seulement Nuvio Plus et affiche un avertissement pour Nuvio Pro.

## 3. Récupérer les APK

À chaque push sur `main`, GitHub Actions construit les deux APK et publie une release numérotée (par exemple `v0.6.6`) qui contient `NuvioPlus.apk` et `NuvioPro.apk`. Les 5 dernières releases sont gardées.

Liens qui pointent toujours vers la dernière version :

- https://github.com/amarbcrb/Nuvio-enhanced-/releases/latest/download/NuvioPlus.apk
- https://github.com/amarbcrb/Nuvio-enhanced-/releases/latest/download/NuvioPro.apk

Dans l'appli, la mise à jour propose la dernière release : Nuvio Plus télécharge `NuvioPlus.apk`, Nuvio Pro télécharge `NuvioPro.apk`.

Sur ton PC : `./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore` donne Nuvio Plus. Ajoute `-Pnuvio.edition=pro` pour Nuvio Pro.

## 4. Vendre un mois

1. Le client installe Nuvio Pro. En bas de l'écran, il voit le **code du téléphone** (par exemple `K7F3-92QX`). Un appui dessus le copie.
2. Il te l'envoie avec son paiement.
3. Dans le générateur : tape le code et son nom, choisis la **date de début** et la **date de fin** (ou une durée, qui calcule la fin), puis **Créer la licence**. La clé marche du jour de début au jour de fin inclus.
4. Appuie sur **Envoyer par WhatsApp** ou **Copier la clé**, et envoie-la-lui.
5. Il colle la clé dans le champ **Clé d'accès** et appuie sur **Activer**.

Pour renouveler, appuie sur **Renouveler** dans la liste des clients. Le début est mis au lendemain de la fin actuelle (ou à aujourd'hui si elle est passée), et tu peux changer les dates avant de créer la clé. Le client colle la nouvelle clé à la place de l'ancienne.

## Ce que voit le client

- **Jours restants** : en haut des réglages de Nuvio Pro, la section **Abonnement** affiche « 12 jours restants » et la date de fin.
- **Rappel** : quand il reste 3 jours ou moins, un message s'affiche à l'ouverture de l'appli.
- **Renouvellement en avance** : le client appuie sur la ligne de la section **Abonnement** dans les réglages et colle la nouvelle clé. Si elle commence plus tard (le lendemain de la fin actuelle), l'appli la garde et passe dessus toute seule le jour venu.
- **Après la date de fin** : l'appli revient sur l'écran de clé.

## Bloquer quelqu'un (remboursement, abus)

La liste de blocage est `licence/bloques.txt`. Nuvio Pro la relit à l'ouverture et toutes les 30 minutes quand il a internet. Un téléphone bloqué affiche « Accès suspendu », même s'il lui reste des jours, et le reste même hors ligne.

### En un appui (recommandé)

Une seule fois, dans le générateur sur ton site (pas la page Claude, qui ne peut pas joindre GitHub) :

1. **Réglages > Bloquer en un appui** : suis les 4 étapes pour créer un jeton GitHub (Only select repositories > Nuvio-enhanced-, Contents : Read and write).
2. Colle le jeton, puis **Enregistrer et tester**.

Ensuite, dans **Clients** : **Bloquer**, puis **Confirmer le blocage**. Le client passe en « Bloqué ». Pour lui rendre l'accès : **Débloquer**, puis **Confirmer le déblocage**.

Le jeton reste dans ce navigateur. Il ne peut modifier que ce dépôt. Pour le retirer : **Réglages > Retirer le jeton**, ou supprime-le sur GitHub (Settings > Developer settings > Personal access tokens).

### Sans jeton

**Bloquer** copie le code et ouvre `bloques.txt` sur GitHub : colle le code sur une nouvelle ligne, puis **Commit changes**. Pour débloquer, supprime la ligne.

Modifier ce fichier ne relance pas la construction de l'appli.

## Ce que fait Nuvio Pro

- Vérifie la signature de la clé : personne ne peut fabriquer de clé sans ta clé secrète.
- Vérifie que la clé correspond au code de ce téléphone.
- Bloque si la date du téléphone a été reculée, et utilise l'heure d'internet quand elle est disponible.
- Revérifie toutes les 30 minutes : l'appli se bloque d'elle-même à l'expiration, même si elle est ouverte.

## Limites

- Le code appareil vient de l'identifiant Android de l'appli. Il ne change pas si le client réinstalle l'appli, mais il change après une réinitialisation d'usine, ou si l'APK est signé avec une autre clé. Les APK de GitHub Actions sont toujours signés avec `androidApp/debug.keystore`, donc les codes restent les mêmes d'une mise à jour à l'autre.
- Le code source est public (licence GPL-3.0 de Nuvio). Un développeur pourrait retirer la vérification. Pour la plupart des clients, ça n'arrivera pas.

## Tests

```bash
node licence/test/core.test.js vecteurs.json
java licence/test/InteropTest.java vecteurs.json
```

Le premier test vérifie le format des clés du générateur, avec le même WebCrypto que les navigateurs. Le second vérifie ces clés avec les mêmes appels Java que l'appli (`KeyFactory "EC"`, `SHA256withECDSA`).
