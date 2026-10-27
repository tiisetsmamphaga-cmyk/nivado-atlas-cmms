# Nivado Atlas: free test setup

Source: https://github.com/tiisetsmamphaga-cmyk/nivado-atlas-cmms
Frontend: https://atlas-cmms.vercel.app

Vercel hosts `frontend`; Render Free hosts the Java API; a separate Supabase Free project holds PostgreSQL and the private `atlas-files` bucket. Atlas continues to use its own login and permissions.

## Backend

`render.yaml` provisions only a free web service. It creates no paid database, disk or MinIO server. The running service may also be provisioned directly through Render.

Use Supabase session pooler port 5432 and `sslmode=require&currentSchema=atlas` in `DB_URL` (omit the `jdbc:postgresql://` prefix). Use the restricted `atlas_app` database role and its password. The `atlas` schema is not exposed through Supabase's Data API. Hibernate and Liquibase default to this schema.

Set backend-only `SUPABASE_SECRET_KEY` to a secret or legacy service_role key. Never place it in the frontend or source code. Set `SUPABASE_URL`, `SUPABASE_STORAGE_BUCKET=atlas-files`, `STORAGE_TYPE=SUPABASE`, and restrict `ALLOWED_ORGANIZATION_ADMINS` to the owner's email. The storage bucket must remain private; Atlas issues short-lived signed URLs after authorizing requests.

Set `PUBLIC_FRONT_URL` and `PUBLIC_HOME_URL` to the Vercel origin. `PUBLIC_API_URL` uses the Render external URL. Email notifications are disabled until SMTP is configured.

## Frontend

Set Vercel `ATLAS_API_URL` to the Render origin, without `/api`, then redeploy. Public runtime settings are generated during builds. No Supabase private credentials belong in Vercel's frontend environment.

## Free-plan limits

Render sleeps when idle, so startup can be slow and scheduled jobs do not run during sleep. Supabase may pause after inactivity; database and file quotas apply. This is a test setup; verify Java memory use, signup, login and uploads before relying on it. No paid upgrades are enabled automatically.
