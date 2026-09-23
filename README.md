# Coding Review with AI

Boilerplate ban dau cho ung dung review code voi AI. Giai doan hien tai chi khoi dong backend, frontend va module AI placeholder; chua co chuc nang review code hoac tich hop AI that.

## Dieu kien

- Java 17
- Maven 3.9+
- Node.js 18+ va npm

## Cau truc

- `code-review-ai`: module Java danh cho tich hop AI sau nay.
- `code-review-api`: REST API Spring Boot chay tren cong 8080.
- `code-review-web`: React/Vite chay tren cong 8000.

## Build va chay backend

Tu thu muc goc:

```bash
mvn clean verify
mvn -pl code-review-api -am spring-boot:run
```

Kiem tra:

- http://localhost:8080/api/health
- http://localhost:8080/api/greeting

## Chay frontend

Mo terminal thu hai:

```bash
cd code-review-web
npm install
npm run dev
```

Mo http://localhost:8000. Frontend se goi `/api/greeting` qua Vite proxy den backend tai `http://localhost:8080`.

## Gioi han giai doan nay

Chua co review code, upload file, dang nhap, database, Docker, Redis, message queue, luu lich su, goi AI that hay quan ly API key.
