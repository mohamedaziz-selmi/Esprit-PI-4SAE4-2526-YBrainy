# Integration Guide

This guide is for teammates who want to run the user service stack locally and make signup, login, Keycloak, avatar upload, and the optional biometric flow work on their own machine.

## What This Service Depends On

- MySQL on `localhost:3306`
- Keycloak on `http://localhost:9190`
- Eureka on `http://localhost:8071`
- User service on `http://localhost:8899`
- API Gateway on `http://localhost:8088`
- Angular app on `http://localhost:4200`

Repo defaults assume those ports unless you override them.

## Local Prerequisites

- Java 17
- Maven Wrapper
- Node.js and npm
- MySQL 8+
- Keycloak
- PowerShell
- Optional for face biometric login:
  - Python
  - `opencv-python`
  - a working `py` or `python` command
- Optional for avatar uploads:
  - a local public folder or web server to serve uploaded images
- Optional for SMTP:
  - a working SMTP account or Gmail app password

## Important Config Files

- User service config:
  - `src/main/resources/application.properties`
- API Gateway config:
  - `p-r-k/ApiGateway/ApiGateway/src/main/resources/application.properties`
- Angular API and Keycloak config:
  - `angular/angular-app/src/environments/environment.ts`
  - `angular/angular-app/src/environments/environment.prod.ts`

## Step 1: MySQL

The user service defaults to:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/ybrainy_users?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=
```

That means:

- MySQL only needs to be running locally
- the database `ybrainy_users` will be created automatically if it does not exist
- if your MySQL username or password is different, update `src/main/resources/application.properties`

## Step 2: Keycloak Setup

The app expects:

- Keycloak base URL: `http://localhost:9190`
- Realm: `microservices`
- Frontend auth client ID: `angular-client`
- Backend admin client ID: `bb-user-admin`

### Create the realm

Create a realm named:

```text
microservices
```

### Create realm roles

Create these realm roles:

- `ADMIN`
- `INSTRUCTOR`
- `STUDENT`
- `ENTERPRISE_USER`

### Create the frontend client

Create a client named:

```text
angular-client
```

Recommended local settings for `angular-client`:

- Client type: `Public`
- Standard Flow: `Enabled`
- Direct Access Grants: `Enabled`
- PKCE: `S256`
- Root URL: `http://localhost:4200`
- Home URL: `http://localhost:4200`
- Valid Redirect URIs:
  - `http://localhost:4200/*`
- Valid Post Logout Redirect URIs:
  - `http://localhost:4200/*`
- Web Origins:
  - `http://localhost:4200`

Why those settings matter:

- the Angular app uses Keycloak browser login with PKCE
- the backend also uses password login and refresh token flows against the same auth client
- the resource servers validate tokens against the configured client id, so the client id must match

If you rename the auth client, update all of these:

- `KEYCLOAK_AUTH_CLIENT_ID`
- `src/main/resources/application.properties`
- `p-r-k/ApiGateway/ApiGateway/src/main/resources/application.properties`
- `angular/angular-app/src/environments/environment.ts`
- `angular/angular-app/src/environments/environment.prod.ts`

### Create the backend admin client

Create a client named:

```text
bb-user-admin
```

Recommended local settings for `bb-user-admin`:

- Client type: `Confidential`
- Service Accounts: `Enabled`
- Standard Flow: `Disabled`
- Direct Access Grants: `Disabled`
- Copy the generated client secret

Set the secret in your shell before starting the app:

```powershell
$env:KEYCLOAK_ADMIN_CLIENT_SECRET='your-bb-user-admin-secret'
```

### Give the admin service account permission to manage users

For the service account user of `bb-user-admin`, assign the needed `realm-management` roles. At minimum, this project needs enough access to:

- create users
- update users
- delete users
- set passwords
- read users
- read realm roles
- impersonate users for face login

In practice, grant these `realm-management` roles:

- `manage-users`
- `view-users`
- `query-users`
- `view-realm`
- `impersonation`

### Admin fallback

The backend can fall back to Keycloak admin username/password if the admin service client is not configured, but the preferred setup is the `bb-user-admin` confidential client.

Optional fallback env vars:

```powershell
$env:KEYCLOAK_ADMIN_USERNAME='admin'
$env:KEYCLOAK_ADMIN_PASSWORD='admin'
$env:KEYCLOAK_ADMIN_REALM='master'
$env:KEYCLOAK_ADMIN_FALLBACK_CLIENT_ID='admin-cli'
```

