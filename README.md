# ERP Microservices

Java Spring Boot ERP backend split into auth, inventory, item, task, and notification services.

## Highlights

- Service split across inventory, item/document/order, task, notification, and auth responsibilities
- Security and resource-server setup across multiple services
- Business flows for inventory, BOMs, items, orders, documents, and task handling

## Tech Stack

- Java 17, Spring Boot 3, Spring Security, OAuth2 resource server, JPA, PostgreSQL, RabbitMQ, MinIO, JUnit

## Repository Layout

- `user-service`
- `inventory-service`
- `item-service`
- `task-service`
- `notification-service`

## Getting Started

- Open each service as a standard Spring Boot project and configure the required backing services.
- Build a service with `./mvnw test` or `mvn test`, then start it with the Spring Boot plugin or your IDE.
- Treat the repository as a multi-service sandbox rather than a single deployment package.

## Current Status

- This README was refreshed from a code audit and is intentionally scoped to what is directly visible in the repository.
