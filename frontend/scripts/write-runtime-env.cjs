const fs = require('node:fs');
const origin = process.env.FRONTEND_URL || 'https://atlas-cmms.vercel.app';
const apiUrl = process.env.ATLAS_API_URL || `${origin}/api`;
for (const value of [origin, apiUrl]) {
  const url = new URL(value);
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Expected an HTTP(S) URL');
}
// Public frontend settings only. Never serialize the whole environment.
const config = {
  API_URL: apiUrl,
  HOME_URL: process.env.ATLAS_HOME_URL || 'https://atlas-cmms.com',
  CLOUD_VERSION: 'false',
  INVITATION_VIA_EMAIL: 'false',
  ENABLE_SSO: 'false',
  LDAP_ENABLED: 'false'
};
fs.writeFileSync('public/runtime-env.js', `window.__RUNTIME_CONFIG__=${JSON.stringify(config)};\n`);