## Step 3: Angular Local Config

The Angular app now reads local API and Keycloak settings from:

- `angular/angular-app/src/environments/environment.ts`
- `angular/angular-app/src/environments/environment.prod.ts`

Default local values:

```typescript
apiBaseUrl: 'http://localhost:8088',
keycloakUrl: 'http://localhost:9190',
keycloakRealm: 'microservices',
keycloakClientId: 'angular-client',
googleIdpHint: 'google',
```

If your local API Gateway URL, Keycloak URL, realm, or client differs, change them there before running the Angular app.

### Angular files teammates usually need

These are the main frontend files to check when local auth or API calls do not match a teammate's machine:

- `angular/angular-app/src/environments/environment.ts`
  - local Angular config used by `ng serve`
  - set `apiBaseUrl`, `keycloakUrl`, `keycloakRealm`, `keycloakClientId`, and `googleIdpHint`
- `angular/angular-app/src/environments/environment.prod.ts`
  - production Angular config used by production builds
  - keep this aligned with the deployed API and Keycloak setup
- `angular/angular-app/src/app/auth/keycloak.service.ts`
  - initializes the browser Keycloak client
  - reads the values from the Angular environment files
  - also sends password login, refresh-token, and face-login requests to `${environment.apiBaseUrl}`
- `angular/angular-app/src/app/signup/signup.component.ts`
  - frontend signup flow
  - posts to `${environment.apiBaseUrl}/api/auth`
  - uploads avatars through `${environment.apiBaseUrl}/api/users/uploads/avatar`
- `angular/angular-app/src/app/login/login-page.component.ts`
  - frontend login and ban-appeal flow
  - uses the same API base URL from the environment config
- `angular/angular-app/src/app/frontoffice/services/user.service.ts`
  - main frontend user/profile API service
  - uses `${environment.apiBaseUrl}/api/users`

Practical rule for teammates:

- if Angular is calling the wrong backend, update `environment.ts`
- if Keycloak browser login opens the wrong realm or client, update `environment.ts`
- if a production build points to the wrong place, update `environment.prod.ts`
- if someone wants to understand how Angular login actually works, read `src/app/auth/keycloak.service.ts`

## Step 4: API Gateway And User Service Config

The gateway and user service both validate JWTs against the same Keycloak realm and auth client.

Current defaults:

- issuer URI: `http://localhost:9190/realms/microservices`
- auth client id: `angular-client`

If you change Keycloak realm or client id, update:

- `src/main/resources/application.properties`
- `p-r-k/ApiGateway/ApiGateway/src/main/resources/application.properties`
- Angular environment files

## Step 5: Optional Google Login

The Angular app uses `google` as the default Keycloak IdP hint.

If you want Google login locally:

1. Create Google OAuth credentials.
2. In Google Cloud, add this redirect URI:

```text
http://localhost:9190/realms/microservices/broker/google/endpoint
```

3. Set these env vars:

```powershell
$env:KEYCLOAK_GOOGLE_CLIENT_ID='your-google-client-id'
$env:KEYCLOAK_GOOGLE_CLIENT_SECRET='your-google-client-secret'
```

4. Run:

```powershell
.\scripts\configure-keycloak-google-idp.ps1
```

That script will create or update the `google` identity provider inside the `microservices` realm.

## Step 6: Optional SMTP

Forgot-password can use SMTP, but local development does not require it.

If you do not want SMTP locally:

```powershell
$env:APP_MAIL_ENABLED='false'
```

If you do want SMTP:

```powershell
$env:APP_SMTP_USERNAME='your-email@example.com'
$env:APP_SMTP_APP_PASSWORD='your-app-password'
```

The backend already supports a dev fallback response code if SMTP fails.

## Step 7: Optional Avatar Uploads

Avatar uploads default to:

```text
C:/xampp/htdocs/images
```

And public URLs default to:

```text
http://localhost/images
```

If you are not using XAMPP, set your own values:

```powershell
$env:APP_UPLOAD_IMAGES_DIR='C:/some/public/folder/images'
$env:APP_UPLOAD_IMAGES_PUBLIC_BASE_URL='http://localhost/images'
```

The backend only saves files there. You are responsible for making that folder publicly reachable.

## Step 8: Optional Face Biometric Login

Face biometric login uses:

- `scripts/face_biometric.py`
- OpenCV (`cv2`)
- Haar cascade file in `src/main/resources/biometrics/haarcascade_frontalface_alt.xml`

