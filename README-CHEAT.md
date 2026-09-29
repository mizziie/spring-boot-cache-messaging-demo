# Cheat Sheet: Redis + RabbitMQ + WebSocket + Ticketing System

ไฟล์นี้สรุปเนื้อหาที่ใช้ทบทวนก่อนสอบ/สัมภาษณ์ ครอบคลุม Redis, RabbitMQ, WebSocket และระบบกดบัตร

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Redis Cheat Sheet](#2-redis-cheat-sheet)
3. [RabbitMQ Cheat Sheet](#3-rabbitmq-cheat-sheet)
4. [Redis + RabbitMQ Flows](#4-redis--rabbitmq-flows)
5. [WebSocket Integration](#5-websocket-integration)
6. [Ticketing System Deep Dive](#6-ticketing-system-deep-dive)
7. [Useful Commands](#7-useful-commands)
8. [Interview Q&A](#8-interview-qa)
9. [Anti-Patterns](#9-anti-patterns)
10. [Production Checklist](#10-production-checklist)

---

## 1. Architecture Overview

```text
User
  ↓
Load Balancer / CDN
  ↓
Waiting Room (Redis Queue)
  ↓
API Gateway / Spring Boot
  ↓
Redis (Cache + Lock) + Database (ACID)
  ↓
RabbitMQ (Async Events)
  ↓
WebSocket Server (Real-time Push)
  ↓
Browser Client
```

หลักการ:

- **Redis** = ความเร็ว + ชั่วคราว
- **Database** = ความจริงสุดท้าย
- **RabbitMQ** = ส่งงานรองแบบ async
- **WebSocket** = push ผลลัพธ์ไปหน้าจอ user

---

## 2. Redis Cheat Sheet

### 2.1 Use Cases

| ใช้งาน | เหตุผล |
|--------|--------|
| Cache | อ่านข้อมูลเร็ว ลด load DB |
| Session | เก็บ login session + TTL |
| Distributed Lock | กันคนกดพร้อมกัน |
| Rate Limiter | จำกัด request ต่อ user |
| Waiting Room Queue | จัดการคิวเข้าระบบ |
| Real-time data | เก็บข้อมูลที่เปลี่ยนเร็ว |

### 2.2 Cache Pattern

```text
Client → API → Redis Cache?
              ↓
        HIT → ตอบกลับทันที
        MISS → DB → เก็บ Cache → ตอบกลับ
```

### 2.3 Spring Cache Annotations

| Annotation | ทำอะไร |
|------------|--------|
| `@Cacheable` | ถ้ามี cache คืนค่าทันที ไม่รัน method |
| `@CachePut` | อัปเดต cache หลังรัน method |
| `@CacheEvict` | ลบ cache |

### 2.4 Redis Lock for Ticketing

```bash
SET seat:A1:lock "user_123" NX EX 300
```

| ส่วน | ความหมาย |
|------|----------|
| `SET` | set ค่า |
| `seat:A1:lock` | key ล็อกที่นั่ง A1 |
| `"user_123"` | ระบุคนจอง |
| `NX` | set ได้เฉพาะถ้ายังไม่มี |
| `EX 300` | หมดอายุใน 300 วินาที |

### 2.5 Why Redis Lock?

- กันคนกดพร้อมกัน (atomic)
- ตอบเร็ว ~ms
- TTL หมดอายุเอง กันจองแล้วไม่จ่าย

---

## 3. RabbitMQ Cheat Sheet

### 3.1 Components

| ตัว | หน้าที่ |
|-----|---------|
| Producer | ส่ง message |
| Exchange | รับ message ตัดสินส่งไป queue ไหน |
| Queue | เก็บ message รอ consumer |
| Binding | กฎระหว่าง exchange กับ queue |
| Routing Key | ชื่อที่ใช้เลือก queue |
| Consumer | รับ message ไปประมวลผล |

### 3.2 Exchange Types

| Type | ทำงาน | ใช้เมื่อไหร |
|------|--------|------------|
| Direct | Routing key ตรงเป๊ะ | 1 ต่อ 1 queue |
| Fanout | ส่งทุก queue ที่ bind | Broadcast |
| Topic | Routing key ตรง pattern | Hierarchy event |
| Headers | ตรงตาม header | Complex filter |

### 3.3 ACK and Durability

| คอนเซ็ป | ทำอะไร |
|---------|--------|
| Manual ACK | ยืนยันว่าทำงานเสร็จ กัน message หาย |
| Durable Queue | Queue ยังอยู่หลัง RabbitMQ restart |
| Persistent Message | Message เก็บลง disk |
| DLQ | เก็บ message ที่ fail |

### 3.4 Retry + DLQ Flow

```text
main.queue
   ↓
consumer fail
   ↓
retry จำกัดครั้ง (เช่น 5)
   ↓
ยัง fail → DLX → DLQ
   ↓
alert / manual fix / replay
```

---

## 4. Redis + RabbitMQ Flows

### 4.1 Short Version

- **Redis** = ด่านหน้า (เร็ว)
- **RabbitMQ** = ส่งงานรอง (async)
- **Database** = ความจริงสุดท้าย

### 4.2 Order Flow

```text
User สั่งซื้อ
   ↓
Order API
   ↓
Redis Cache (ตรวจสินค้า)
   ↓
Database (บันทึก order)
   ↓
ตอบ user สำเร็จ
   ↓
RabbitMQ event "order.created"
   ↓
Email Service / Inventory / Analytics
```

### 4.3 Project Flow: POST /api/products

```text
POST /api/products
   ↓
ProductService
   ↓
บันทึก in-memory db
   ↓
@CacheEvict("productList") ลบ list cache
   ↓
publish ProductEvent ไป RabbitMQ
   ↓
ตอบกลับ Client
   ↓
ProductEventListener log event
```

---

## 5. WebSocket Integration

### 5.1 Roles

| ตัว | หน้าที่ |
|-----|---------|
| Redis | Cache, lock, session, waiting room |
| RabbitMQ | Event ระหว่าง backend service |
| WebSocket | Push real-time ไป browser |
| Database | ACID, ความจริงสุดท้าย |

### 5.2 Seat Booked Real-time Update

```text
User A จองที่นั่ง A1 สำเร็จ
   ↓
Booking API บันทึก DB
   ↓
publish "seat.A1.booked" ไป RabbitMQ
   ↓
WebSocket Server รับ event
   ↓
broadcast ไป browser ทุกคน
   ↓
UI อัปเดต: A1 เปลี่ยนเป็นสีแดง
```

---

## 6. Ticketing System Deep Dive

### 6.1 Problems to Solve

- คนเข้าพร้อมกันเยอะ
- ที่นั่งจำกัด
- ห้าม double booking
- ตอบผลเร็ว

### 6.2 Multi-Layer Architecture

```text
User
  ↓
Waiting Room (Redis Queue)
  ↓
API
  ↓
Redis Cache (ดูสถานะที่นั่ง)
  ↓
Redis Lock (SET NX EX)
  ↓
Database Transaction (ACID)
  ↓
RabbitMQ (ส่ง email/QR/analytics)
  ↓
WebSocket (อัปเดต UI)
```

### 6.3 Booking Flow

```text
User กดจอง A1
   ↓
[1] Redis Cache: GET seat:A1:status
        ├─ ไม่ว่าง → reject
        └─ ว่าง → [2]
   ↓
[2] Redis Lock: SET seat:A1:lock NX EX 300
        ├─ (nil) → reject
        └─ OK → [3]
   ↓
[3] DB Check FOR UPDATE
        ├─ ไม่ว่าง → ลบ lock → reject
        └─ ว่าง → [4]
   ↓
[4] UPDATE seat=LOCKED, INSERT booking PENDING
   ↓
[5] ตอบ user: "จองสำเร็จ กรุณาจ่ายใน 5 นาที"
   ↓
[6] RabbitMQ event
   ↓
Email / QR / Analytics
```

### 6.4 If User Does Not Pay in 5 Minutes

```text
Redis lock หายไปเอง (EX 300 หมด)
   ↓
Scheduled Worker ตื่นทุก 1 นาที
   ↓
SELECT booking WHERE status=PENDING AND expires_at < NOW
   ↓
UPDATE booking=EXPIRED
UPDATE seat=AVAILABLE
   ↓
Redis Cache อัปเดตใหม่
```

### 6.5 If Payment Succeeds

```text
User จ่ายเงิน
   ↓
Payment Gateway ตอบ success
   ↓
UPDATE booking=CONFIRMED
   ↓
DEL seat:A1:lock
   ↓
UPDATE seat=BOOKED
   ↓
RabbitMQ "booking.confirmed"
   ↓
Email ส่งบัตร + QR
```

---

## 7. Useful Commands

### 7.1 Redis

```bash
# ดูทุก key (ระวังใน production ใช้ SCAN แทน)
KEYS *

# ดู key ทีละชุด (production-safe)
SCAN 0 MATCH products:* COUNT 100

# ดูค่าและ TTL
GET products::1
TTL products::1

# ล้างทุก key
FLUSHALL

# ดู memory
INFO memory

# ดูทุกคำสั่งที่เข้ามา
MONITOR
```

### 7.2 RabbitMQ

```bash
# ดู queue
docker exec rabbitmq rabbitmqctl list_queues

# ดู queue ละเอียด
docker exec rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged consumers

# ดู connections
docker exec rabbitmq rabbitmqctl list_connections

# ดู exchanges
docker exec rabbitmq rabbitmqctl list_exchanges

# ดู bindings
docker exec rabbitmq rabbitmqctl list_bindings

# ดู logs
docker logs rabbitmq
```

### 7.3 API Test (PowerShell)

```powershell
# ดูสินค้าทั้งหมด
curl.exe http://localhost:8080/api/products

# ดูสินค้ารายชิ้น
curl.exe http://localhost:8080/api/products/1

# สร้างสินค้าใหม่
curl.exe -X POST http://localhost:8080/api/products `
  -H "Content-Type: application/json" `
  -d '{"name":"Headphone","price":99.99}'
```

---

## 8. Interview Q&A

### Redis

| Question | Answer |
|----------|--------|
| ทำไมใช้ Redis? | เร็ว ลด load DB ทำ cache/lock/session |
| Redis กับ Database ต่างกันยังไง? | Redis เร็วแต่ไม่ ACID, DB ช้าแต่ durability |
| ถ้า Redis ล่มทำยังไง? | Fallback ไป DB หรือ cache miss แล้ว rebuild |
| Cache hit/miss ต่างกันยังไง? | Hit = อ่านจาก cache, Miss = ไป DB แล้ว cache ใหม่ |
| ทำไม JSON ใน Redis มี `@class`? | `GenericJackson2JsonRedisSerializer` เก็บ type info |
| Redis lock ทำไมต้อง NX EX? | NX = ได้แค่คนแรก, EX = หมดอายุกันค้าง lock |

### RabbitMQ

| Question | Answer |
|----------|--------|
| RabbitMQ ทำอะไร? | Message broker ส่ง event async ระหว่าง service |
| ทำไมไม่ใช้ Kafka? | RabbitMQ setup ง่ายกว่า มี routing + DLQ ง่าย ถ้า event ไม่มหาศาล |
| Manual ACK vs Auto ACK? | Manual ปลอดภัยกว่า กัน message หายตอน consumer fail |
| DLQ ทำอะไร? | เก็บ message ที่ fail ไม่ให้ loop |
| ถ้า consumer ล่ม message หายไหม? | ไม่หายถ้า queue durable + message persistent + manual ACK |
| Exchange มีกี่แบบ? | Direct, Fanout, Topic, Headers |

### WebSocket

| Question | Answer |
|----------|--------|
| WebSocket ต่างจาก RabbitMQ ยังไง? | WebSocket คุยกับ browser แบบ real-time, RabbitMQ คุยกันใน backend |
| ทำไมต้องใช้คู่กัน? | RabbitMQ ส่ง event ระหว่าง service, WebSocket push ไปหน้าจอ user |
| ถ้ามี WebSocket server หลายตัวทำยังไง? | ใช้ Redis Pub/Sub หรือ RabbitMQ เป็น broadcast bus |

### Ticketing

| Question | Answer |
|----------|--------|
| ทำไมไม่ใช้ RabbitMQ ตรงจุดจองที่นั่ง? | Async มี delay อาจ double booking |
| ทำไมต้อง Redis lock? | กันคนกดพร้อมกัน ตอบเร็ว ~ms |
| ทำไมต้อง DB transaction? | ACID ความจริงสุดท้าย กัน double booking |
| ถ้า Redis lock หายแล้ว DB ยัง PENDING ทำยังไง? | ตรวจ DB ก่อนและหลัง lock, ใช้ DB constraint, มี cleanup scheduler |
| ทำไมต้อง waiting room? | ควบคุมจำนวนคนเข้าระบบ ไม่ให้ DB/Redis รับ load หมื่นคนพร้อมกัน |

---

## 9. Anti-Patterns

| อย่าทำ | เพราะอะไร |
|--------|----------|
| Auto ACK กับงานสำคัญ | Message อาจหาย |
| Requeue ไม่จำกัดครั้ง | Infinite loop |
| ใช้ RabbitMQ ตรงจุดจองที่นั่ง | อาจ double booking |
| ไม่ set TTL ให้ Redis lock | Lock ค้างถาวร |
| ไม่ cleanup expired booking | ที่นั่งถูกจองแล้วไม่คืน |
| ไม่ durable queue | Queue หายตอน restart |
| ใช้ `KEYS *` ใน production | Block Redis |
| ไม่ validate input | Error 500 ง่าย |

---

## 10. Production Checklist

| ข้อ | สถานะ |
|------|--------|
| Redis persistence / cluster | [ ] |
| RabbitMQ durable queue + persistent message | [ ] |
| Manual ACK + DLQ | [ ] |
| Retry จำกัดครั้ง | [ ] |
| Idempotency | [ ] |
| Input validation | [ ] |
| Global exception handler | [ ] |
| Logging + monitoring | [ ] |
| Database lock / constraint | [ ] |
| Scheduled cleanup | [ ] |
| WebSocket scale หลาย server | [ ] |
| Security / JWT / rate limit | [ ] |

---

## Quick Reference: Layer Responsibilities

| Layer | Responsibility | Example |
|-------|----------------|---------|
| Redis | Speed / temporary data | Cache, lock, session, waiting room |
| RabbitMQ | Async event delivery | Email, QR, analytics |
| WebSocket | Real-time UI update | Seat status, queue position |
| Database | Source of truth | Booking, order, user |

---

End of cheat sheet.
