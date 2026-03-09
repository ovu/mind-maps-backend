## 1. Database Schema

- [ ] 1.1 Add nullable `name VARCHAR(100)` column to the `users` table DDL in Main.scala

## 2. Domain Model

- [ ] 2.1 Add `name: Option[String]` field to the `User` case class
- [ ] 2.2 Add `name: Option[String]` field to `RegisterRequest` (with JSON codec)

## 3. Repository

- [ ] 3.1 Update `UserRepository.insert` to accept and store the name
- [ ] 3.2 Update `UserRepository.findByEmail` to read the name column

## 4. Service Layer

- [ ] 4.1 Update `AuthService.register` to pass the name through (trimmed) to the repository

## 5. HTTP Layer

- [ ] 5.1 Update the `POST /auth/register` handler to pass name from the request
- [ ] 5.2 Update the `GET /api/me` handler to return name alongside userId (fetch user from DB by id)
- [ ] 5.3 Add `UserRepository.findById` method if not already present (needed for /me)

## 6. API Documentation

- [ ] 6.1 Update openapi.yaml to add optional `name` field to RegisterRequest schema
- [ ] 6.2 Update openapi.yaml to add `name` field to the /me response schema

## 7. Tests

- [ ] 7.1 Update registration tests to cover name field (with name, without name, trimming)
- [ ] 7.2 Update /me tests to verify name is returned
- [ ] 7.3 Run all tests and verify they pass
