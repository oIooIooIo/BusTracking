# DEV Offline Images 發布與 VM 部署 SOP

> **Implementation status:** Offline-image packaging rules remain authoritative.
> DEV and PROD have adopted Blue-Green application deployment, but the current
> Compose file and `deploy-offline.sh` still implement one application stack.
> Their deployment commands are not a zero-downtime SOP and require separate
> owner approval until the second-stage Blue-Green implementation is complete.

## 文件目的

GitHub 只用來保存、審查與追蹤原始碼版本。DEV Server 不從 GitHub
clone、pull 或現場建置程式；每次發布都必須先在打包電腦完成建置與驗證，
再將已打包的 offline images 交付到 DEV Server，以 `docker load` 和 Docker
Compose 更新服務。

本文件是長期部署流程，不記錄單一版本的功能差異。每個 offline package
必須另外附帶該版本的 release manifest，明確記錄來源 commit、程式碼差異、
資料庫 migration、驗證結果與 rollback 限制。

PROD 採用相同的 Offline Images 與 Blue-Green 原則，但目前環境仍在準備中；
未核准的 PROD 值必須保持空白，且不得直接套用 DEV 值。

## 不可混入部署包的內容

- 不可放入已填值的 `.env.dev`、密碼、API key、token 或其他 runtime secrets。
- 不可放入 TLS private key。DEV Server 應繼續使用 Server 上既有、已核准的
  TLS 檔案。
- 不可把開發電腦的資料庫 dump 當成一般程式發布內容。只有在專案擁有者
  明確要求資料搬移或還原時，才能另外建立受控的 DB 交付包。
- 不可用 DEV 範本覆蓋 Server 上既有的 `.env.dev`。
- 不可把未提交的程式修改默認納入發布。打包前必須確認 Git 狀態；若修改
  應發布，先完成審查並 commit。

## 每次發布必須先確定版本範圍

打包前先記錄本次與上一個已部署版本：

```bash
git status --short
git rev-parse HEAD
git log -1 --format='%H %cI %s'
git diff --stat <previous-release-commit> HEAD
git log --oneline <previous-release-commit>..HEAD
```

必須使用上一個「實際部署到 DEV」的 commit 作比較基準，不能只看最新一個
commit，也不能只依 commit 訊息推測內容。建議替每次已部署版本建立 Git tag，
並在 release manifest 同時記錄完整 commit SHA。

若 `git status --short` 有輸出，release manifest 必須列出原因與處理結果；
正常發布應改用乾淨 checkout 或乾淨 worktree 重新建置。

## 如何描述程式碼改動

release manifest 的「程式碼改動」至少要列出：

- 受影響元件：Backend、Admin Web、Android、Compose 或其他服務。
- 使用者可見行為：新增、變更或移除的功能。
- API 影響：endpoint、request、response、驗證或相容性變化。
- 執行影響：新增背景工作、連接埠、volume、服務相依或重啟需求。
- 未納入範圍：例如只有 Android 改動時，要明確寫明 Server images 未變更。

只列檔名或 commit 標題不足以判斷部署影響；必須根據上一版到本版的實際
diff 撰寫摘要。

## 如何描述資料庫改動

Backend 使用 Flyway。每次發布都必須直接比較 migration 目錄：

```bash
git diff --name-status <previous-release-commit> HEAD -- \
  services/backend/src/main/resources/db/migration
git diff <previous-release-commit> HEAD -- \
  services/backend/src/main/resources/db/migration
```

release manifest 必須明確寫其中一種結果：

- `DB schema change: none`；或
- 列出每一個新增 migration 的版本、檔名、DDL、資料轉換、預期影響與相容性。

有 DB migration 時，至少說明：

- 新增或修改的 table、column、index、constraint、sequence 與 foreign key。
- 是否更新或回填既有資料，以及資料量大時可能造成的 lock 或執行時間。
- migration 前後的 Flyway 版本。
- 新 schema 是否仍相容於上一版 Backend。
- rollback 是可以只切回舊 image，還是必須還原 DB backup 或另外做 forward fix。

已在任何共用環境執行過的 Flyway migration 不可修改、重新命名或重用版本號；
後續調整必須新增下一個 migration。資料庫結構的來源必須是 repository 中的
migration，不能以開發電腦或 DEV Server 上手動修改後的 schema 作為發布依據。

Backend image 內含 migration。新 Backend container 啟動時，Flyway 會讀取
DEV DB 的 `flyway_schema_history`，只執行尚未套用的 migration。一般 offline
image 更新不會以開發電腦資料覆蓋 DEV DB；PostgreSQL named volume 會保留。

Blue-Green 期間新舊 Backend 會短暫同時使用同一份 schema，因此 migration
必須同時相容於兩個版本，採用先擴充、後清理的方式。無法保持向後相容的
migration 必須明確宣告維護時段並另行取得批准，不得標示為零停機部署。

## 建置與驗證

打包電腦需具備 Docker Engine，以及 Backend、Admin Web 所需的建置與測試
工具。使用專案擁有者已核准、未受 Git 追蹤的 `.env.dev`；不得在打包過程
新增、推測或修改環境值。

執行任何測試或打包命令前，必須依 `AGENTS.md` 列出目標環境、環境檔案、
完整命令與影響範圍，並取得專案擁有者對該次操作的明確同意。

依改動範圍執行測試。以下從 repository root 執行：

```bash
source scripts/environment/load-env.sh
bus_env_load local .env.local
(cd services/backend && ./mvnw clean test package)
(cd apps/admin-web && npm run lint)

./scripts/environment/build-frontend.sh dev .env.dev
```

