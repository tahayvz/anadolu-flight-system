# Anadolu Air Flight System

[![CI](https://github.com/tahayvz/anadolu-flight-system/actions/workflows/ci.yml/badge.svg)](https://github.com/tahayvz/anadolu-flight-system/actions/workflows/ci.yml)
[![Java](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A microservices flight booking platform: reservations, flight operations, external feed
integration and an API gateway, wired together with Kafka, a Saga orchestrator and Redis.

> **Anadolu Air is fictional.** The airline, its flights and its data are invented for
> this project. Nothing here is affiliated with any real carrier.

The interesting part is not that it books flights — it is what happens when a booking
touches four services and one of them fails. That is what the Saga orchestrator, the
transactional outbox and the dead-letter queue exist for.

## Status

Built as a personal project, and honest about where it stands: the services run, the
booking rules are covered by tests, and the whole stack comes up with Docker Compose.
It has not run in production and is not a finished product.

| | |
| --- | --- |
| Services | api-gateway · booking · flight-ops · integration · shared-events |
| Tests | 40 — see [Testing](#testing) |
| Runs on | Docker Compose · Helm charts included for Kubernetes |

---

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Technology Stack](#technology-stack)
4. [Enterprise Patterns](#enterprise-patterns)
5. [Project Structure](#project-structure)
6. [Getting Started](#getting-started)
7. [Running the Application](#running-the-application)
8. [Kubernetes Deployment](#kubernetes-deployment)
9. [API Documentation](#api-documentation)
10. [Business Rules](#business-rules)
11. [Testing](#testing)
12. [Monitoring & Observability](#monitoring--observability)
13. [Debugging](#debugging)
14. [Docker Commands Reference](#docker-commands-reference)
15. [Troubleshooting](#troubleshooting)
16. [Architecture Decision Records](#architecture-decision-records)
17. [Production Readiness Checklist](#production-readiness-checklist)
18. [User Simulation](#user-simulation-load-testing)

---

## Overview

### What is this project?

A **production-ready microservices system** implementing all enterprise patterns required for a real airline booking system:

- **API Gateway** - Central entry point with rate limiting, authentication, and circuit breaker
- **Event-Driven Architecture** - Apache Kafka as the event backbone with guaranteed delivery
- **Fault Tolerance** - Circuit breaker, retry, bulkhead, and rate limiter patterns
- **Distributed Transactions** - Saga pattern with compensating transactions
- **Reliable Messaging** - Outbox pattern for at-least-once delivery guarantee
- **Distributed Tracing** - End-to-end request tracing with Zipkin
- **Caching** - Redis for high-performance data access
- **Real-Time Updates** - WebSocket for live flight status
- **Legacy Integration** - SOAP/XML endpoints for enterprise systems

### Why this architecture?

This architecture addresses real-world challenges in airline systems:

| Challenge | Solution | Implementation |
|-----------|----------|----------------|
| High availability | Circuit breaker + fallbacks | Resilience4j |
| Data consistency | Saga + Outbox pattern | Orchestration-based |
| Peak load handling | Rate limiting + caching | Redis + Gateway |
| Legacy integration | Protocol translation | SOAP/REST adapters |
| Debugging distributed systems | Distributed tracing | Zipkin + B3 headers |
| Message delivery guarantee | Outbox + DLQ | Transactional outbox |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              EXTERNAL CLIENTS                                    │
│                                                                                 │
│   ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐            │
│   │  Legacy Systems │    │   Web/Mobile    │    │   Partner APIs  │            │
│   │   (SOAP/XML)    │    │   Applications  │    │   (REST/JSON)   │            │
│   └────────┬────────┘    └────────┬────────┘    └────────┬────────┘            │
└────────────┼──────────────────────┼──────────────────────┼──────────────────────┘
             │                      │                      │
             │ SOAP/HTTP            │ HTTPS                │ HTTPS + API Key
             ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           API GATEWAY (Port: 8080)                              │
│                        Spring Cloud Gateway + Resilience4j                       │
│                                                                                 │
│   ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐          │
│   │    Rate     │  │   Circuit   │  │   Request   │  │   Auth      │          │
│   │   Limiter   │  │   Breaker   │  │   Logging   │  │   Filter    │          │
│   │ (Redis)     │  │ (Resilience)│  │ (Trace ID)  │  │ (JWT/API)   │          │
│   └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘          │
│                                                                                 │
│   Routes: /api/bookings/** → booking-service                                   │
│           /api/flights/**  → flight-ops-service                                │
│           /ws/**           → integration-service                               │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
              ┌─────────────────────────┼─────────────────────────┐
              │                         │                         │
              ▼                         ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           MICROSERVICES LAYER                                   │
│                                                                                 │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────────┐  │
│  │  integration-service │  │  flight-ops-service  │  │   booking-service    │  │
│  │      (Port: 8081)    │  │     (Port: 8082)     │  │     (Port: 8083)     │  │
│  │                      │  │                      │  │                      │  │
│  │ • SOAP Endpoints     │  │ • WebSocket/STOMP    │  │ • REST API           │  │
│  │ • XML/XSD Validation │  │ • Kafka Consumer     │  │ • Saga Orchestrator  │  │
│  │ • Kafka Producer     │  │ • Flight Scheduling  │  │ • Outbox Pattern     │  │
│  │ • Protocol Transform │  │ • Real-time Updates  │  │ • Circuit Breaker    │  │
│  │                      │  │                      │  │ • Redis Caching      │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └──────────┬───────────┘  │
│             │                         │                         │              │
│             │                    ┌────┴────┐                    │              │
│             │                    │  Redis  │◄───────────────────┤              │
│             │                    │ (Cache) │                    │              │
│             │                    └─────────┘                    │              │
└─────────────┼─────────────────────────┼─────────────────────────┼──────────────┘
              │                         │                         │
              ▼                         ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        KAFKA (Event Backbone)                                   │
│                                                                                 │
│   Topics:                                                                       │
│   ┌───────────────────┐  ┌───────────────────┐  ┌───────────────────┐          │
│   │  booking-events   │  │   flight-events   │  │  notification-    │          │
│   │  (CRUD + Status)  │  │  (Status Updates) │  │     events        │          │
│   └───────────────────┘  └───────────────────┘  └───────────────────┘          │
│                                                                                 │
│   Dead Letter Queues:                                                           │
│   ┌───────────────────┐  ┌───────────────────┐                                 │
│   │ booking-events.DLT│  │ flight-events.DLT │  ← Failed message handling      │
│   └───────────────────┘  └───────────────────┘                                 │
│                                                                                 │
│   Port: 9092 (external) / 29092 (internal)                                     │
└─────────────────────────────────────────────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         OBSERVABILITY STACK                                     │
│                                                                                 │
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │
│  │   Prometheus  │  │    Grafana    │  │    Zipkin     │  │   Kafka UI    │   │
│  │   (Metrics)   │  │  (Dashboard)  │  │   (Tracing)   │  │   (Events)    │   │
│  │  Port: 9090   │  │  Port: 3001   │  │  Port: 9411   │  │  Port: 8090   │   │
│  └───────────────┘  └───────────────┘  └───────────────┘  └───────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Request Flow Examples

**1. Booking Creation with Saga Pattern:**
```
Client → API Gateway → booking-service
                            │
                            ├─► Validate Flight (Flight Ops)
                            │        ↓ (Circuit Breaker)
                            ├─► Reserve Seats
                            │        ↓
                            ├─► Calculate Price
                            │        ↓
                            ├─► Process Payment
                            │        ↓
                            ├─► Create Booking (DB + Outbox)
                            │        ↓
                            └─► Send Confirmation
                                     │
                          Outbox Processor (1s poll)
                                     │
                                     ▼
                                   Kafka
                                     │
                        ┌────────────┴────────────┐
                        ▼                         ▼
              flight-ops-service          notification-service
                   (update seats)              (send email)
```

**2. Failure Recovery with Compensating Transactions:**
```
Payment Failed → Saga Orchestrator
                        │
                        ├─► Release Reserved Seats (Compensation)
                        ├─► Refund Price Calculation
                        ├─► Update Booking Status to FAILED
                        └─► Notify Customer of Failure
```

---

## Technology Stack

| Category | Technology | Version | Purpose |
|----------|------------|---------|---------|
| **Language** | Java | 17 | Main programming language |
| **Framework** | Spring Boot | 3.2.1 | Application framework |
| **API Gateway** | Spring Cloud Gateway | 4.1.0 | Routing, rate limiting, security |
| **Fault Tolerance** | Resilience4j | 2.2.0 | Circuit breaker, retry, bulkhead |
| **Messaging** | Apache Kafka | 7.5.0 | Event streaming platform |
| **Caching** | Redis | 7.x | Distributed caching |
| **Tracing** | Zipkin + Micrometer | Latest | Distributed tracing |
| **SOAP** | Spring Web Services | 3.2.1 | Legacy integration |
| **WebSocket** | Spring WebSocket + STOMP | 3.2.1 | Real-time communication |
| **Database** | H2 (dev) / PostgreSQL (prod) | Latest | Data persistence |
| **ORM** | Spring Data JPA + Hibernate | 3.2.1 | Database access |
| **API Docs** | SpringDoc OpenAPI | 2.3.0 | Swagger UI |
| **Monitoring** | Prometheus + Grafana | Latest | Metrics & visualization |
| **Container** | Docker + Docker Compose | Latest | Containerization |

---

## Enterprise Patterns

### 1. Circuit Breaker Pattern (Resilience4j)

Prevents cascade failures when downstream services are unavailable.

```java
@CircuitBreaker(name = "flightOps", fallbackMethod = "getFlightFallback")
@Retry(name = "flightOps")
@Bulkhead(name = "flightOps")
@RateLimiter(name = "flightOps")
public FlightBookingInfo getFlightBookingInfo(String flightNumber) {
    return webClient.get()
        .uri("/api/flights/{flightNumber}/booking-info", flightNumber)
        .retrieve()
        .bodyToMono(FlightBookingInfo.class)
        .block();
}
```

**Configuration:**

| Setting | Value | Purpose |
|---------|-------|---------|
| Failure Rate Threshold | 50% | Open circuit after 50% failures |
| Wait Duration | 10 seconds | Time before attempting recovery |
| Permitted Calls in Half-Open | 3 | Test calls before fully closing |
| Sliding Window Size | 10 | Number of calls to evaluate |

### 2. Saga Pattern (Orchestration-Based with Kafka + Redis)

Manages distributed transactions with compensating actions. State is persisted in Redis, steps are coordinated via Kafka.

**Architecture:**
```
┌────────────────────────────────────────────────────────────────────────────┐
│                         BOOKING REQUEST                                     │
└─────────────────────────────────┬──────────────────────────────────────────┘
                                  │
                                  ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      SAGA ORCHESTRATOR                                      │
│  1. Create saga state in Redis (saga:{sagaId})                             │
│  2. Send command to Kafka (anadolu.saga.commands)                              │
│  3. Wait for reply (anadolu.saga.replies)                                      │
│  4. On SUCCESS → advance to next step                                      │
│  5. On FAILURE (after 3 retries) → start compensation                      │
└─────────────────────────────────┬──────────────────────────────────────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
│ VALIDATE_FLIGHT │   │ RESERVE_SEATS   │   │ PROCESS_PAYMENT │
│    (Kafka)      │──▶│    (Kafka)      │──▶│    (Kafka)      │
└─────────────────┘   └─────────────────┘   └─────────────────┘
         │                    │                      │
         │ SUCCESS            │ SUCCESS              │ FAILURE
         ▼                    ▼                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      COMPENSATION (ROLLBACK)                                │
│  1. REFUND_PAYMENT_CMD → Payment refund                                    │
│  2. RELEASE_SEATS_CMD → Release reserved seats                             │
│  3. CANCEL_BOOKING_CMD → Cancel booking record                             │
│  4. State = ROLLED_BACK                                                    │
└────────────────────────────────────────────────────────────────────────────┘
```

**Saga Steps:**

| Step | Command | Compensation | Service |
|------|---------|--------------|---------|
| 1 | VALIDATE_FLIGHT_CMD | - | flight-ops |
| 2 | RESERVE_SEATS_CMD | RELEASE_SEATS_CMD | seat-service |
| 3 | VALIDATE_PASSENGERS_CMD | - | booking |
| 4 | CALCULATE_PRICE_CMD | - | pricing |
| 5 | PROCESS_PAYMENT_CMD | REFUND_PAYMENT_CMD | payment |
| 6 | CREATE_BOOKING_CMD | CANCEL_BOOKING_CMD | booking |
| 7 | SEND_CONFIRMATION_CMD | - | notification |

**Kafka Topics:**

| Topic                  | Purpose | Retention |
|------------------------|---------|-----------|
| `anadolu.saga.commands`    | Saga step commands | 24 hours |
| `anadolu.saga.replies`     | Step success/failure responses | 24 hours |
| `anadolu.saga.compensation` | Rollback commands | 7 days |

**Redis Key Structure:**
```
saga:{sagaId}              → Saga state JSON
saga:pnr:{PNR}             → sagaId (lookup by PNR)
saga:flight:{TK123}:{date} → Set of sagaIds
saga:active                → Active saga IDs
saga:failed                → Failed saga IDs
saga:compensating          → Compensating saga IDs
```

**Recovery Mechanisms:**
- **Timeout Recovery**: Every 1 min, sagas stuck for >5 min → compensation
- **Stuck Compensation Recovery**: Every 5 min, retry stuck compensations
- **Max Retry**: Each step retries 3 times before compensation

**Monitoring API:**
```bash
# Saga durumu
GET /api/sagas/{sagaId}
GET /api/sagas/by-pnr/{pnr}

# Listeler
GET /api/sagas/active
GET /api/sagas/compensating
GET /api/sagas/timed-out

# İstatistikler
GET /api/sagas/stats
GET /api/sagas/health

# Manuel müdahale
POST /api/sagas/{sagaId}/compensate  # Trigger rollback
POST /api/sagas/{sagaId}/retry       # Retry current step
```

### 3. Outbox Pattern (Reliable Messaging)

Guarantees message delivery with transactional consistency.

```
┌─────────────────────────────────────────────────────────────────┐
│                      BOOKING SERVICE                            │
│                                                                 │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐       │
│  │   Booking   │     │   Outbox    │     │   Outbox    │       │
│  │   Service   │────►│   Table     │◄────│  Processor  │       │
│  │             │     │  (Events)   │     │  (1s poll)  │       │
│  └─────────────┘     └─────────────┘     └──────┬──────┘       │
│         │                                       │               │
│         │            Same Transaction           │               │
│         ▼                                       ▼               │
│  ┌─────────────┐                         ┌─────────────┐       │
│  │  Bookings   │                         │    Kafka    │       │
│  │   Table     │                         │  Producer   │       │
│  └─────────────┘                         └─────────────┘       │
└─────────────────────────────────────────────────────────────────┘
```

**Event States:**
- `PENDING` → Waiting to be processed
- `PROCESSING` → Currently being sent to Kafka
- `COMPLETED` → Successfully delivered
- `FAILED` → Delivery failed (will retry)

### 4. Kafka Production Configuration

Kafka broker ID çakışması ve Zookeeper state sorunlarını önlemek için production-grade ayarlar:

**Problem: NodeExistsException**
```
ERROR: Broker ID 1 is already registered in Zookeeper at /brokers/ids/1
```

**Çözümler:**

| Ayar | Değer | Açıklama |
|------|-------|----------|
| `KAFKA_BROKER_ID` | `-1` | Otomatik unique ID üretir |
| `KAFKA_BROKER_ID_GENERATION_ENABLE` | `true` | Auto-generation aktif |
| `KAFKA_CONTROLLED_SHUTDOWN_ENABLE` | `true` | Graceful shutdown - ZK kaydını temizler |
| `KAFKA_ZOOKEEPER_SESSION_TIMEOUT_MS` | `18000` | Session timeout (eski kayıt temizleme) |
| `ZOOKEEPER_AUTOPURGE_PURGE_INTERVAL` | `1` | Eski snapshot'ları otomatik sil |

**Temiz Başlangıç (Sorun devam ederse):**
```bash
# Tüm volume'ları temizle ve yeniden başlat
docker compose down -v && docker compose up -d
```

### 5. Dead Letter Queue (DLQ)

Handles failed messages for analysis and reprocessing.

```java
@KafkaListener(topics = "booking-events.DLT", groupId = "dlq-group")
public void handleFailedBookingEvent(ConsumerRecord<String, String> record) {
    log.error("DLQ received failed event: {}", record.key());

    // Store for analysis
    storeFailedEvent(record);

    // Alert if threshold exceeded
    checkAndAlert("booking-events");
}

// Manual reprocessing
public void republishMessage(String topic, String key, String value) {
    kafkaTemplate.send(topic, key, value);
}
```

### 5. API Gateway Features

| Feature | Implementation | Purpose |
|---------|---------------|---------|
| Rate Limiting | Redis + RequestRateLimiter | Protect against DDoS |
| Circuit Breaker | Resilience4j | Prevent cascade failures |
| Request Logging | Custom Filter + Trace ID | Debugging & audit |
| Authentication | JWT + API Key | Security |
| Fallback | FallbackController | Graceful degradation |

### 6. Caching Strategy

| Cache | TTL | Purpose |
|-------|-----|---------|
| `bookings` | 5 min | Recent booking lookups |
| `flights` | 15 min | Flight information (relatively stable) |
| `seatMaps` | 30 sec | Real-time seat availability |
| `statistics` | 1 min | Dashboard metrics |

---

## Project Structure

```
anadolu-flight-system/
│
├── pom.xml                              # Parent POM
├── docker-compose.yml                   # Full stack deployment
├── README.md                            # This documentation
│
├── api-gateway/                         # ★ API Gateway (NEW)
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/java/com/anadoluair/flight/gateway/
│       ├── ApiGatewayApplication.java
│       ├── config/
│       │   ├── GatewayConfig.java       # Rate limiting key resolvers
│       │   └── RouteConfig.java         # Programmatic routes
│       ├── controller/
│       │   ├── FallbackController.java  # Graceful degradation
│       │   └── GatewayHealthController.java
│       └── filter/
│           ├── RequestLoggingFilter.java
│           └── AuthenticationFilter.java
│
├── shared-events/                       # Shared Kafka Event DTOs
│   └── src/main/java/com/anadoluair/flight/events/
│       ├── FlightEvent.java
│       ├── BookingEvent.java
│       └── KafkaTopics.java
│
├── integration-service/                 # SOAP/XML Edge Layer
│   └── src/main/java/com/anadoluair/flight/integrationservice/
│       ├── endpoint/FlightEndpoint.java
│       ├── service/FlightService.java
│       └── kafka/FlightEventProducer.java
│
├── flight-ops-service/                  # Real-Time Operations
│   └── src/main/java/com/anadoluair/flight/flightopsservice/
│       ├── controller/FlightController.java
│       ├── websocket/FlightWebSocketHandler.java
│       ├── kafka/FlightEventConsumer.java
│       └── scheduler/FlightStatusScheduler.java
│
├── booking-service/                     # REST API + Enterprise Patterns
│   └── src/main/java/com/anadoluair/flight/bookingservice/
│       ├── config/
│       │   ├── RedisCacheConfig.java    # ★ Caching configuration
│       │   ├── TracingConfig.java       # ★ Distributed tracing
│       │   ├── BookingRulesConfig.java  # ★ Business rules
│       │   └── KafkaProducerConfig.java
│       ├── controller/
│       │   └── BookingController.java
│       ├── service/
│       │   └── BookingService.java
│       ├── client/
│       │   └── FlightOpsClient.java     # ★ Circuit breaker enabled
│       ├── saga/                        # ★ Saga Pattern (NEW)
│       │   ├── BookingSagaOrchestrator.java
│       │   └── BookingSagaState.java
│       ├── outbox/                      # ★ Outbox Pattern (NEW)
│       │   ├── OutboxEvent.java
│       │   ├── OutboxRepository.java
│       │   ├── OutboxService.java
│       │   └── OutboxProcessor.java
│       ├── kafka/
│       │   ├── BookingEventProducer.java
│       │   └── DeadLetterQueueHandler.java  # ★ DLQ (NEW)
│       └── exception/
│           ├── GlobalExceptionHandler.java
│           ├── BookingNotFoundException.java
│           ├── CheckInNotAllowedException.java  # ★ Business rule
│           └── BookingValidationException.java  # ★ Validation
│
└── monitoring/
    ├── prometheus.yml                   # Scrape configuration
    ├── prometheus-alerts.yml            # Alert rules
    └── grafana/
        ├── provisioning/
        └── dashboards/
```

---

## Getting Started

### Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| Java | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Docker | 20+ | `docker --version` |
| Docker Compose | 2.0+ | `docker compose version` |

### Installation

```bash
# 1. Clone the repository
git clone <repository-url>
cd anadolu-flight-system

# 2. Build the project
./mvnw clean install -DskipTests

# 3. Verify build success
ls -la */target/*.jar
```

Expected output:
```
api-gateway-1.0.0-SNAPSHOT.jar          (~35 MB)
booking-service-1.0.0-SNAPSHOT.jar      (~80 MB)
flight-ops-service-1.0.0-SNAPSHOT.jar   (~46 MB)
integration-service-1.0.0-SNAPSHOT.jar  (~43 MB)
shared-events-1.0.0-SNAPSHOT.jar        (~24 KB)
```

---

## Running the Application

### Option 1: Full Docker Mode (Recommended)

```bash
# Build and start everything
docker compose up -d --build

# Check all containers are running
docker compose ps

# View logs
docker compose logs -f api-gateway booking-service flight-ops-service
```

### Option 2: Development Mode

**Step 1: Start Infrastructure**
```bash
# Start all infrastructure services
docker compose up -d zookeeper kafka redis zipkin kafka-ui prometheus grafana

# Wait for services to be ready
docker compose logs -f kafka | grep -m1 "started"
```

**Step 2: Start Services (4 terminals)**
```bash
# Terminal 1 - API Gateway
./mvnw spring-boot:run -pl api-gateway

# Terminal 2 - Integration Service
./mvnw spring-boot:run -pl integration-service

# Terminal 3 - Flight Ops Service
./mvnw spring-boot:run -pl flight-ops-service

# Terminal 4 - Booking Service
./mvnw spring-boot:run -pl booking-service
```

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| **API Gateway** | http://localhost:8080 | Main entry point |
| **Booking API (via Gateway)** | http://localhost:8080/api/bookings | REST endpoints |
| **Flight Status Board** | http://localhost:8082 | Real-time WebSocket UI |
| **Swagger UI** | http://localhost:8083/swagger-ui.html | API documentation |
| **WSDL** | http://localhost:8081/ws/flights.wsdl | SOAP contract |
| **H2 Console** | http://localhost:8083/h2-console | Database browser |
| **Zipkin** | http://localhost:9411 | Distributed tracing |
| **Kafka UI** | http://localhost:8090 | Kafka topics/messages |
| **Prometheus** | http://localhost:9090 | Metrics queries |
| **Grafana** | http://localhost:3001 | Dashboards (admin/admin) |

---

## Kubernetes Deployment

This project includes production-ready Kubernetes manifests with Helm charts for enterprise deployment.

### Prerequisites

| Tool | Version | Check Command |
|------|---------|---------------|
| kubectl | 1.28+ | `kubectl version` |
| Helm | 3.12+ | `helm version` |
| Skaffold (optional) | 2.x | `skaffold version` |

### Quick Start with Helm

```bash
# 1. Add required Helm repositories
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

# 2. Build Docker images
docker build -t anadolu-flight-system/api-gateway:1.0.0 ./api-gateway
docker build -t anadolu-flight-system/booking-service:1.0.0 ./booking-service
docker build -t anadolu-flight-system/flight-ops-service:1.0.0 ./flight-ops-service
docker build -t anadolu-flight-system/integration-service:1.0.0 ./integration-service

# 3. Install for development
helm install anadolu-flight-system ./k8s/helm/anadolu-flight-system \
  -f ./k8s/helm/anadolu-flight-system/values-dev.yaml \
  --namespace anadolu-flight-system \
  --create-namespace

# 4. Install for production
helm install anadolu-flight-system ./k8s/helm/anadolu-flight-system \
  -f ./k8s/helm/anadolu-flight-system/values-prod.yaml \
  --namespace anadolu-flight-system \
  --create-namespace
```

### Quick Start with Skaffold

```bash
# Development mode with hot reload
skaffold dev -p dev

# Production deployment
skaffold run -p prod
```

### Kubernetes Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              KUBERNETES CLUSTER                                  │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        INGRESS CONTROLLER (nginx)                        │   │
│  │                     api.anadolu-flight-system.com                            │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                          │
│  ┌───────────────────────────────────┴───────────────────────────────────────┐ │
│  │                    anadolu-flight-system namespace                             │ │
│  │                                                                            │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌────────────────┐               │ │
│  │  │  API Gateway   │  │    Booking     │  │   Flight Ops   │               │ │
│  │  │   (3 pods)     │  │    Service     │  │    Service     │               │ │
│  │  │                │  │   (5 pods)     │  │   (3 pods)     │               │ │
│  │  │  HPA: 3-15     │  │  HPA: 5-30     │  │  HPA: 3-12     │               │ │
│  │  │  PDB: min 2    │  │  PDB: min 3    │  │  PDB: min 2    │               │ │
│  │  └────────────────┘  └────────────────┘  └────────────────┘               │ │
│  │           │                  │                  │                          │ │
│  │  ┌────────┴──────────────────┴──────────────────┴────────────────────┐    │ │
│  │  │                                                                    │    │ │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │    │ │
│  │  │  │    Kafka    │  │    Redis    │  │  PostgreSQL │  │  Zipkin  │ │    │ │
│  │  │  │  (3 nodes)  │  │ (replicated)│  │   (HA)      │  │          │ │    │ │
│  │  │  └─────────────┘  └─────────────┘  └─────────────┘  └──────────┘ │    │ │
│  │  │                        Infrastructure                             │    │ │
│  │  └───────────────────────────────────────────────────────────────────┘    │ │
│  │                                                                            │ │
│  │  ┌───────────────────────────────────────────────────────────────────┐    │ │
│  │  │  Network Policies │ RBAC │ Pod Security │ Service Accounts        │    │ │
│  │  └───────────────────────────────────────────────────────────────────┘    │ │
│  └────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                         monitoring namespace                             │   │
│  │  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐                │   │
│  │  │  Prometheus   │  │    Grafana    │  │    Zipkin     │                │   │
│  │  │   + Alerts    │  │  Dashboards   │  │   Tracing     │                │   │
│  │  └───────────────┘  └───────────────┘  └───────────────┘                │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Helm Chart Structure

```
k8s/
├── helm/
│   └── anadolu-flight-system/
│       ├── Chart.yaml              # Chart metadata
│       ├── values.yaml             # Default values
│       ├── values-dev.yaml         # Development overrides
│       ├── values-prod.yaml        # Production overrides
│       └── templates/
│           ├── _helpers.tpl        # Template helpers
│           ├── namespace.yaml
│           ├── configmap.yaml
│           ├── api-gateway-deployment.yaml
│           ├── booking-service-deployment.yaml
│           ├── flight-ops-deployment.yaml
│           ├── integration-service-deployment.yaml
│           ├── hpa.yaml            # Horizontal Pod Autoscaler
│           ├── pdb.yaml            # Pod Disruption Budget
│           ├── ingress.yaml
│           ├── networkpolicy.yaml
│           ├── serviceaccount.yaml
│           └── servicemonitor.yaml
└── base/
    ├── namespace.yaml
    ├── configmap.yaml
    ├── secrets.yaml
    ├── rbac.yaml
    └── pdb.yaml
```

### Key Kubernetes Features

| Feature | Description | Configuration |
|---------|-------------|---------------|
| **HPA** | Auto-scaling based on CPU/Memory | Min 3, Max 30 pods for booking-service |
| **PDB** | High availability during updates | Min 2-3 pods always available |
| **Network Policy** | Zero-trust networking | Only allow required traffic |
| **Affinity** | Pod anti-affinity | Spread across nodes/zones |
| **Topology Spread** | Zone distribution | Even distribution across AZs |
| **Init Containers** | Dependency checks | Wait for Kafka/Redis before starting |
| **Resource Limits** | CPU/Memory bounds | Prevent resource exhaustion |
| **Security Context** | Non-root, read-only FS | Container hardening |

### Scaling Configuration

**Development:**
```yaml
# Single replica, no auto-scaling
replicaCount: 1
autoscaling:
  enabled: false
```

**Production:**
```yaml
# High availability with auto-scaling
replicaCount: 5
autoscaling:
  enabled: true
  minReplicas: 5
  maxReplicas: 30
  targetCPUUtilizationPercentage: 60
```

### Useful Commands

```bash
# Check deployment status
kubectl get pods -n anadolu-flight-system

# View HPA status
kubectl get hpa -n anadolu-flight-system

# Check pod logs
kubectl logs -f deployment/booking-service -n anadolu-flight-system

# Scale manually
kubectl scale deployment booking-service --replicas=10 -n anadolu-flight-system

# Port forward for local access
kubectl port-forward svc/api-gateway 8080:8080 -n anadolu-flight-system

# Upgrade deployment
helm upgrade anadolu-flight-system ./k8s/helm/anadolu-flight-system \
  -f ./k8s/helm/anadolu-flight-system/values-prod.yaml \
  --namespace anadolu-flight-system

# Rollback if needed
helm rollback anadolu-flight-system 1 -n anadolu-flight-system
```

---

## API Documentation

### Booking API (Through API Gateway)

#### Create Booking
```bash
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -H "X-API-Key: anadolu-api-key-2024" \
  -d '{
    "flightNumber": "TK1",
    "flightDate": "2026-02-15",
    "passengers": [
      {
        "firstName": "John",
        "lastName": "Doe",
        "dateOfBirth": "1990-05-15",
        "passengerType": "ADULT"
      }
    ],
    "contactEmail": "john@example.com",
    "contactPhone": "+905551234567"
  }'
```

#### Get Booking
```bash
curl http://localhost:8080/api/bookings/ABC123 \
  -H "X-API-Key: anadolu-api-key-2024"
```

#### Check-in (Must be within 24 hours of departure)
```bash
curl -X POST http://localhost:8080/api/bookings/ABC123/checkin \
  -H "X-API-Key: anadolu-api-key-2024"
```

#### Cancel Booking
```bash
curl -X DELETE http://localhost:8080/api/bookings/ABC123 \
  -H "X-API-Key: anadolu-api-key-2024"
```

### Direct Service Access (Development)

```bash
# Direct to booking-service (bypassing gateway)
curl http://localhost:8083/api/bookings

# Direct to flight-ops-service
curl http://localhost:8082/api/flights
```

---

## Business Rules

### Check-in Rules

| Rule | Description |
|------|-------------|
| Time Window | Check-in opens 24 hours before departure |
| Status Requirement | Booking must be CONFIRMED |
| Passenger Validation | All passengers must be validated |

### Passenger Validation

| Rule | Description |
|------|-------------|
| Adult Age | Must be 12+ years old |
| Child Age | 2-11 years old |
| Infant Age | Under 2 years old |
| Infant Companion | Must travel with adult |
| Max Infants | Maximum 1 infant per adult |

### Pricing Rules

| Passenger Type | Pricing |
|----------------|---------|
| Adult | Base fare × 1.0 |
| Child | Base fare × 0.75 (25% discount) |
| Infant | Base fare × 0.10 (90% discount) |

---

## Testing

40 tests. The booking rules — the part with real business logic — are covered by 30 of
them, and those run without Spring, a database or Kafka.

| Suite | Count | Needs |
| --- | ---: | --- |
| `BookingRulesValidatorTest` | 30 | nothing — plain JUnit, fixed clock |
| `BookingControllerTest` | 7 | Docker (Redis container) |
| Context tests (3 services) | 3 | Docker for booking-service |

The rules were originally private methods inside `BookingService`, which meant answering
"does a 12-year-old count as an adult?" required booting the entire Spring context. They
now live in `BookingRulesValidator`, a plain class with an injected `Clock`, so the
time-dependent rules (booking cut-off, check-in window) are deterministic instead of
depending on what time the test happens to run.

```bash
./mvnw test
```

```bash
./mvnw -pl booking-service test -Dtest=BookingRulesValidatorTest
```

The second command runs the 30 rule tests in about 80 milliseconds.

### Known gaps

- Coverage is concentrated in booking rules; the saga orchestrator and Kafka consumers
  are exercised only indirectly
- No end-to-end test spanning all four services
- No load or failure-injection testing

### Run Integration Tests (requires Docker)
```bash
# Start infrastructure
docker compose up -d zookeeper kafka redis

# Run tests
./mvnw verify -P integration-tests
```

### Manual Testing Commands

**Test API Gateway Rate Limiting:**
```bash
# Send 20 requests quickly (should get rate limited)
for i in {1..20}; do
  curl -w "%{http_code}\n" -o /dev/null -s http://localhost:8080/api/bookings
done
```

**Test Circuit Breaker:**
```bash
# Stop flight-ops-service and try booking
docker compose stop flight-ops-service

# Attempt booking - should get fallback response
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" \
  -d '{"flightNumber": "TK1", ...}'

# Circuit breaker metrics
curl http://localhost:8083/actuator/metrics/resilience4j.circuitbreaker.state
```

---

## Monitoring & Observability

### Distributed Tracing (Zipkin)

All requests are traced across services using B3 headers:
- `X-B3-TraceId` - Unique trace identifier
- `X-B3-SpanId` - Current span identifier
- `X-B3-ParentSpanId` - Parent span reference

**View Traces:**
1. Open http://localhost:9411
2. Select service and click "Find Traces"
3. Click on a trace to see the full request flow

### Prometheus Metrics

**Key Metrics:**
```promql
# API Gateway request rate
rate(gateway_requests_total[5m])

# Circuit breaker state
resilience4j_circuitbreaker_state{name="flightOps"}

# Outbox pending events
outbox_events_pending

# Kafka consumer lag
kafka_consumer_records_lag_max

# Cache hit rate
cache_gets{result="hit"} / cache_gets
```

### Grafana Dashboards

Pre-configured dashboards available at http://localhost:3001:
- **JVM Metrics** - Memory, GC, threads
- **API Gateway** - Request rates, latency, errors
- **Kafka** - Consumer lag, throughput
- **Circuit Breaker** - State, failure rate

### Health Endpoints

```bash
# API Gateway health
curl http://localhost:8080/actuator/health

# Individual service health
curl http://localhost:8083/actuator/health

# Detailed health with components
curl http://localhost:8083/actuator/health | jq
```

---

## Debugging

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f booking-service

# Filter by level
docker compose logs booking-service 2>&1 | grep ERROR
```

### Common Debug Scenarios

**1. Tracing a Request:**
```bash
# Get trace ID from response header
curl -v http://localhost:8080/api/bookings/ABC123 2>&1 | grep -i trace

# View in Zipkin
open http://localhost:9411/zipkin/?traceId=<trace-id>
```

**2. Circuit Breaker Issues:**
```bash
# Check circuit breaker state
curl http://localhost:8083/actuator/circuitbreakers

# Reset circuit breaker (if stuck open)
curl -X POST http://localhost:8083/actuator/circuitbreakers/flightOps/reset
```

**3. Outbox Processing Issues:**
```bash
# Check outbox table
curl http://localhost:8083/actuator/outbox/stats

# View pending events in H2
# JDBC URL: jdbc:h2:mem:bookingdb
# Query: SELECT * FROM outbox_event WHERE status = 'PENDING'
```

**4. Kafka Issues:**
```bash
# List topics
docker exec anadolu-kafka kafka-topics --list --bootstrap-server localhost:9092

# View consumer groups
docker exec anadolu-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --list

# Check consumer lag
docker exec anadolu-kafka kafka-consumer-groups --bootstrap-server localhost:9092 \
  --describe --group booking-service-group
```

---

## Docker Commands Reference

### Basic Commands

```bash
# Start all services
docker compose up -d

# Start with rebuild
docker compose up -d --build

# Stop all services
docker compose down

# Stop and remove volumes (clean start)
docker compose down -v

# View logs
docker compose logs -f [service-name]

# Restart single service
docker compose restart booking-service

# Scale a service
docker compose up -d --scale booking-service=3
```

### Container Access

```bash
# Access Redis CLI
docker exec -it anadolu-redis redis-cli

# Access Kafka shell
docker exec -it anadolu-kafka bash

# View container stats
docker stats
```

---

## Troubleshooting

### Common Issues

| Problem | Cause | Solution |
|---------|-------|----------|
| Connection refused to Redis | Redis not started | `docker compose up -d redis` |
| Circuit breaker always open | Downstream service unavailable | Check flight-ops-service logs |
| Outbox events not processing | Kafka connection issue | Check Kafka connectivity |
| Rate limit exceeded | Too many requests | Wait or increase limit |
| Check-in not allowed | Outside 24h window | Check flight departure time |

### Error Messages

**"CircuitBreaker 'flightOps' is OPEN"**
```bash
# Check downstream service
curl http://localhost:8082/actuator/health

# Wait for recovery or reset
curl -X POST http://localhost:8083/actuator/circuitbreakers/flightOps/reset
```

**"Check-in not allowed: Not within 24-hour check-in window"**
```bash
# Check flight schedule
curl http://localhost:8082/api/flights/TK1

# Ensure flight departs within 24 hours
```

**"Outbox processor: processed=0, failed=5"**
```bash
# Check Kafka connection
docker exec anadolu-kafka kafka-broker-api-versions --bootstrap-server localhost:9092

# Check DLQ for failed messages
curl http://localhost:8083/api/dlq/stats
```

---

## Architecture Decision Records

### ADR-001: API Gateway Pattern
**Decision:** Use Spring Cloud Gateway as the single entry point
**Rationale:** Centralized routing, security, rate limiting, and observability
**Alternatives Considered:** Netflix Zuul (deprecated), direct service access

### ADR-002: Saga Pattern over Two-Phase Commit
**Decision:** Orchestration-based Saga for distributed transactions
**Rationale:** Better suited for long-running transactions, more flexible failure handling
**Alternatives Considered:** Choreography-based Saga, 2PC (blocking, not cloud-native)

### ADR-003: Outbox Pattern for Reliable Messaging
**Decision:** Transactional outbox with polling publisher
**Rationale:** Guarantees exactly-once delivery without distributed transactions
**Alternatives Considered:** CDC with Debezium (more complex), direct Kafka writes (data loss risk)

### ADR-004: Redis for Caching and Rate Limiting
**Decision:** Redis as distributed cache and rate limiter store
**Rationale:** High performance, cluster support, Spring integration
**Alternatives Considered:** Hazelcast, Caffeine (in-memory only)

### ADR-005: Zipkin for Distributed Tracing
**Decision:** Zipkin with B3 header propagation
**Rationale:** Simple setup, good Spring Boot integration, adequate for demo
**Alternatives Considered:** Jaeger (more features but complex), OpenTelemetry (newer, less stable)

---

## Redis Cache Kullanımı

### Cache Yapısı

Projede Redis şu amaçlarla kullanılır:

| Cache Adı | TTL | Kullanım Yeri | Anadolu Air Senaryosu |
|-----------|-----|---------------|---------------|
| `bookings` | 5 dk | `BookingService.getBookingByPNR()` | Yolcu check-in öncesi PNR'ını defalarca kontrol eder |
| `flights` | 15 dk | `FlightOpsClient.getFlightBookingInfo()` | Aynı uçuş bilgisi birden fazla agent tarafından sorgulanır |
| `seatMaps` | 30 sn | Koltuk durumu | Gerçek zamanlı koltuk müsaitliği |
| `statistics` | 1 dk | Dashboard metrikleri | Operasyon ekranları |

### Cache Nasıl Çalışır?

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        CACHE AKIŞI                                       │
│                                                                         │
│   GET /api/bookings/ABC123                                              │
│            │                                                            │
│            ▼                                                            │
│   ┌─────────────────────┐                                               │
│   │  @Cacheable kontrol │ ◄── Spring AOP Proxy                          │
│   └──────────┬──────────┘                                               │
│              │                                                          │
│      ┌───────┴───────┐                                                  │
│      │               │                                                  │
│   Cache HIT       Cache MISS                                            │
│      │               │                                                  │
│      ▼               ▼                                                  │
│  ┌────────┐    ┌──────────────┐                                         │
│  │ Redis  │    │  Database    │                                         │
│  │ return │    │  + Redis'e   │                                         │
│  │  ~1ms  │    │  yaz ~50ms   │                                         │
│  └────────┘    └──────────────┘                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### Redis CLI Komutları

```bash
# Redis CLI'a bağlan
docker exec -it anadolu-redis redis-cli

# Tüm cache key'lerini listele
KEYS *

# Belirli bir booking'i görüntüle
GET "bookings::ABC123"

# Tüm bookings cache'ini temizle
KEYS "bookings*" | xargs redis-cli DEL

# Tüm cache'i temizle (DİKKAT!)
FLUSHALL

# Cache istatistiklerini gör
INFO stats

# Memory kullanımını gör
INFO memory

# Belirli key'in TTL'ini kontrol et
TTL "bookings::ABC123"
```

### Cache'i Monitoring ile Takip

```bash
# Gerçek zamanlı Redis komutlarını izle
docker exec -it anadolu-redis redis-cli MONITOR

# Sadece GET komutlarını filtrele (başka terminal)
docker exec -it anadolu-redis redis-cli MONITOR | grep GET
```

### Local Geliştirmede Debug

Cache aktifken breakpoint'ler çalışmaz (method çağrılmaz). Debug için:

**Yöntem 1: Local Profile Kullan (Önerilen)**
```bash
# Cache devre dışı, breakpoint çalışır
./mvnw spring-boot:run -pl booking-service -Dspring.profiles.active=local
```

**Yöntem 2: Sadece Infrastructure Başlat**
```bash
# Kafka, Redis, Zipkin'i başlat
docker compose up -d zookeeper kafka redis zipkin

# Servisleri IDE'den debug modda çalıştır
# IntelliJ: Run > Debug 'BookingServiceApplication'
```

**Yöntem 3: Redis'i Geçici Temizle**
```bash
# Test öncesi cache'i temizle
docker exec anadolu-redis redis-cli FLUSHALL
```

### Cache Log'larını Görüntüleme

```yaml
# application.yml (local profile'da zaten aktif)
logging:
  level:
    org.springframework.cache: TRACE
```

Log çıktısı:
```
TRACE - Cache entry for key 'ABC123' found in cache 'bookings'     # HIT
TRACE - No cache entry for key 'XYZ789' in cache 'bookings'        # MISS
```

### Cache Eviction (Silme) Senaryoları

| İşlem | Silinen Cache'ler | Neden |
|-------|-------------------|-------|
| `cancelBooking()` | bookings, flights | İptal sonrası eski veri gösterilmemeli |
| `checkIn()` | bookings, flights, seatMaps | Koltuk ataması değişti |
| `createBooking()` | flights (otomatik) | Koltuk sayısı değişti |

### Performans Karşılaştırması

| Senaryo | Cache Olmadan | Cache İle | İyileşme |
|---------|---------------|-----------|----------|
| PNR Sorgu | ~50ms | ~2ms | **25x** |
| Uçuş Bilgisi | ~100ms | ~3ms | **33x** |
| Manifest Listesi | ~200ms | ~5ms | **40x** |

---

## Production Readiness Checklist

### ✅ Implemented

- [x] **API Gateway** - Central entry point with routing
- [x] **Rate Limiting** - Redis-based request throttling
- [x] **Circuit Breaker** - Resilience4j with fallbacks
- [x] **Retry Pattern** - Automatic retry with backoff
- [x] **Bulkhead Pattern** - Thread pool isolation
- [x] **Distributed Tracing** - Zipkin with B3 headers
- [x] **Caching** - Redis with appropriate TTLs
- [x] **Saga Pattern** - Distributed transaction management
- [x] **Outbox Pattern** - Reliable message delivery
- [x] **Dead Letter Queue** - Failed message handling
- [x] **Health Checks** - All services with actuator
- [x] **Metrics** - Prometheus + Grafana
- [x] **Business Rules** - Check-in window, passenger validation
- [x] **Error Handling** - Global exception handler with proper responses

- [x] **Kubernetes Deployment** - Helm charts with HPA, PDB, Network Policies
- [x] **Auto-scaling** - Horizontal Pod Autoscaler (HPA) for all services
- [x] **High Availability** - Pod Disruption Budgets (PDB) for zero-downtime
- [x] **Security** - RBAC, Network Policies, Security Contexts, Non-root containers
- [x] **Topology Awareness** - Pod anti-affinity, zone spreading

### 🔄 Recommended for Real Anadolu Air Production

- [ ] **Service Mesh (Istio)** - Advanced traffic management, mTLS
- [ ] **Secrets Management** - HashiCorp Vault for credentials
- [ ] **Config Server** - Spring Cloud Config for centralized configuration
- [ ] **ELK Stack** - Centralized logging (Elasticsearch, Logstash, Kibana)
- [ ] **PagerDuty/OpsGenie** - Alert management integration
- [ ] **Database Migration** - Flyway/Liquibase for schema management
- [ ] **API Versioning** - v1/v2 endpoint support
- [ ] **OAuth 2.0 / OIDC** - Enterprise authentication
- [ ] **GraphQL Gateway** - For mobile/frontend optimization
- [ ] **Event Sourcing** - Full audit trail of all changes
- [ ] **CQRS** - Separate read/write models for scalability

---

## Quick Reference Card

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Anadolu Air FLIGHT SYSTEM                               │
├─────────────────────────────────────────────────────────────────────────┤
│ DOCKER DEPLOYMENT                                                       │
│ BUILD         │ ./mvnw clean install -DskipTests                        │
│ START         │ docker compose up -d --build                            │
│ STOP          │ docker compose down                                     │
│ LOGS          │ docker compose logs -f [service]                        │
│ TEST          │ ./mvnw test                                             │
├─────────────────────────────────────────────────────────────────────────┤
│ KUBERNETES DEPLOYMENT                                                   │
│ DEV INSTALL   │ helm install anadolu ./k8s/helm/anadolu-flight-system           │
│               │   -f ./k8s/helm/anadolu-flight-system/values-dev.yaml       │
│ PROD INSTALL  │ helm install anadolu ./k8s/helm/anadolu-flight-system           │
│               │   -f ./k8s/helm/anadolu-flight-system/values-prod.yaml      │
│ UPGRADE       │ helm upgrade anadolu ./k8s/helm/anadolu-flight-system           │
│ SKAFFOLD DEV  │ skaffold dev -p dev                                     │
│ STATUS        │ kubectl get pods -n anadolu-flight-system                   │
│ HPA STATUS    │ kubectl get hpa -n anadolu-flight-system                    │
├─────────────────────────────────────────────────────────────────────────┤
│ SERVICE PORTS                                                           │
│ 8080 - API Gateway (Main Entry Point) ★                                │
│ 8081 - Integration Service (SOAP)                                       │
│ 8082 - Flight Ops Service (WebSocket)                                   │
│ 8083 - Booking Service (REST)                                           │
├─────────────────────────────────────────────────────────────────────────┤
│ INFRASTRUCTURE PORTS                                                    │
│ 6379 - Redis (Caching)                                                  │
│ 9092 - Kafka (Messaging)                                                │
│ 9411 - Zipkin (Tracing) ★                                              │
│ 8090 - Kafka UI                                                         │
│ 9090 - Prometheus                                                       │
│ 3001 - Grafana (admin/admin)                                            │
├─────────────────────────────────────────────────────────────────────────┤
│ KEY FEATURES                                                            │
│ ✓ API Gateway with Rate Limiting                                        │
│ ✓ Circuit Breaker + Retry + Bulkhead                                    │
│ ✓ Distributed Tracing (Zipkin)                                          │
│ ✓ Redis Caching                                                         │
│ ✓ Saga Pattern (Distributed Transactions)                               │
│ ✓ Outbox Pattern (Reliable Messaging)                                   │
│ ✓ Dead Letter Queue                                                     │
│ ✓ Kubernetes with HPA + PDB                                             │
│ ✓ Helm Charts (Dev & Prod)                                              │
│ ✓ User Simulation (Load Testing)                                        │
├─────────────────────────────────────────────────────────────────────────┤
│ KEY URLS                                                                │
│ API Gateway   │ http://localhost:8080                                   │
│ Swagger       │ http://localhost:8083/swagger-ui.html                   │
│ Zipkin        │ http://localhost:9411                                   │
│ Grafana       │ http://localhost:3001                                   │
│ Kafka UI      │ http://localhost:8090                                   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## User Simulation (Load Testing)

Simulates real user traffic for testing, demos, and observability verification.

### Quick Start

```bash
# Start simulation
curl -X POST http://localhost:8083/api/simulation/start

# Check status
curl http://localhost:8083/api/simulation/stats

# Stop simulation
curl -X POST http://localhost:8083/api/simulation/stop
```

### API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/simulation/start` | POST | Start user simulation |
| `/api/simulation/stop` | POST | Stop user simulation |
| `/api/simulation/stats` | GET | Get simulation statistics |
| `/api/simulation/status` | GET | Check if simulation is running |

### What It Does

When enabled, the simulator automatically generates:

| Action | Frequency | Description |
|--------|-----------|-------------|
| **Bookings** | Every 2s (60%) | Random flights, 1-4 passengers |
| **Check-ins** | Every 2s (20%) | Check-in for created bookings |
| **Cancellations** | Every 2s (20%) | Cancel random bookings |
| **Burst Traffic** | Every 5s | 1-3 concurrent bookings |

### Sample Output

```json
{
  "running": true,
  "totalRequests": 150,
  "successfulBookings": 45,
  "failedBookings": 12,
  "totalCheckins": 28,
  "totalCancellations": 15,
  "activePNRs": 30
}
```

### Use Cases

1. **Demo/Presentation** - Show live system activity
2. **Load Testing** - Verify system under load
3. **Observability Testing** - Generate traces in Zipkin, metrics in Grafana
4. **Circuit Breaker Testing** - See resilience patterns in action

### Monitoring During Simulation

```bash
# Watch Zipkin traces
open http://localhost:9411

# Watch Grafana metrics
open http://localhost:3001

# Watch Kafka messages
open http://localhost:8090

# Watch service logs
docker compose logs -f booking-service | grep SIMULATION
```

---

## License

MIT License - Free to use for learning and portfolio purposes.

---

*A personal project exploring the patterns that distributed booking systems rely on: saga orchestration, transactional outbox, circuit breakers and dead-letter queues.*

*This project demonstrates the key architectural patterns and technologies used in modern airline booking systems, including those implemented by Anadolu Air.*
