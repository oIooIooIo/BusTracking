# Admin Web

React, TypeScript, Vite, Ant Design, and Leaflet administration UI.

Implemented Demo functions:

- Admin login
- Bus, employee, and device management
- Device assignment history
- Route and shared-stop management
- Employee-to-route permission grant and revoke
- Bus route query and map display
- Boarding-event display

## Run

Start the backend first, then run from the repository root:

```bash
./scripts/environment/start-local.sh frontend
```

To use an approved untracked runtime file, run
`./scripts/environment/start-local.sh frontend .env.local`.

Open `http://localhost:5173` and use `admin` / `admin123`.

The Vite development server calls the backend at `http://localhost:8080`.

## Verify

Before execution, Codex must identify the LOCAL environment and exact commands
and receive the owner's confirmation. Run from the repository root:

```bash
(cd apps/admin-web && npm run lint)
./scripts/environment/build-frontend.sh local .env.local
```
