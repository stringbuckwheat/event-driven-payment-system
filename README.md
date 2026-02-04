# 💸 Event-Driven Payment System 💸

Kafka와 Outbox 패턴을 활용한 **이벤트 기반 비동기 결제 시스템**입니다.   
결제 요청 → 처리 → 결과 반영 전 과정을 **정합성, 멱등성, 장애 복원성**을 중심으로 설계했습니다.

---

## 🛠 기술 스택

- Java 21, Spring Boot 4.1
- Apache Kafka, Spring Data JPA
- MySQL, Redis
- WireMock (PG Mock)

---

## ✨ 주요 구현 내역

- Kafka 기반 **비동기 결제 파이프라인**
- Outbox 패턴으로 **DB 트랜잭션과 메시지 발행 정합성 보장**
- 상태머신 + Claim 기반 **중복 결제 방지**
- 결제 단계를 **트랜잭션 경계별로 분리**
- 재시도 / 타임아웃 / Stuck 결제 복구 로직 구현
- Redis 기반 **장바구니 캐시**

---

## 🧭 결제 처리 흐름

![flow](diagram/flow.png)

---

## 🔁 상태 머신

![pay_status](diagram/pay_status.png)
![outbox_status](diagram/outbox_status.png)

---

## 🔐 Claim 기반 결제 선점

동일 Payment ID에 대해 **단 하나의 워커만 결제 수행 가능**하도록 조건부 업데이트 방식으로 선점합니다.

```sql
UPDATE payment
SET status = PROCESSING
WHERE id = ?
  AND status = REQUESTED
```

---

## 🧱 Transaction Boundary 분리

 단계	                 | 트랜잭션 
---------------------|------
 주문 생성 / 결제 생성	      | TX1  
 결제 선점(claim)	       | TX2  
 PG 호출	              | 없음   
 결과 확정 + 로그 + Outbox | 	TX3 
 결과 반영(Order)	       | TX4  

> PG는 트랜잭션 밖에서 호출하여 DB Lock 확산과 장기 트랜잭션을 방지합니다.

---

## 🔄 Retry / Timeout / Recovery 전략
- Kafka `DefaultErrorHandler` 기반 재시도 
- transient 오류 시 `PROCESSING → READY` 되돌려 재시도 유도 
- 일정 시간 이상 PROCESSING 상태 유지 시 
  - → stuck 결제 FAILED 확정 + 실패 이벤트 발행

---

## 🧠 설계 의도
- DB 트랜잭션과 메시지 발행을 분리하면 유실 위험 → **Outbox 패턴 도입**
- 워커 수 증가 시 중복 처리 위험 → **Claim(조건부 업데이트) 방식**
- 긴 PG 호출로 DB Lock 확산 위험 → **트랜잭션 외부 호출**

---

## 📌 기대 효과
- 메시지 유실 없는 결제 이벤트 처리
- 워커 수평 확장 가능
- 장애 발생 시 자동 재시도 및 복구
