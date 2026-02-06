# AI Ad-Manager (Backend)

## Local run (fastest)
This project defaults to **dev profile** using in-memory H2 (Postgres mode).
So you can run without installing any DB.

### Requirements
- Java 17+
- Maven

### Run
Set your Gemini key:
- Windows PowerShell:
  $env:GEMINI_API_KEY="YOUR_KEY"

Then:
  mvn spring-boot:run

API:
- POST http://localhost:8080/api/ads/analyze (multipart field: file)
- GET  http://localhost:8080/api/ads/history

## Production (Render)
Set environment variables in Render:
- GEMINI_API_KEY
- GEMINI_MODEL (optional)
- JDBC_DATABASE_URL (recommended) OR DATABASE_URL (if it works)
- DB_USER / DB_PASSWORD (only if needed by your connection string)
