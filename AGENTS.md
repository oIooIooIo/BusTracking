# BusTracking repository instructions

## Protected environment configuration

The environment configuration is owner-controlled. The protected source-of-truth files are:

- `config/environments/local.env.example`
- `config/environments/dev.env.example`
- `config/environments/prod.env.example`
- `docs/environment-configuration.md`
- Any runtime `.env` file copied from those templates
- Environment-dependent values in Android Gradle, Vite, Spring Boot, Nginx, Dockerfiles, and Compose files

Codex and other automation must not add, remove, rename, infer, normalize, or change any environment field or value without explicit approval from the user in the current conversation.

Before changing protected configuration:

1. Explain why the change is needed.
2. Show the exact files, fields, old values, and proposed new values.
3. Ask the user for approval.
4. Make the change only after the user explicitly agrees.

Starting, stopping, rebuilding, or inspecting an environment must use the existing approved values and does not authorize configuration changes. Missing PROD values must remain blank until the user supplies and approves them. Never copy DEV secrets into PROD or commit populated deployment secrets.

