# Feuilles de style et scripts

`emp/tokens.css` et `emp/components/bundle.css` sont copiés tels quels du design system EMP : ne pas les modifier ici, les remplacer par une nouvelle copie.

- Source : https://claude.ai/artifact/XDatPoKAZScoY8cJwfervp (`project/tokens.css`, `project/components/bundle.css`)
- Version copiée : 1793221483-453f, du 28/10/2026

`htmx.js` est [htmx](https://htmx.org) copié tel quel : ne pas le modifier ici, le remplacer par une nouvelle copie.

- Source : https://unpkg.com/htmx.org@2.0.11/dist/htmx.min.js (code lisible : `dist/htmx.js`, même version)
- Version copiée : 2.0.11, SHA-256 `d6fdc75f204e6bdefa99b69bf1e6d4ac69b8a364f77929f45c13476b4000f717`

`base.css` porte les styles de base propres au site, `emp.js` le script commun. Ces fichiers sont intégrés au binaire par la tâche Gradle `generateAssets`, sous un nom qui change avec leur contenu.
