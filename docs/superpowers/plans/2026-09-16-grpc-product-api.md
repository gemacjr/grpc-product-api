# gRPC Product API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a Spring Boot 4.1 gRPC Product CRUD API backed by PostgreSQL with Docker Compose and grpcurl docs.

**Architecture:** gRPC service → domain service → Spring Data JPA → PostgreSQL. Proto defines the contract; Hibernate manages schema locally.

**Tech Stack:** Java 21, Spring Boot 4.1.1, Gradle, spring-boot-starter-grpc-server, Spring Data JPA, PostgreSQL, Testcontainers, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-09-16-grpc-product-api-design.md`

## Global Constraints

- Java 21; Spring Boot 4.1.1
- gRPC port 9090; Actuator on 8080
- Price as proto `string` → Java `BigDecimal`
- Timestamps: `google.protobuf.Timestamp`
- Errors: INVALID_ARGUMENT / NOT_FOUND / INTERNAL
- Env overrides: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD

### Task 1: Project scaffold + proto + persistence + gRPC CRUD

**Files:** Gradle wrapper/build, `product.proto`, entity/repo/service/grpc, `application.yml`, `docker-compose.yml`, README, unit + integration tests.

Execute inline in this session (user requested code immediately).
