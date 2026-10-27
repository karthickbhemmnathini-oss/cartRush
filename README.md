# Cart Rush

Cart Rush is a Spring Boot shopping application with a MySQL-backed catalogue, cart, checkout and order flows, plus a Gemini-powered shopping assistant with local fallback behavior.

## Requirements

- Java 17
- MySQL 8.x
- Maven Wrapper (included)
- Optional: a Gemini API key for AI classification and generated replies

## Configure

Set these environment variables before starting the application:

- `DB_PASSWORD`: password for the configured MySQL account (`cart_rush_app`)
- `GEMINI_API_KEY`: Gemini API key; leave unset to use the assistant's local fallback
- `APP_ASSISTANT_GEMINI_MODEL`: optional model override; defaults to `gemini-3.8-flash`

Secrets are intentionally not stored in `application.properties`. Do not commit credentials or local override files.

PowerShell example:

```powershell
$env:DB_PASSWORD = "your-local-database-password"
$env:GEMINI_API_KEY = "your-gemini-api-key"
.\mvnw.cmd spring-boot:run
```

Run without Gemini by omitting `GEMINI_API_KEY`. MySQL must be available at the URL configured in `application.properties`.

## Verify

```powershell
.\mvnw.cmd test
```

See [HELP.md](HELP.md) for API details and application workflows.
