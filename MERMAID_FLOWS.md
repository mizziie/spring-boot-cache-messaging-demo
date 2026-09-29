# Mermaid Flow Diagrams

ไฟล์นี้รวม mermaid diagrams สำหรับระบบ Redis + RabbitMQ + WebSocket + Ticketing

---

## Table of Contents

1. [High-Level Architecture](#1-high-level-architecture)
2. [User Booking Flow](#2-user-booking-flow)
3. [Payment Success Flow](#3-payment-success-flow)
4. [Expired Booking Cleanup Flow](#4-expired-booking-cleanup-flow)
5. [WebSocket Multi-Server with Redis Pub/Sub](#5-websocket-multi-server-with-redis-pubsub)
6. [Multi-Redis Architecture](#6-multi-redis-architecture)
7. [Waiting Room Flow](#7-waiting-room-flow)
8. [Redis + RabbitMQ Project Flow](#8-redis--rabbitmq-project-flow)

---

## 1. High-Level Architecture

```mermaid
graph TD
    User[User Browser]
    CDN[CDN / Static Assets]
    WR[Waiting Room]
    LB[Load Balancer]
    API[Spring Boot API]
    RedisCache[Redis Cache]
    RedisLock[Redis Lock]
    RedisQueue[Redis Waiting Queue]
    RedisPubSub[Redis Pub/Sub]
    DB[(PostgreSQL Database)]
    RMQ[RabbitMQ]
    Email[Email Service]
    QR[QR Service]
    Analytics[Analytics Service]
    WS[WebSocket Server]
    
    User --> CDN
    CDN --> WR
    WR --> LB
    LB --> API
    API --> RedisCache
    API --> RedisLock
    API --> RedisQueue
    API --> DB
    API --> RMQ
    RMQ --> Email
    RMQ --> QR
    RMQ --> Analytics
    API --> RedisPubSub
    RedisPubSub --> WS
    WS --> User
```

---

## 2. User Booking Flow

```mermaid
sequenceDiagram
    participant U as User Browser
    participant LB as Load Balancer
    participant API as Booking API
    participant RC as Redis Cache
    participant RL as Redis Lock
    participant DB as PostgreSQL
    participant RMQ as RabbitMQ
    participant WS as WebSocket Server
    
    U->>LB: Request booking page
    LB->>API: GET seat status
    API->>RC: GET seat:A1:status
    RC-->>API: AVAILABLE
    API-->>U: Show seat A1 available
    
    U->>LB: Click book A1
    LB->>API: POST /book A1
    API->>RC: GET seat:A1:status
    RC-->>API: AVAILABLE
    API->>RL: SET seat:A1:lock NX EX 300
    RL-->>API: OK
    
    API->>DB: SELECT ... FOR UPDATE seat A1
    DB-->>API: AVAILABLE
    
    API->>DB: UPDATE seat=LOCKED
    API->>DB: INSERT booking PENDING
    
    API->>RC: SET seat:A1:status LOCKED
    API-->>U: Booking pending, pay in 5 min
    
    API->>RMQ: publish booking.created
    RMQ->>WS: consume event
    WS->>U: WebSocket push: A1 LOCKED
```

---

## 3. Payment Success Flow

```mermaid
sequenceDiagram
    participant U as User
    participant API as Booking API
    participant PG as Payment Gateway
    participant DB as PostgreSQL
    participant RL as Redis Lock
    participant RC as Redis Cache
    participant RMQ as RabbitMQ
    participant WS as WebSocket Server
    participant Email as Email Service
    
    U->>API: Pay for booking
    API->>PG: Charge payment
    PG-->>API: Success
    API->>DB: UPDATE booking=CONFIRMED
    API->>RL: DEL seat:A1:lock
    API->>DB: UPDATE seat=BOOKED
    API->>RC: SET seat:A1:status BOOKED
    API-->>U: Payment success
    API->>RMQ: publish booking.confirmed
    RMQ->>WS: consume event
    WS->>U: WebSocket push: A1 BOOKED
    RMQ->>Email: send email with ticket
```

---

## 4. Expired Booking Cleanup Flow

```mermaid
sequenceDiagram
    participant SW as Scheduled Worker
    participant DB as PostgreSQL
    participant RC as Redis Cache
    participant RMQ as RabbitMQ
    participant WS as WebSocket Server
    participant U as User Browser
    
    Note over SW: Every 60 seconds
    SW->>DB: SELECT expired PENDING bookings
    DB-->>SW: list of expired bookings
    
    loop For each expired booking
        SW->>DB: UPDATE booking=EXPIRED
        SW->>DB: UPDATE seat=AVAILABLE
        SW->>RC: SET seat:A1:status AVAILABLE
        SW->>RMQ: publish seat.available
    end
    
    RMQ->>WS: consume event
    WS->>U: WebSocket push: A1 AVAILABLE
```

---

## 5. WebSocket Multi-Server with Redis Pub/Sub

```mermaid
sequenceDiagram
    participant U1 as User A Browser
    participant WS1 as WebSocket Server 1
    participant U2 as User B Browser
    participant WS2 as WebSocket Server 2
    participant Redis as Redis Pub/Sub
    participant API as Booking API
    
    U1->>WS1: Connect WebSocket
    U2->>WS2: Connect WebSocket
    
    Note over API: User A books seat A1
    API->>Redis: PUBLISH event:123:seats "A1:BOOKED"
    
    Redis->>WS1: Deliver A1:BOOKED
    Redis->>WS2: Deliver A1:BOOKED
    
    WS1->>U1: Send seat update
    WS2->>U2: Send seat update
```

---

## 6. Multi-Redis Architecture

```mermaid
graph LR
    API[Spring Boot API]
    RC[(Redis Cache)]
    RL[(Redis Lock)]
    RQ[(Redis Queue)]
    RPS[(Redis Pub/Sub)]
    DB[(PostgreSQL)]
    RMQ[RabbitMQ]
    WS[WebSocket Server]
    
    API -->|cache read/write| RC
    API -->|SET NX EX| RL
    API -->|INCR queue| RQ
    API -->|PUBLISH| RPS
    API -->|transaction| DB
    API -->|publish event| RMQ
    RMQ -->|consume| WS
    RPS -->|subscribe| WS
```

---

## 7. Waiting Room Flow

```mermaid
sequenceDiagram
    participant U as User Browser
    participant LB as Load Balancer
    participant WR as Waiting Room Service
    participant RQ as Redis Queue
    participant API as Booking API
    
    U->>LB: Open booking site
    LB->>WR: Check queue
    WR->>RQ: INCR queue:counter
    RQ-->>WR: queueNumber=5000
    WR->>RQ: GET queue:current
    RQ-->>WR: current=1000
    
    alt Not yet allowed
        WR-->>U: Show position 5000
        U->>WR: Poll every 5 sec
        WR->>RQ: GET queue:current
        RQ-->>WR: current=4500
        WR-->>U: Still waiting
    else Allowed
        WR-->>U: Redirect to booking page
        U->>API: Select seat
        API->>API: Normal booking flow
    end
```

---

## 8. Redis + RabbitMQ Project Flow

```mermaid
sequenceDiagram
    participant C as Client
    participant PC as ProductController
    participant PS as ProductService
    participant DB as In-Memory DB
    participant RC as Redis Cache
    participant RMQ as RabbitMQ
    participant PEL as ProductEventListener
    
    C->>PC: POST /api/products
    PC->>PS: create(product)
    PS->>DB: save product
    PS->>RC: evict productList::all
    PS->>RMQ: publish ProductEvent CREATE
    PS-->>PC: return product
    PC-->>C: response
    RMQ->>PEL: consume ProductEvent
    PEL->>PEL: log received event
```

---

## หมายเหตุ

mermaid diagrams อาจไม่ render ในทุก editor แต่ support ใน:
- GitHub / GitLab
- VS Code with Mermaid extension
- Markdown viewer ที่ support mermaid 
- Obsidian
