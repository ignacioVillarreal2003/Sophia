# Sophia — Frontend

Colaborative document workspace (Notion/Confluence-class) built in **Angular 22**.
Consumes the `authentication` (:8080), `document-workspace` (:8081) and `notification` (:8082) microservices.

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
├── core/            singletons: models, http (ApiService + 4 interceptors),
│                    auth (AuthService w/ single-flight refresh, guards),
│                    theme, i18n, toast
├── shared/          reusable UI library (button, card, dialog, form fields,
│                    icon set, toast, tooltip, breadcrumbs…), pipes, directives
├── layout/          app shell (header + collapsible sidebar) and auth layout
└── features/        lazily-loaded verticals, each with its own data/ services:
    auth · dashboard · workspaces · documents (explorer + block editor +
    comments + revisions) · search · templates · trash · notifications ·
    settings · admin · help · errors
```

Path aliases: `@core/*`, `@shared/*`, `@layout/*`, `@features/*`, `@env/*`.

## Development

```bash
npm install
npm start          # ng serve → http://localhost:4200
```

The dev environment points at `http://localhost:8080` (auth) and
`http://localhost:8082` (workspace). Adjust `src/environments/environment.ts`.

## Build

```bash
npm run build      # production build → dist/app  (~87 kB initial transfer)
```

Production uses `environment.prod.ts` (relative `/auth` + `/api` proxy paths)
and enables the service worker.
