# CSON Converter — Backend

## Table of Contents

- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [API Endpoints](#api-endpoints)
- [Error Responses](#error-responses)
- [Running Locally](#running-locally)

---

## Technology Stack

| Category        | Technology                                      |
|-----------------|-------------------------------------------------|
| Language        | Java 25                                         |
| Framework       | Spring Boot 4.0.1                               |
| Security        | Spring Security, JWT (jjwt 0.13.0)              |
| Persistence     | Spring Data JPA, Hibernate, PostgreSQL 15       |
| Migrations      | Flyway                                          |
| Serialization   | Jackson (JSON / XML / CSV)                      |
| Email           | Spring Mail, Gmail SMTP                         |
| Build           | Maven 3.9+                                      |
| Utilities       | Lombok                                          |

---

## Architecture

The project follows a three-layered architecture:

```
HTTP Request
    │
    ▼
RequestFilter (validates JWT token, populates SecurityContext)
    │
    ▼
Controllers (controllers/frontend/, controllers/api/)
    │  DTO mapping via Mappers
    ▼
Services (services/frontend/, services/api/)
    │  Entity mapping via Mappers
    ▼
Repositories (Spring Data JPA)
    │
    ▼
Database (PostgreSQL)
```

### Layer responsibilities

- **Controllers** — parse HTTP requests, delegate to services, serialize responses. Split into two groups:
    - `frontend/` — multipart file upload endpoints + auth + user/pattern/modification CRUD.
      CORS configured for `https://cson.site`.
    - `api/` — direct body-based JSON↔XML conversion.
- **Services** — business logic: file parsing, format conversion, pattern application, user authentication, JWT generation.
- **Repositories** — Spring Data JPA repositories for `User`, `Pattern`, `Modification`, `VerificationCode` entities.
- **DTOs** — separate controller DTOs (exchanged with clients) and service DTOs (passed between layers).
  Creation and update operations use dedicated `ToCreate` / `ToUpdate` variants.
- **Mappers** — bidirectional conversion between entities and DTOs, and between service-layer DTOs and controller-layer DTOs.

---

## Project Structure

```
src/main/java/team/anonyms/converter/
├── configs/                - Spring configuration beans (Security, CORS, Jackson, etc.)
├── controllers/
│   ├── frontend/           - Auth, User, Pattern, Modification, Conversion endpoints
│   └── api/                - Direct body-based conversion endpoints
├── dto/
│   ├── controller/         - DTOs exchanged with HTTP clients
│   └── service/            - DTOs passed between layers
├── entities/               - JPA entities: User, Pattern, Modification, VerificationCode
├── exceptions/             - Custom exception classes
├── mappers/                - Bidirectional DTO↔Entity converters
├── repositories/           - Spring Data JPA repositories
├── scheduling/             - Scheduled cleanup of expired verification codes
├── security/               - RequestFilter: JWT validation, SecurityContext population
└── services/
    ├── frontend/           - Business logic: auth, users, patterns, conversion
    └── api/                - Direct conversion logic
```

---

## API Endpoints

All endpoints are served under the base path `/api/v1`

### Authentication — `/auth`

#### `POST /auth`
Log in with credentials. The existing JWT is read from the `jwtToken` cookie —
if still valid, it is reused; otherwise a new one is generated.

**Request body** (`application/json`):
```json
{
  "email": "user@example.com",
  "password": "secret"
}
```

**Response `200 OK`** — sets `jwtToken` `HttpOnly; Secure; SameSite=Strict` cookie (4 h) and returns:
```json
{
  "success": true,
  "username": "john",
  "email": "john@example.com",
  "userId": "uuid"
}
```

**Response `400 Bad Request`** — invalid credentials.

---

#### `POST /auth/email/verification`
Submit the 6-digit code that was emailed after registration. Requires authentication.

**Request body** (`text/plain`): the 6-digit code string.

**Response `200 OK`**:
```json
true
```
Returns `false` if the code is wrong or expired.

**Response `400 Bad Request`** (`EMAIL ALREADY VERIFIED`) — email is already verified.

---

#### `POST /auth/email/resending`
Request a new email verification code. Requires authentication.

**Response `204 No Content`**

---

#### `POST /auth/password/reset`
Request a password-reset verification code to be sent to the provided email address.
No authentication required.

**Request body** (`text/plain`): the email address.

**Response `204 No Content`**

---

#### `POST /auth/password/verification`
Verify the password-reset code and set a new password.
No authentication required.

**Request body** (`application/json`):
```json
{
  "email": "user@example.com",
  "verificationCode": "123456",
  "newPassword": "newSecret"
}
```

**Response `200 OK`**:
```json
true
```
Returns `false` if the code is wrong or expired.

---

#### `DELETE /auth`
Log out — clears the `jwtToken` cookie.

**Response `204 No Content`**

---

### Users — `/users`

#### `POST /users`
Create a new account. Triggers a verification email to the provided address.

**Request body** (`application/json`):
```json
{
  "username": "john",
  "email": "john@example.com",
  "password": "secret"
}
```

**Response `201 Created`** — sets `jwtToken` `HttpOnly; Secure; SameSite=Strict` cookie and returns:
```json
{
  "success": true,
  "username": "john",
  "email": "john@example.com",
  "userId": "uuid"
}
```

**Response `400 Bad Request`** (`EMAIL EXISTS`) — email already registered.

---

#### `PUT /users`
Update the current user's username and/or password. The user is identified by the JWT. Requires authentication.

**Request body** (`application/json`):
```json
{
  "username": "new_name",
  "password": "newPassword"
}
```

**Response `200 OK`**:
```json
{
  "id": "uuid",
  "username": "new_name",
  "email": "current@example.com",
  "isVerified": true
}
```

**Response `404 Not Found`** — user not found.

---

#### `PUT /users/email`
Update the current user's email address. The user is identified by the JWT. Requires authentication.
After updating, the new address is unverified and a verification email is sent automatically.

**Request body** (`text/plain`): the new email address.

**Response `200 OK`**:
```json
{
  "id": "uuid",
  "username": "john",
  "email": "new@example.com",
  "isVerified": false
}
```

**Response `404 Not Found`** — user not found.

---

#### `DELETE /users`
Delete the current user's account. The user is identified by the JWT. Requires authentication.
Clears the `jwtToken` cookie on success.

**Response `204 No Content`**

**Response `404 Not Found`** — user not found.

---

### Patterns — `/patterns`

All pattern endpoints require authentication. The user is identified by the JWT cookie.

#### `GET /patterns/{limit}/{offset}`
Get a paginated slice of patterns belonging to the authenticated user.

**Path variables:**
- `limit` — maximum number of items to return
- `offset` — number of items to skip

**Response `200 OK`**:
```json
[
  { "id": "uuid", "name": "My Pattern" },
  { "id": "uuid", "name": "Another Pattern" }
]
```

---

#### `GET /patterns`
Get the total number of patterns belonging to the authenticated user.

**Response `200 OK`**:
```json
5
```

---

#### `POST /patterns`
Create a new pattern for the authenticated user.

**Request body** (`application/json`):
```json
{
  "name": "My Pattern",
  "modifications": [
    {
      "oldName": "field",
      "newName": "renamed field",
      "newType": null,
      "newValue": null
    }
  ]
}
```

**Response `201 Created`**:
```json
{
  "id": "uuid",
  "name": "My Pattern"
}
```

---

#### `PUT /patterns`
Update an existing pattern. The `id` must be present in the body. Requires ownership — the pattern must belong to the authenticated user.
Note, that this API endpoint is the only way to update modifications.
Modifications are updated only through updating a pattern they belong to.

**Request body** (`application/json`):
```json
{
  "id": "uuid",
  "name": "Updated Pattern",
  "modifications": [
    {
      "id": "uuid",
      "oldName": "field",
      "newName": "renamed field",
      "newType": null,
      "newValue": null
    }
  ]
}
```

**Response `200 OK`**:
```json
{
  "id": "uuid",
  "name": "Updated Pattern"
}
```

**Response `403 Forbidden`** — pattern belongs to another user.

**Response `404 Not Found`** — pattern not found.

---

#### `DELETE /patterns/{patternId}`
Delete a pattern and all its modifications. Requires ownership — the pattern must belong to the authenticated user.

**Path variable:** `patternId` (UUID)

**Response `204 No Content`**

**Response `403 Forbidden`** — pattern belongs to another user.

**Response `404 Not Found`** — pattern not found.

---

### Modifications — `/modifications`

All modification endpoints require authentication. Ownership is verified — the pattern must belong to the authenticated user.

#### `GET /modifications/{patternId}/{limit}/{offset}`
Get a paginated slice of modifications belonging to a pattern.

**Path variables:**
- `patternId` — UUID of the pattern
- `limit` — maximum number of items to return
- `offset` — number of items to skip

**Response `200 OK`**:
```json
[
  {
    "id": "uuid",
    "oldName": "field",
    "newName": "renamed field",
    "newType": null,
    "newValue": null
  }
]
```

**Response `403 Forbidden`** — pattern belongs to another user.

**Response `404 Not Found`** — pattern not found.

---

#### `GET /modifications/{patternId}`
Get the total number of modifications belonging to a pattern.

**Path variable:** `patternId` (UUID)

**Response `200 OK`**:
```json
3
```

**Response `403 Forbidden`** — pattern belongs to another user.

**Response `404 Not Found`** — pattern not found.

---

### File Conversion — `/conversion`

No authentication required. All endpoints consume `multipart/form-data` and stream the converted file back as an attachment.

**Multipart form part:**

| Part   | Type                        | Required | Description                |
|--------|-----------------------------|----------|----------------------------|
| `file` | `multipart/form-data` part  | yes      | The source file to convert |

**Query parameter:**

| Parameter | Type | Required | Description                                       |
|-----------|------|----------|---------------------------------------------------|
| `pattern` | UUID | no       | ID of a saved pattern to apply during conversion. |

When `pattern` provided, each row of the data is transformed by the pattern's modifications
before the output is written. If omitted, the file is converted as-is.

**Common error responses:**
- `400 Bad Request` — file extension does not match the declared source format.
- `500 Internal Server Error` — I/O error during conversion.

| Endpoint                    | Source format | Target format | Response `Content-Type`    |
|-----------------------------|---------------|---------------|----------------------------|
| `POST /conversion/json/csv` | `.json`       | `.csv`        | `text/csv`                 |
| `POST /conversion/csv/json` | `.csv`        | `.json`       | `application/octet-stream` |
| `POST /conversion/json/xml` | `.json`       | `.xml`        | `application/octet-stream` |
| `POST /conversion/xml/json` | `.xml`        | `.json`       | `application/octet-stream` |
| `POST /conversion/xml/csv`  | `.xml`        | `.csv`        | `text/csv`                 |
| `POST /conversion/csv/xml`  | `.csv`        | `.xml`        | `application/octet-stream` |

All conversion responses include a `Content-Disposition: attachment; filename="<original_name>.<target_format>"` header.

---

### Direct Conversion API — `/direct/conversion`

No authentication required. Accepts and returns raw content bodies.

#### `POST /direct/conversion/json/xml`
Convert a JSON body to XML.

**Request:** `Content-Type: application/json` — any JSON object.

**Response `200 OK`:** `Content-Type: application/xml` — XML string.

---

#### `POST /direct/conversion/xml/json`
Convert an XML body to JSON.

**Request:** `Content-Type: application/xml` — any XML document.

**Response `200 OK`:** `Content-Type: application/json` — JSON string.

---

## Error Responses

All unhandled exceptions are caught by `GlobalExceptionHandler`. The mapping is:

| Exception                        | HTTP Status                 | Response body                              |
|----------------------------------|-----------------------------|--------------------------------------------|
| `EntityNotFoundException`        | `404 Not Found`             | empty                                      |
| `UnsupportedExtensionException`  | `400 Bad Request`           | empty                                      |
| `IllegalArgumentException`       | `400 Bad Request`           | empty                                      |
| `NullPointerException`           | `500 Internal Server Error` | empty                                      |
| `CredentialException`            | `400 Bad Request`           | JSON — `message: "CREDENTIAL"`             |
| `IllegalPatternException`        | `400 Bad Request`           | JSON — `message: "PATTERN"`                |
| `EmailAlreadyExistsException`    | `400 Bad Request`           | JSON — `message: "EMAIL ALREADY EXISTS"`   |
| `EmailAlreadyVerifiedException`  | `400 Bad Request`           | JSON — `message: "EMAIL ALREADY VERIFIED"` |
| `AccessDeniedException`          | `403 Forbidden`             | JSON — `message: "ACCESS DENIED"`          |

Exceptions that return a JSON body use the following envelope (defined in `ExceptionResponse`):

```json
{
  "message": "CREDENTIAL",
  "time": "2025-01-01T00:00:00Z"
}
```

---

## Running Locally

**Prerequisites:** Java 25, Maven 4.0+, PostgreSQL 15.

Set the required environment variables:

| Variable               | Description                                             |
|------------------------|---------------------------------------------------------|
| `JWT_SECRET`           | Secret key used to sign JWT tokens                      |
| `DB_USER`              | PostgreSQL username                                     |
| `DB_PASSWORD`          | PostgreSQL password                                     |
| `DB_URL`               | JDBC URL — `jdbc:postgresql://host:port/db`             |
| `EMAIL_ADDRESS`        | Gmail account used for sending verification emails      |
| `EMAIL_PASSWORD`       | Gmail app password                                      |
| `EMAIL_SENDER_ADDRESS` | Display address in the `From` header                    |

Then build and run:

```bash
mvn spring-boot:run
```

The application starts on port `8080`. Base path: `/api/v1`. Maximum upload size: **10 MB**.
Flyway applies database migrations automatically on startup.