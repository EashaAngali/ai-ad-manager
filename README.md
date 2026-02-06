# AI Ad-Manager (Full Stack)

## What it does
- Upload ad screenshot (PNG/JPG)
- Gemini Vision returns structured critique (JSON)
- Saves history in DB
- Dashboard shows past critiques

## Local run (fastest)
### Backend
- Java 17
- Set GEMINI_API_KEY
- Run:
  cd backend
  mvn spring-boot:run

Backend uses in-memory H2 (Postgres mode) by default.

### Frontend
  cd frontend
  npm install
  npm run dev

Open: http://localhost:5173

## Deploy on Render (Postgres)
1) Create Render PostgreSQL database
2) Backend Web Service:
   - Root: backend
   - Build: ./mvnw clean package -DskipTests  (or mvn clean package if mvnw not present)
   - Start: java -jar target/*.jar
   - Env:
     - GEMINI_API_KEY
     - GEMINI_MODEL (optional)
     - SPRING_PROFILES_ACTIVE=prod
     - JDBC_DATABASE_URL=jdbc:postgresql://... (recommended)
     - DB_USER / DB_PASSWORD (if you use separate fields)

3) Frontend Static Site:
   - Root: frontend
   - Build: npm install && npm run build
   - Publish: dist
   - Env:
     - VITE_API_BASE = https://<your-backend>.onrender.com
