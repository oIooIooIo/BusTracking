# Admin Web

React, TypeScript, Vite, Ant Design, and Leaflet administration UI.

Implemented Demo functions:

- Admin login
- Bus and employee creation
- Employee-to-bus permission grant and revoke
- Bus route query and map display
- Boarding-event display

## Run

Start the backend first, then:

```bash
cd apps/admin-web
npm install
npm run dev
```

Open `http://localhost:5173` and use `admin` / `admin123`.

The Vite development server calls the backend at `http://localhost:8080`.

## Verify

```bash
npm run lint
npm run build
```
