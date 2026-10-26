# Nuvio Enhanced

Providers pour l'app [Nuvio](https://github.com/tapframe/NuvioStreaming).

## Providers

| Provider | Types | Notes |
|----------|-------|-------|
| MovieBox | film, série | API web H5 de MovieBox. Toutes les langues audio disponibles (une source par doublage), MP4/HLS/DASH, sous-titres. |

## Installation dans Nuvio

Settings > Plugins > ajouter l'URL du manifest :

```
https://raw.githubusercontent.com/amarbcrb/nuvio-enhanced-/<branche>/manifest.json
```

## Développement

```bash
npm install
npm run build   # src/<provider>/ -> providers/<provider>.js
npm test        # tests hors ligne (fetch simulé)
```

Le code source est dans `src/`. Ne pas éditer `providers/` à la main : c'est la sortie du build.

### Fonctionnement de MovieBox

1. TMDB : titre anglais, titre original et année à partir de l'ID TMDB.
2. Jeton invité MovieBox (en-tête `x-user` de `get-latest-app-pkgs`).
3. Recherche par titre, puis on garde les résultats au titre exact (et à l'année proche). Chaque doublage (`Titre [Hindi]`, etc.) est une entrée séparée.
4. Pour chaque entrée : `subject/download` (MP4 + sous-titres) et `subject/play` (HLS/DASH). Les sources VIP sont ignorées.

Les flux exigent `Referer`/`Origin` = `https://fmoviesunblocked.net`, déjà inclus dans `headers` de chaque stream.