上述 LOCAL 測試必須使用核准的 LOCAL 服務與設定，不可指向 DEV DB。測試若
因 seed 狀態、環境或 assertion 失敗，release manifest 必須如實記錄；不能只寫
「建置成功」。已知失敗是否允許發布，由專案擁有者決定。

使用受控入口建置並匯出 DEV images：

```bash
./scripts/environment/build-offline-images.sh dev .env.dev
```

此腳本會使用 `.env.dev` 中已核准的 `BACKEND_IMAGE`、`ADMIN_WEB_IMAGE`、
`POSTGRES_IMAGE`、`REDIS_IMAGE`、`VITE_API_URL` 與 `OFFLINE_PACKAGE_NAME`。
不得另外手動輸入不同的 image tag 或 API URL；若需變更任何環境值，必須先
依環境設定變更管制取得批准。

## Offline package 必要內容

每一版交付包至少包含：

- `OFFLINE_PACKAGE_NAME` 指定的 Docker image tar。
- `infra/vm/compose.yaml`。
- `config/environments/dev.env.example`，只作欄位與核准值參考。
- `RELEASE_MANIFEST.md`。
- 包內檔案的 `SHA256SUMS.txt`。
- 必要時附上本文件；不得以舊版本文件描述新版本部署。

`RELEASE_MANIFEST.md` 必須包含以下欄位：

1. Release 名稱與建立時間。
2. 本次完整 Git commit SHA 與上一個 DEV release commit SHA。
3. Git working tree 是否乾淨。
4. 包含的完整 image names、tags，建議另記 image digest。
5. 程式碼改動摘要及受影響元件。
6. DB schema change；若有，逐一列出 migration 檔案與資料影響。
7. 執行過的測試、結果、警告與已知問題。
8. 部署前置條件、預估停機或 service restart 影響。
9. rollback 方法，以及 DB migration 是否限制 image rollback。
10. Offline image tar 與最外層 ZIP 的 SHA-256。

壓縮完成後必須實際驗證 ZIP 與 checksum，並確認 image tar 內的 image tags
和 `.env.dev` 核准值一致。

## DEV Server 部署前檢查

1. 驗證收到的最外層 ZIP checksum。
2. 解壓後執行 `sha256sum -c SHA256SUMS.txt`。
3. 閱讀 `RELEASE_MANIFEST.md`，確認來源 commit、images、DB migration 與已知問題。
4. 確認 Server 現有 `.env.dev`、TLS 憑證與 private key 存在；例行更新不得覆蓋。
5. 若包含 DB migration，先建立 DEV DB backup，並確認 backup 可讀取及保存位置。
6. 確認磁碟空間足夠同時保留目前 images、offline tar、backup 與新 images。

可使用 container 內既有的 PostgreSQL 環境值建立邏輯備份：

```bash
cd /opt/bus-tracking
docker compose --env-file .env.dev -f infra/vm/compose.yaml \
  exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > /opt/bus-tracking/backups/bus-tracking-before-release.dump
```

備份檔名及保存策略由部署人員依實際 release 管理；不可未經確認覆蓋上一份
可用 backup。

## DEV Server 載入 images 與目前單一 Stack 限制

DEV Server 不執行 `git pull`、`docker build`、Maven 或 npm。使用 release
manifest 中列出的實際檔名載入 images：

```bash
docker load -i <offline-image-tar>
docker images | grep -E 'bus-tracking|postgis|redis'
```

以下命令是現有單一 application stack 的舊部署入口，不是核准完成的
Blue-Green 流量切換 SOP。它會在 image 或設定變更時停止並重建 container，
因此可能中斷 Android API 與 Admin Web，不得描述為零停機更新。

只有在專案擁有者針對該次部署明確同意可能中斷服務後，才能執行：

```bash
cd /opt/bus-tracking
docker compose --env-file .env.dev -f infra/vm/compose.yaml up -d
docker compose --env-file .env.dev -f infra/vm/compose.yaml ps
```

第一次建立環境與例行更新是不同程序。只有第一次部署，且已取得環境值批准
時，才能由 DEV 範本建立 `.env.dev`；例行發布一律保留 Server 現有檔案。

## 部署後驗證

先確認 Backend health：

```bash
curl -f http://localhost:8080/actuator/health
```

若本版含 DB migration，檢查 Backend logs 及 Flyway history：

```bash
cd /opt/bus-tracking
docker compose --env-file .env.dev -f infra/vm/compose.yaml logs --no-color backend \
  | grep -E 'Flyway|migration|schema'

docker compose --env-file .env.dev -f infra/vm/compose.yaml \
  exec -T postgres sh -c \
  'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT installed_rank, version, description, success \
   FROM flyway_schema_history ORDER BY installed_rank;"'
```

最後依 release manifest 驗證受影響功能、Admin Web、Android API，以及 DB
資料回填或 constraint 結果。只確認 container 是 `running` 不代表 migration
與功能都正確。

## 失敗處理與 rollback 原則

- Backend migration 失敗時，保留 logs 與 DB backup，不可直接修改
  `flyway_schema_history`、刪除 constraint 或反覆手動執行部分 SQL。
- 只有程式碼改動且 DB schema 相容時，可以切回上一版 image tags 後重新執行
  Compose。
- 已成功套用 DB migration 時，切回舊 Backend image 不一定安全。依 release
  manifest 的相容性結論選擇舊 image、forward fix，或在取得批准後還原 DB backup。
- 還原 DB backup 會覆蓋 migration 後的新資料，屬於具資料損失風險的操作，
  必須由專案擁有者明確批准。

## 發布完成紀錄

部署完成後，記錄實際部署時間、執行人、release commit、image tags、Flyway
最終版本、health/功能驗證結果與異常處理。此紀錄決定下一版比較用的
`previous-release-commit`，避免再次以日期、ZIP 名稱或記憶猜測版本差異。
