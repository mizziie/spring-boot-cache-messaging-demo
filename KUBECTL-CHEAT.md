# Cheat Sheet: kubectl Commands

ไฟล์นี้สรุปคำสั่ง `kubectl` ที่ใช้บ่อย ครอบคลุมการดู pod, image, restart, log, debug และ deploy Spring Boot app

---

## Table of Contents

1. [Pods / Deployment พื้นฐาน](#1-pods--deployment-พื้นฐาน)
2. [ดู Image](#2-ดู-image)
3. [Restart / Rollback](#3-restart--rollback)
4. [Logs / เข้า Pod](#4-logs--เข้า-pod)
5. [Apply / Delete / Scale](#5-apply--delete--scale)
6. [Namespace / Context](#6-namespace--context)
7. [Debug](#7-debug)
8. [Deploy Flow ของโปรเจคนี้](#8-deploy-flow-ของโปรเจคนี้)
9. [Anti-Patterns](#9-anti-patterns)
10. [Quick Reference](#10-quick-reference)

---

## 1. Pods / Deployment พื้นฐาน

```bash
kubectl get pods                            # ดู pods ทั้งหมดใน namespace ปัจจุบัน
kubectl get pods -n <namespace>             # ระบุ namespace
kubectl get pods -A                         # ทุก namespace
kubectl get pods -o wide                    # เห็น node + IP ด้วย
kubectl get pods -w                         # watch ดูสถานะเปลี่ยนแบบ realtime
kubectl get all                             # ดูทุกอย่างใน namespace
kubectl get deployments                     # ดู deployments
kubectl get svc                             # ดู services
kubectl describe pod <pod-name>             # รายละเอียด pod (ใช้ debug อาการพัง ดู Events ด้านล่าง)
kubectl describe node <node-name>           # รายละเอียด node
```

### Pod Status ที่เจอบ่อย

| Status | ความหมาย | วิธีแก้ |
|--------|----------|---------|
| `Pending` | ยังไม่ได้ schedule หรือกำลัง pull image | `describe pod` ดู Events |
| `CrashLoopBackOff` | container start แล้วตายวน | `kubectl logs --previous` |
| `ImagePullBackOff` | pull image ไม่ได้ | เช็คชื่อ/tag image, registry auth |
| `Running` | ทำงานปกติ | - |
| `OOMKilled` | memory เกิน limit | เพิ่ม memory limit |

---

## 2. ดู Image

```bash
# ดู image ของ pod
kubectl get pod <pod-name> -o jsonpath='{.spec.containers[*].image}'

# ดู image ของ deployment ทั้งหมด
kubectl get deployments -o jsonpath='{.items[*].spec.template.spec.containers[*].image}'

# ดูทุก image ใน namespace (sort ไม่ซ้ำ)
kubectl get pods -o jsonpath='{.items[*].spec.containers[*].image}' | tr -s ' ' '\n' | sort -u
```

---

## 3. Restart / Rollback

```bash
kubectl rollout restart deployment/<name>   # restart แบบ rolling (ใช้บ่อยสุด)
kubectl rollout status deployment/<name>    # ดู status การ restart/update
kubectl rollout history deployment/<name>   # ดู revision ย้อนหลัง
kubectl rollout undo deployment/<name>      # rollback กลับ version ก่อน
kubectl rollout undo deployment/<name> --to-revision=2   # rollback ไป revision เฉพาะ

# เปลี่ยน image ไป version ใหม่ (trigger rolling update)
kubectl set image deployment/<name> <container>=<image>:<tag>
```

> `rollout restart` จะทยอย restart pod ทีละตัว → app ไม่ล่มระหว่าง restart (ถ้ามี replicas > 1)

---

## 4. Logs / เข้า Pod

```bash
kubectl logs <pod-name>                     # ดู log
kubectl logs -f <pod-name>                  # follow log realtime
kubectl logs --previous <pod-name>          # log ของ container ที่ crash ก่อน restart (debug CrashLoopBackOff)
kubectl logs -l app=redis-demo              # log ทุก pod ที่มี label นี้
kubectl logs <pod-name> --tail=100          # เฉพาะ 100 บรรทัดล่าสุด
kubectl logs <pod-name> --since=5m          # เฉพาะ 5 นาทีล่าสุด

kubectl exec -it <pod-name> -- /bin/sh      # เข้า shell ใน pod
kubectl exec -it <pod-name> -- /bin/bash    # ถ้า image มี bash

# เช็ค env ใน pod
kubectl exec -it <pod-name> -- env
```

---

## 5. Apply / Delete / Scale

```bash
kubectl apply -f k8s.yaml                   # สร้าง/อัปเดต resource จากไฟล์
kubectl apply -f ./k8s/                     # apply ทั้งโฟลเดอร์
kubectl delete -f k8s.yaml                  # ลบ resource ตามไฟล์
kubectl delete pod <pod-name>               # ลบ pod (deployment จะสร้างใหม่เอง)
kubectl delete deployment <name>            # ลบ deployment ทั้งชุด
kubectl scale deployment/<name> --replicas=5   # scale pod
kubectl autoscale deployment/<name> --min=2 --max=10 --cpu-percent=80   # HPA
kubectl get hpa                             # ดู HPA
```

---

## 6. Namespace / Context

```bash
kubectl get ns                              # ดู namespace ทั้งหมด
kubectl config get-contexts                 # ดู cluster/context ที่ต่อได้
kubectl config current-context              # context ปัจจุบัน
kubectl config use-context <name>           # สลับ cluster
kubectl config set-context --current --namespace=<ns>   # set default namespace
kubectl get pods -n kube-system             # ดู pod ของระบบ k8s เอง
```

---

## 7. Debug

```bash
kubectl get events --sort-by=.lastTimestamp   # ดู event ล่าสุด (หา crash cause)
kubectl top pods                              # ดู CPU/Memory (ต้องมี metrics-server)
kubectl top nodes                             # resource ของ node
kubectl port-forward <pod-name> 8080:8080     # forward port มาที่ local ทดสอบ
kubectl port-forward svc/<svc-name> 8080:80   # forward ผ่าน service
kubectl run tmp --rm -it --image=busybox -- sh      # pod ชั่วคราว debug network/DNS
kubectl run tmp --rm -it --image=curlimages/curl -- sh  # pod ที่มี curl

# ดู yaml ของ resource ที่รันอยู่จริงใน cluster
kubectl get deployment <name> -o yaml
kubectl get pod <pod-name> -o yaml

# แก้ resource ตรง ๆ (เหมาะ debug — ไม่ใช่ production)
kubectl edit deployment <name>

# รอจน pod ready (ใช้ใน CI/script)
kubectl wait --for=condition=ready pod -l app=redis-demo --timeout=60s
```

### Debug Flow เมื่อ Pod พัง

```text
kubectl get pods                  → เจอ status แปลก
   ↓
kubectl describe pod <name>       → ดู Events ด้านล่างสุด
   ↓
kubectl logs <name> --previous    → ดู log ก่อนตาย
   ↓
kubectl exec -it <name> -- sh     → เข้าไปเช็ค env/network
```

---

## 8. Deploy Flow ของโปรเจคนี้

```bash
# 1. build jar
mvn clean package

# 2. build + push image
docker build -t <registry>/redis-demo:1.0 .
docker push <registry>/redis-demo:1.0

# 3. deploy
kubectl apply -f k8s.yaml

# 4. เช็คผล
kubectl rollout status deployment/redis-demo
kubectl get pods
kubectl logs -f -l app=redis-demo

# 5. ถ้าพัง rollback
kubectl rollout undo deployment/redis-demo
```

---

## 9. Anti-Patterns

| อย่าทำ | เพราะอะไร |
|--------|----------|
| `kubectl delete pod` เพื่อ fix bug | pod ใหม่จะพังเหมือนเดิม ต้องแก้ root cause |
| `kubectl edit` ใน production | แก้แล้วไม่มีใน git คนต่อไปไม่รู้ state จริง ใช้ `apply -f` แทน |
| ใช้ tag `latest` | ไม่รู้ว่า cluster รัน version ไหน rollback ยาก |
| `kubectl exec` แก้ไฟล์ใน pod | pod restart แล้วหายหมด |
| ลืม `-n <namespace>` | คำสั่งไปโดน namespace ผิด |

---

## 10. Quick Reference

| อยากทำ | Command |
|--------|---------|
| ดู pod | `kubectl get pods` |
| ดู image | `kubectl get deploy -o jsonpath='{.items[*].spec.template.spec.containers[*].image}'` |
| restart | `kubectl rollout restart deployment/<name>` |
| rollback | `kubectl rollout undo deployment/<name>` |
| ดู log | `kubectl logs -f <pod>` |
| log ก่อน crash | `kubectl logs --previous <pod>` |
| เข้า pod | `kubectl exec -it <pod> -- sh` |
| scale | `kubectl scale deployment/<name> --replicas=N` |
| debug event | `kubectl describe pod <pod>` หรือ `kubectl get events` |
| ทดสอบ local | `kubectl port-forward <pod> 8080:8080` |

---

End of cheat sheet.