Install OpenCV locally:

```powershell
py -m pip install opencv-python
```

If your Python command is not `py`, override it:

```powershell
$env:APP_FACE_BIOMETRIC_PYTHON_COMMAND='python'
```

Optional overrides:

```powershell
$env:APP_FACE_BIOMETRIC_SCRIPT_PATH='scripts/face_biometric.py'
$env:APP_FACE_BIOMETRIC_CASCADE_PATH='src/main/resources/biometrics/haarcascade_frontalface_alt.xml'
$env:APP_FACE_BIOMETRIC_STORAGE_DIR='tmp/face-biometric'
```

If Python or OpenCV is missing, email/password signup and login still work, but face-biometric endpoints will fail.

## Step 9: Suggested Local Env Block

Example PowerShell session setup:

```powershell
$env:KEYCLOAK_ADMIN_CLIENT_SECRET='your-bb-user-admin-secret'
$env:KEYCLOAK_AUTH_CLIENT_ID='angular-client'
$env:KEYCLOAK_AUTH_CLIENT_SECRET=''
$env:APP_MAIL_ENABLED='false'
$env:APP_UPLOAD_IMAGES_DIR='C:/xampp/htdocs/images'
$env:APP_UPLOAD_IMAGES_PUBLIC_BASE_URL='http://localhost/images'
$env:APP_FACE_BIOMETRIC_PYTHON_COMMAND='py'
```

## Step 10: Start Everything

Start MySQL and Keycloak first.

Then from the repo root:

```powershell
.\run-everything.ps1
```

That script starts:

- Eureka
- user service
- API Gateway
- Angular app

## Step 11: Verify The Environment

Open these URLs:

- Eureka:
  - `http://localhost:8071`
- User service health:
  - `http://localhost:8899/actuator/health`
- API Gateway:
  - `http://localhost:8088`
- Angular app:
  - `http://localhost:4200`
- Keycloak realm:
  - `http://localhost:9190/realms/microservices`

Run automated verification:

```powershell
.\run-all-tests.ps1
```

If the local stack is up and configured, run the smoke script too:

```powershell
.\run-all-curls.ps1
```

## Signup Flow Used By This Project

Signup is challenge-based now.

### 1. Create a signup challenge

```http
POST /api/auth/signup/challenge
```

Example body:

```json
{
  "age": 25,
  "country": "Tunisia",
  "preferredMode": "jigsaw"
}
```

### 2. Solve the returned challenge

The final signup request must include:

- `challengeToken`
- `challengeMode`
- `challengeAnswer`

For `flag_jigsaw`, the answer is the pipe-delimited list of piece ids ordered by `correctIndex`.

Example:

```text
piece-1|piece-2|piece-3|piece-4|piece-5|piece-6
```

### 3. Submit signup

```http
POST /api/auth/signup
```

Example body:

```json
{
  "username": "student_01",
  "firstName": "Student",
  "lastName": "Example",
  "email": "student_01@example.com",
  "password": "Password123",
  "confirmPassword": "Password123",
  "role": "STUDENT",
  "age": 25,
  "country": "Tunisia",
  "city": "Tunis",
  "challengeToken": "token-from-step-1",
  "challengeMode": "jigsaw",
  "challengeAnswer": "piece-1|piece-2|piece-3|piece-4|piece-5|piece-6"
}
```

## Password Rules

Signup and password changes require:

- at least 8 characters
- at least one uppercase letter
- at least one lowercase letter
- at least one digit

## Roles

Preferred role values:

- `ADMIN`
- `INSTRUCTOR`
- `STUDENT`
- `ENTERPRISE_USER`

Legacy `USER` is still accepted and mapped to `STUDENT`, but new integrations should send `STUDENT`.

## Common Setup Problems

- `401` or `invalid_client` on login:
  - check the `angular-client` client id and whether it is public or confidential
- `Token was not issued for the configured Keycloak client`:
  - the frontend, gateway, and user service are not using the same auth client id
- signup works but browser login redirect fails:
  - redirect URIs or web origins are wrong in Keycloak
- forgot-password fails:
  - SMTP is not configured and local fallback is disabled
- avatar upload succeeds but images do not load:
  - the upload folder exists, but it is not actually served publicly
- face login fails immediately:
  - Python/OpenCV is missing or `APP_FACE_BIOMETRIC_PYTHON_COMMAND` is wrong
