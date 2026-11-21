# Nexo — Frontend

Daily personalised content platform built in **Angular 22**. It consumes the Nexo APIs:
`authentication` (:8090), `notification` (:8091), `topic` (:8092), `subscription` (:8093),
`content` (:8094), `delivery` (:8095) and `collector` (:8097). Runs on http://localhost:4300.

## Stack

- Angular 22 · standalone components · **Signals** · zoneless change detection
- Angular Router with lazy-loaded features + `PreloadAllModules`
- Angular **CDK** (overlay, dialog, menu) — behaviour without visual opinion
- Custom design system tokenised from `DESIGN.md` (Apple-flavoured: single blue
  accent, SF Pro / Inter, pill CTAs, one system shadow)
- SCSS · light/dark/system theming via CSS custom properties
- Runtime i18n (ES/EN) with live locale switching
- PWA (service worker + manifest)

## Architecture

```
src/app/
├── core/            singletons: models, one API client per backend service
│                    (all extend BaseApiService) + 4 interceptors, auth
│                    (AuthenticationService w/ single-flight refresh, guards),
│                    theme, i18n, toast
├── shared/          reusable UI library (button, card, dialog, form fields,
│                    icon set, toast, pagination, filters…), pipes, utils
├── layout/          app shell (header + collapsible sidebar) and auth layout
└── features/        lazily-loaded verticals, each with its own data/ services:
    authentication · feed (my day, explore, my topics, favorites, history) ·
    notifications · settings (incl. daily delivery) · admin (overview, catalog,
    sources, content review, users, roles, audit, service clients) · help · errors
```

Path aliases: `@core/*`, `@shared/*`, `@layout/*`, `@features/*`, `@env/*`.

## Development

```bash
npm install
npm start          # ng serve → http://localhost:4300
```

The dev environment points at the APIs on `http://localhost:8090`–`8097`
(see `api/docker`). Adjust `src/environments/environment.ts`.

## Build

```bash
npm run build      # production build → dist/app  (~87 kB initial transfer)
```

Production uses `environment.prod.ts` (relative `/auth` + `/api` proxy paths)
and enables the service worker.
