# TLS Keystore Provisioning

This directory holds the PKCS12 keystore used by the Spring Boot backend when
`SSL_ENABLED=true`. The keystore file (`dev-keystore.p12` or a production
equivalent) is **gitignored** and must be provisioned per deployment.

## Default Docker Compose Setup (SSL Enabled)

The default `docker-compose.yml` ships with `SSL_ENABLED=true`. The backend
serves HTTPS on port 8443 and nginx proxies to it over HTTPS. A keystore is
required in this configuration.

## Enabling TLS (Local HTTPS)

### Step 1 — Generate a self-signed keystore (development / internal CA)

```bash
keytool -genkeypair \
  -alias eventops \
  -keyalg RSA \
  -keysize 2048 \
  -storetype PKCS12 \
  -keystore backend/src/main/resources/keystore/dev-keystore.p12 \
  -validity 365 \
  -storepass changeit \
  -dname "CN=localhost, OU=EventOps, O=EventOps, L=Facility, ST=Local, C=US"
```

For a production deployment, replace the self-signed cert with one issued by
your facility's internal CA and adjust the `-dname` accordingly.

### Step 2 — Set environment variables

```bash
SSL_ENABLED=true
SSL_KEYSTORE=classpath:keystore/dev-keystore.p12  # or filesystem path
SSL_KEYSTORE_PASSWORD=changeit
```

In `docker-compose.yml`, mount the keystore as a volume or set
`SSL_KEYSTORE` to an absolute path that is bind-mounted into the container.

### Step 3 — Update nginx proxy

When the backend serves HTTPS, change `nginx.conf`:

```nginx
location /api/ {
    proxy_pass https://backend:8443/api/;
    proxy_ssl_verify off;   # for self-signed cert; remove for CA-signed
    ...
}
```

## Environment Variables Reference

| Variable                | Default                               | Description                 |
| ----------------------- | ------------------------------------- | --------------------------- |
| `SSL_ENABLED`           | `true`                                | Enable HTTPS on the backend |
| `SSL_KEYSTORE`          | `classpath:keystore/dev-keystore.p12` | Path to PKCS12 keystore     |
| `SSL_KEYSTORE_PASSWORD` | `changeit`                            | Keystore password           |

## Security Notes

- Never commit keystore files (`.p12`, `.jks`) to version control.
- In production, use a strong keystore password distinct from `changeit`.
- Request-signature verification (`SIGNATURE_ENABLED=true`) provides
  message-level authenticity independent of TLS; keep both enabled in
  production.
