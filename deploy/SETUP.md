# Nivado Atlas deployment

Source and frontend deployment settings are prepared. Backend, PostgreSQL and storage still need provisioning. Login is not operational yet.

## Vercel frontend
Use this fork, branch main, Root Directory frontend. frontend/vercel.json supplies build settings. ATLAS_API_URL must be the actual Java backend HTTPS origin, without /api. The backend serves endpoints at its root. FRONTEND_URL is https://atlas-cmms.vercel.app.

## Railway backend
Create a PostgreSQL service named Postgres and an API service from this fork. Set API Root Directory /api and Config File /api/railway.json. Use the existing Dockerfile and target port 8080.

Set DB_URL from the Postgres private host, port and database name; DB_USER and DB_PWD from Railway reference variables. Generate a strong JWT_SECRET_KEY privately. Set PUBLIC_FRONT_URL=https://atlas-cmms.vercel.app and PUBLIC_API_URL to the generated API HTTPS origin. Restrict ALLOWED_ORGANIZATION_ADMINS to the owner email before opening signup. Set STORAGE_TYPE=MINIO and configure persistent private object storage with its internal endpoint, bucket, credentials and public HTTPS endpoint. Never commit credentials.

Use ENABLE_CORS=true and SINGLE_INGRESS=false for separate frontend/backend hosts. Disable email-dependent features until SMTP is configured: INVITATION_VIA_EMAIL=false, ENABLE_EMAIL_NOTIFICATIONS=false, ENABLE_MAIL_HEALTH_CHECK=false. Set MAIL_RECIPIENTS to an empty value.

Check startup migrations and /health-check, then test signup, login, work-order persistence and attachment upload/retrieval. The upstream health endpoint only reports application liveness.

## Licence
Preserve the upstream licence and attribution. This fork does not bypass branding entitlements or advanced-feature checks.
