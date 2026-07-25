# BusTracking 環境設定基準

本文件是 BusTracking 的環境設定權威說明。環境值的單一來源位於：

- LOCAL：`config/environments/local.env.example`
- DEV：`config/environments/dev.env.example`
- PROD：`config/environments/prod.env.example`

舊文件、README、終端歷史或目前正在運行的 container 若與上述檔案衝突，以上述三份檔案為準。

## 變更管制

環境設定由專案擁有者控管。Codex 或其他自動化工具不得自行新增、刪除、重新命名、推測或修改任何環境欄位和值。

任何環境設定修改都必須依序執行：

1. 說明修改原因。
2. 列出檔案、欄位、舊值與建議新值。
3. 詢問專案擁有者。
4. 取得明確同意後才能修改。

僅要求啟動、停止、重建或部署程式，不代表同意變更環境設定。PROD 空白欄位不得從 LOCAL 或 DEV 自動複製。此規則也記錄於根目錄 `AGENTS.md`，供後續 Codex 工作階段自動讀取。

## 敏感資料規則

- 三份 `.env.example` 可以提交，真正密碼與 API key 不可提交。
- DEV 與 PROD 使用前，先複製成不受 Git 追蹤的 `.env` 或 `.env.*` runtime 檔案。
- `CHANGE_ME_*` 必須在部署前由擁有者提供並批准。
- PROD 欄位刻意保留空白，在基礎設施、網址、帳密、憑證與 image 版本確定前不得填入。

## 環境總覽

| 項目 | LOCAL | DEV | PROD |
|---|---|---|---|
| 執行方式 | 電腦直接啟動，不使用 Docker | Offline Docker images | Offline Docker images |
| Admin Web | `http://localhost:5173` | `https://taxiportal-dev.fushan.fihnbb.com` | 待定 |
| Backend | `http://localhost:8080` | Docker 內部 `backend:8080`，由 Nginx proxy | 待定 |
| Android API | Emulator 使用 `http://10.0.2.2:8080/api/device/v1/` | `https://taxiportal-dev.fushan.fihnbb.com/api/device/v1/` | 待定 |
| PostgreSQL | 電腦 `localhost:5432` | Docker service `postgres:5432` | 待定 |
| Redis | 電腦 `localhost:6379` | Docker service `redis:6379` | 待定 |
| TLS | 不使用 | 使用 IT 提供的憑證 | 待定 |

## LOCAL：完全不使用 Docker

LOCAL 需要電腦本身安裝並啟動 Java 21、Node.js/npm、PostgreSQL 16 + PostGIS、Redis，以及 Android SDK。

可直接使用核准的 LOCAL 範本，或先建立只供本機使用的 runtime 設定：

```bash
cp config/environments/local.env.example .env.local
```

`.env.local` 已被 Git 忽略。若要修改其中任何環境值，仍須先取得專案擁有者同意。

先檢查本機 PostgreSQL 與 Redis（不使用 Docker）：

```bash
./scripts/environment/start-local.sh check
```

啟動後端：

```bash
./scripts/environment/start-local.sh backend
```

另一個終端啟動前端：

```bash
./scripts/environment/start-local.sh frontend
```

建置 Android emulator APK：

```bash
./scripts/environment/start-local.sh mobile
```

若要使用已批准的未追蹤 runtime 檔，將路徑作為最後一個參數，例如
`./scripts/environment/start-local.sh backend .env.local`。

Android emulator 不能用 `localhost` 連到電腦，因此固定使用 `10.0.2.2`。實體 Android 裝置需要使用電腦 LAN IP；該變更只能寫入未追蹤的 runtime 檔案，並須先取得批准。

## DEV：Offline Docker images

建立未追蹤的部署設定：

```bash
cp config/environments/dev.env.example .env.dev
```

由擁有者提供並批准 DEV 的三個 `CHANGE_ME_*` 值後才能建置 Android APK 或部署。

建置並匯出 images 時，前端 API 保持相對路徑，由 Nginx 代理到 backend：

```bash
./scripts/environment/build-offline-images.sh dev .env.dev
```

建置 DEV Android APK：

```bash
./scripts/environment/build-mobile.sh dev .env.dev
```

VM 載入 images 後，使用同一份已批准的 runtime 檔案：

```bash
./scripts/environment/deploy-offline.sh dev .env.dev
```

## PROD：欄位先建立、值保持空白

`config/environments/prod.env.example` 已包含與 LOCAL、DEV 相同類別的欄位，但除 `APP_ENV=prod` 外不填值。未來需逐項確認：

- 正式網址與 DNS
- Admin Web、Backend 與 Android API URL
- PostgreSQL 與 Redis設定
- 管理者帳密與 device API key
- Offline image 名稱及版本
- TLS 憑證目錄
- 防火牆、連接埠與正式部署位置

所有值都必須經專案擁有者明確批准後才能寫入。

## 元件與欄位對照

| 元件 | 使用欄位 |
|---|---|
| Android | `MOBILE_API_BASE_URL`, `MOBILE_USES_CLEARTEXT`, `DEVICE_API_KEY` |
| Admin Web build | `VITE_API_URL`, `ADMIN_WEB_IMAGE` |
| Spring Boot | `SERVER_PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`, `DEVICE_API_KEY`, `ADMIN_WEB_ORIGIN` |
| PostgreSQL | `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_IMAGE` |
| Redis | `REDIS_HOST`, `REDIS_PORT`, `REDIS_IMAGE` |
| Offline deployment | `BACKEND_IMAGE`, `ADMIN_WEB_IMAGE`, `POSTGRES_IMAGE`, `REDIS_IMAGE`, `TLS_CERT_DIR`, `OFFLINE_PACKAGE_NAME` |

## 強制載入行為

- Backend 的環境相關 Spring properties 不再提供 fallback；未載入設定即啟動失敗。
- Admin Web 的 Vite 設定與 API client 都要求 `VITE_API_URL`；未透過核准入口載入即建置或執行失敗。
- Android Gradle 要求 `MOBILE_API_BASE_URL` 與 `DEVICE_API_KEY`，並讀取 `MOBILE_USES_CLEARTEXT`。
- DEV 的 `CHANGE_ME_*`、PROD 空白必填欄位都會在任何建置或部署前被阻擋。
