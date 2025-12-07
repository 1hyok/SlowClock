# Firebase/Google Cloud 알림 메일 중단 가이드

## 🔴 즉시 조치 필요 사항

### 1. 사용하지 않는 Realtime Database 삭제

**문제**: `itemdatabase-8a191-default-rtdb` 데이터베이스가 6개월간 사용되지 않아 비활성화 예정

**해결 방법**:
1. [Firebase Console](https://console.firebase.google.com/project/slow-clock-scheduler/database) 접속
2. 왼쪽 메뉴에서 **Realtime Database** 선택
3. `itemdatabase-8a191-default-rtdb` 데이터베이스 찾기
4. 데이터베이스 옆 **⋮** (점 3개) 클릭 → **데이터베이스 삭제** 선택
5. 확인 메시지에 "삭제" 입력하여 완전 삭제

**결과**: 이 알림 메일이 더 이상 오지 않습니다.

---

### 2. 노출된 서비스 계정 키 폐기 및 교체 ⚠️ 긴급

**문제**: 서비스 계정 키가 GitHub에 노출되어 보안 경고 발생

**현재 상태**: 
- `app/src/main/res/raw/service_account` 파일에 키가 포함되어 있음
- 이미 GitHub에 커밋되어 노출되었을 가능성 높음

**해결 방법**:

#### Step 1: 기존 키 폐기
1. [Google Cloud Console - IAM & Admin](https://console.cloud.google.com/iam-admin/serviceaccounts?project=slow-clock-scheduler) 접속
2. 서비스 계정 목록에서 `firebase-adminsdk-fbsvc@slow-clock-scheduler.iam.gserviceaccount.com` 찾기
3. 클릭 → **키** 탭 선택
4. 노출된 키(키 ID: `51511ce6ae310f6663ad0bd156af871065e0d565`) 찾기
5. **삭제** 또는 **사용 중지** 클릭하여 즉시 폐기

#### Step 2: 새 키 생성
1. 같은 서비스 계정 페이지에서 **키 추가** → **새 키 만들기** 선택
2. **JSON** 형식 선택 → **만들기** 클릭
3. 다운로드된 JSON 파일을 `app/src/main/res/raw/service_account`로 저장
   - 파일명은 그대로 `service_account` (확장자 없음)

#### Step 3: Git에서 노출된 키 제거 (중요!)
```bash
# Git 히스토리에서 키 파일 제거
git filter-branch --force --index-filter \
  "git rm --cached --ignore-unmatch app/src/main/res/raw/service_account" \
  --prune-empty --tag-name-filter cat -- --all

# 또는 BFG Repo-Cleaner 사용 (더 빠름)
# https://rtyley.github.io/bfg-repo-cleaner/
```

**주의**: 이미 노출된 키는 Git 히스토리에 남아있으므로, 민감한 프로젝트라면 레포지토리를 비공개로 전환하거나 새로 만드는 것을 고려하세요.

---

### 3. Firestore 보안 규칙 확인 및 수정

**문제**: Firestore Rules가 만료되거나 안전하지 않을 수 있음

**해결 방법**:
1. [Firebase Console - Firestore Database](https://console.firebase.google.com/project/slow-clock-scheduler/firestore) 접속
2. **규칙** 탭 선택
3. 현재 규칙 확인 및 다음처럼 수정:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // 사용자 컬렉션: 본인만 읽기/쓰기 가능
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // 일정 컬렉션: 본인 일정만 읽기/쓰기 가능
    match /schedules/{scheduleId} {
      allow read, write: if request.auth != null && 
        (resource.data.userId == request.auth.uid || 
         request.resource.data.userId == request.auth.uid);
    }
    
    // 가족 그룹 컬렉션: 그룹 멤버만 접근 가능
    match /familyGroups/{groupId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && 
        request.auth.uid in resource.data.memberIds;
    }
    
    // 알림 컬렉션: 본인 알림만 읽기 가능
    match /notifications/{notificationId} {
      allow read: if request.auth != null && 
        resource.data.userId == request.auth.uid;
      allow write: if request.auth != null;
    }
  }
}
```

4. **게시** 클릭하여 규칙 적용

---

### 4. Smartee 프로젝트 관련 (별도 프로젝트)

**문제**: Smartee 프로젝트의 Storage/Firestore 액세스 만료

**해결 방법**:
- Smartee 프로젝트를 더 이상 사용하지 않는다면:
  1. [Firebase Console](https://console.firebase.google.com/) 접속
  2. Smartee 프로젝트 선택
  3. 프로젝트 설정 → **프로젝트 삭제** 선택

- 계속 사용한다면:
  1. Smartee 프로젝트의 Firestore/Storage 규칙을 위와 같이 수정
  2. 보안 규칙을 게시하여 액세스 복구

---

## ✅ 완료 체크리스트

- [ ] 사용하지 않는 Realtime Database 삭제 완료
- [ ] 노출된 서비스 계정 키 폐기 완료
- [ ] 새 서비스 계정 키 생성 및 교체 완료
- [ ] Firestore 보안 규칙 확인 및 수정 완료
- [ ] Smartee 프로젝트 처리 완료 (삭제 또는 규칙 수정)
- [ ] Git 히스토리에서 노출된 키 제거 (선택사항, 중요도 높음)

---

## 📧 메일 알림 중단 확인

위 작업을 완료한 후 24-48시간 내에 메일 알림이 중단됩니다. 
만약 계속 메일이 온다면, 해당 프로젝트의 설정을 다시 확인하세요.

