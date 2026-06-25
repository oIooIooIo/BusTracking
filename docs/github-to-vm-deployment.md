# GitHub → 內網 VM 部署

此專案以 GitHub **私有 repository** 保存原始碼；正式交付給 IT 時，建議在可連外的開發電腦先建置、測試並安全掃描 Docker images，使用 `docker save` 匯出離線 image tar，再由 IT 在內網 VM 使用 `docker load` 與 Docker Compose 啟動服務。

## 一次性準備

1. 在 GitHub 建立空白的 private repository，例如 `BusTracking`。建立時不要勾選 README、`.gitignore` 或 license，避免首次推送衝突。
2. 打包電腦須安裝 Docker Engine，且可以連到 Docker Hub 下載 base images。VM 須安裝 Docker Engine 和 Docker Compose plugin，並允許 TCP `22`、`443`、`8080`。
3. Admin Web 使用 HTTPS `443`。IT 需提供憑證與 private key，放在 VM 的 `/opt/bus-tracking/secrets/tls/tls.crt` 與 `/opt/bus-tracking/secrets/tls/tls.key`。

## 首次推送到 GitHub

在專案根目錄執行。將 `<GITHUB_OWNER>` 換成 GitHub 帳號或組織名稱：

```bash
git init -b main
git add .
git status
git commit -m "Initial Bus Tracking source"
git remote add origin git@github.com:<GITHUB_OWNER>/BusTracking.git
git push -u origin main
```

推送前請確認 `git status` 沒有列出 `.env`、`local.properties`、`target/`、`build/`、`node_modules/` 或任何密碼/API key。GitHub 建議使用 SSH key 或 fine-grained personal access token；不要把 token 放進程式碼或 `.env.example`。

## 打包電腦：建置離線 images

在可連外的開發電腦取得指定版本：

```bash
git clone git@github.com:<GITHUB_OWNER>/BusTracking.git
cd BusTracking
git checkout main
```

建置 images：

```bash
docker build -t bus-tracking-backend:0.1.0 services/backend
docker build -t bus-tracking-admin-web:0.1.0 apps/admin-web
docker pull postgis/postgis:16-3.4
docker pull redis:7-alpine
```

匯出離線 image tar：

```bash
docker save -o bus-tracking-images-0.1.0.tar \
  bus-tracking-backend:0.1.0 \
  bus-tracking-admin-web:0.1.0 \
  postgis/postgis:16-3.4 \
  redis:7-alpine
```

交付給 IT 的最小檔案：

- `bus-tracking-images-0.1.0.tar`
- `infra/vm/compose.yaml`
- `infra/vm/.env.example`
- IT 提供的 `tls.crt` 與 `tls.key`

## VM：載入 images、設定與啟動

第一次部署先建立目錄：

```bash
sudo mkdir -p /opt/bus-tracking/images /opt/bus-tracking/secrets/tls
sudo chown -R bustrk_user:bustrk_user /opt/bus-tracking
chmod 700 /opt/bus-tracking/secrets /opt/bus-tracking/secrets/tls
```

將 `bus-tracking-images-0.1.0.tar` 放到 `/opt/bus-tracking/images/`，將 `compose.yaml` 與 `.env.example` 放到 `/opt/bus-tracking/infra/vm/`，並將 IT 提供的憑證放到：

```text
/opt/bus-tracking/secrets/tls/tls.crt
/opt/bus-tracking/secrets/tls/tls.key
```

載入 images：

```bash
docker load -i /opt/bus-tracking/images/bus-tracking-images-0.1.0.tar
docker images | grep -E 'bus-tracking|postgis|redis'
```

第一次部署才需建立設定檔：

```bash
cd /opt/bus-tracking/infra/vm
cp .env.example .env
chmod 600 .env
nano .env
```

將 `CHANGE_ME_*` 和 `VM_IP_OR_DNS` 全部替換為實際值。完成後啟動或更新服務：

```bash
cd /opt/bus-tracking/infra/vm
docker compose --env-file .env up -d
docker compose ps
curl -f http://localhost:8080/actuator/health
```

管理介面為 `https://VM_IP_OR_DNS/`；後端 Swagger 為 `http://VM_IP:8080/swagger-ui.html`；Android 裝置 API 使用 `http://VM_IP:8080`。資料庫資料保存在 Docker named volumes，正常更新服務不會刪除資料。

## 之後每次發布

在開發電腦完成驗證後：

```bash
git add .
git commit -m "Describe this release"
git push origin main
```

然後在打包電腦重新建置與測試 images，通過安全掃描後交付新的 image tar 給 IT，最後在 VM 執行 `docker load` 與 `docker compose --env-file .env up -d`。建議每次發布以 Git tag 固定版本，例如 `git tag -a v0.1.0 -m "first VM release" && git push origin v0.1.0`，並同步更新 image tag。
