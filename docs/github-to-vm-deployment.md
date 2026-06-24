# GitHub → 內網 VM 部署

此專案以 GitHub **私有 repository** 保存原始碼；部署電腦從 GitHub 取得指定版本後，經內網以 SSH/rsync 將原始碼同步到 VM，最後由 VM 的 Docker Compose 建置並啟動服務。

## 一次性準備

1. 在 GitHub 建立空白的 private repository，例如 `BusTracking`。建立時不要勾選 README、`.gitignore` 或 license，避免首次推送衝突。
2. 部署電腦須安裝 Git、OpenSSH client 與 rsync；VM 須安裝 Docker Engine 和 Docker Compose plugin，並允許 TCP `22`、`80`、`8080`。
3. VM 若不能連網，請先在可連網的電腦完成 Docker image build，使用 `docker image save`/`docker image load` 匯入 VM；Maven、npm 與 Docker base image 都需要外部套件來源。

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

## 部署電腦：取得指定版本並同步到 VM

首次下載：

```bash
git clone git@github.com:<GITHUB_OWNER>/BusTracking.git
cd BusTracking
git checkout main
```

之後更新：

```bash
git fetch origin
git checkout main
git pull --ff-only origin main
```

以下將原始碼同步至 VM。請把帳號、VM IP 與目錄換成實際值；`.env` 只保留在 VM，不會被覆蓋：

```bash
rsync -az --delete \
  --exclude '.git/' --exclude '.env' --exclude 'target/' --exclude 'build/' --exclude 'dist/' --exclude 'node_modules/' \
  ./ deploy-user@VM_IP:/opt/bus-tracking/
```

## VM：設定與啟動

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
docker compose --env-file .env up -d --build
docker compose ps
curl -f http://localhost:8080/actuator/health
```

管理介面為 `http://VM_IP/`；後端 Swagger 為 `http://VM_IP:8080/swagger-ui.html`；Android 裝置 API 使用 `http://VM_IP:8080`。資料庫資料保存在 Docker named volumes，正常更新服務不會刪除資料。

## 之後每次發布

在開發電腦完成驗證後：

```bash
git add .
git commit -m "Describe this release"
git push origin main
```

然後在部署電腦執行「取得指定版本並同步到 VM」，最後在 VM 執行 `docker compose --env-file .env up -d --build`。建議每次發布以 Git tag 固定版本，例如 `git tag -a v0.1.0 -m "first VM release" && git push origin v0.1.0`，部署電腦則 checkout 該 tag。
