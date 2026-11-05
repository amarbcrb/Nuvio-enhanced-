# Nuvio Plus et Nuvio Pro : règles pour Claude

Ce dépôt produit **deux applis Android à partir du même code**. Lis ce fichier avant toute modification.

## Les deux éditions

| | Nuvio Plus | Nuvio Pro |
|---|---|---|
| Rôle | Version gratuite | Version payante |
| Au lancement | S'ouvre directement | Demande une clé d'accès, puis s'ouvre comme Nuvio Plus |
| Option Gradle | aucune (`nuvio.edition=free` par défaut) | `-Pnuvio.edition=pro` |
| Paquet (debug, celui publié) | `com.nuviodebug.com` | `com.nuviodebug.com.pro` |
| Nom affiché | Nuvio Plus | Nuvio Pro |
| APK dans les releases | `NuvioPlus.apk` | `NuvioPro.apk` |
| `AppVersionConfig.EDITION` | `"free"` | `"pro"` |
| Ressource `licence_required` | `false` | `true` |

En dehors de l'écran de clé, **Nuvio Pro est identique à Nuvio Plus**. Tout le reste du code est partagé.

## Où se trouve la différence

Seuls ces fichiers distinguent les deux éditions :

- `androidApp/build.gradle.kts` : lit `nuvio.edition`, ajoute `.pro` au paquet et le dossier `src/pro/res`, et refuse de construire Nuvio Pro sans clé publique.
- `androidApp/src/pro/res/` : nom « Nuvio Pro » et réglages de licence (`values/licence.xml`).
- `composeApp/src/androidMain/res/values/licence.xml` : valeurs de Nuvio Plus (pas de licence).
- `composeApp/src/androidMain/kotlin/com/nuvio/app/core/licence/` : vérification des clés et écran de clé. Ne fait rien dans Nuvio Plus.
- `composeApp/src/androidMain/kotlin/com/nuvio/app/MainActivity.kt` : l'appli est entourée de `LicenceGate { ... }`.
- `composeApp/build.gradle.kts` : écrit `AppVersionConfig.EDITION`.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/features/updater/AppUpdaterRepository.kt` : chaque édition ne télécharge que son APK.
- `composeApp/src/commonMain/kotlin/com/nuvio/app/core/licence/LicenceInfo.kt` et `features/settings/SettingsRootPage.kt` : dans Nuvio Pro seulement, une ligne « Nuvio Pro » en haut des réglages affiche les jours restants et ouvre la fenêtre de nouvelle clé.
- `licence/` : générateur de clés (`generateur.html`), tests, guide (`LISEZMOI.md`).

## Quelle édition modifier

1. **Par défaut, une demande porte sur les deux éditions.** Modifie le code partagé (`composeApp/src/commonMain`, `androidMain`, etc.). Une nouvelle fonction, une correction d'interface ou de lecteur va dans les deux.
2. **« Seulement Nuvio Pro »** : mets le changement derrière `AppVersionConfig.EDITION == "pro"` (code commun) ou `R.bool.licence_required` (code Android), ou dans `androidApp/src/pro/res/` pour une ressource.
3. **« Seulement Nuvio Plus »** : même chose avec `AppVersionConfig.EDITION == "free"`.
4. **Si la demande ne dit pas laquelle et qu'elle ne peut pas logiquement s'appliquer aux deux**, demande avant de coder.
5. Ne change jamais le comportement de Nuvio Plus en travaillant sur la licence de Nuvio Pro.
6. N'ajoute aucune source ni aucun provider dans l'appli : les utilisateurs ajoutent les leurs.

## Licence de Nuvio Pro

- Clé = 10 octets (version 2, code du téléphone sur 5 octets, jour de début, jour de fin) + signature ECDSA P-256, en base32 Crockford (119 caractères). Valable du jour de début au jour de fin inclus.
- Le format est défini deux fois et doit rester identique : bloc `licence-core` de `licence/generateur.html` et `LicenceFormat` dans `LicenceManager.kt`.
- Après une modification du format : `node licence/test/core.test.js vecteurs.json && java licence/test/InteropTest.java vecteurs.json`.
- La clé secrète n'est jamais dans le dépôt. Seule la clé publique va dans `androidApp/src/pro/res/values/licence.xml`.

## Construire et publier

- Une seule branche : `main`. Chaque push sur `main` lance `.github/workflows/build.yml`.
- Le workflow construit Nuvio Plus, puis Nuvio Pro si la clé publique est collée, et publie une release `v<majeur>.<mineur>.<patch + numéro du build>` avec les deux APK. Il garde les 5 dernières releases.
- La version vient de `iosApp/Configuration/Version.xcconfig` (`MARKETING_VERSION` + numéro du build sur le dernier chiffre, `CURRENT_PROJECT_VERSION` + numéro du build pour le version code).
- Le bouton « Mise à jour » de l'appli lit les releases de `amarbcrb/Nuvio-enhanced-`.
- En local : `./gradlew :androidApp:assembleFullDebug -Pnuvio.android.distribution=full -Pnuvio.ios.distribution=appstore`, plus `-Pnuvio.edition=pro` pour Nuvio Pro.
- Dans un script Gradle (`.kts`), importe les classes Java (`import java.util.Base64`) : `java.util.X` écrit en entier ne compile pas, car `java` y désigne une extension Gradle.

## Origine

Fork non officiel de Nuvio (GPL-3.0). Le code source doit rester public et la licence `LICENSE` doit être conservée.
