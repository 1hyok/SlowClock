# 기기 백업·복원 검증

설치 표시, 공유 코드가 포함된 설정, 예약·미룸 알람 장부는 클라우드 백업과 기기 이전에서 제외한다. 새 기기에서 로그인과 공유 코드 등록을 다시 거치며 테마도 기본값으로 시작한다. 서버의 일정은 삭제하지 않는다. 일반 앱 업데이트는 현재 기기의 설정을 유지한다.

이전 버전 백업에 해당 파일이 들어 있을 수 있어 `SlowClockBackupAgent.onRestoreFinished()`에서 같은 네 preferences를 동기적으로 비운다. 이 콜백은 앱의 Hilt Application/Firebase provider를 사용하지 않는다. 복원 뒤 앱을 처음 열면 기존 새 설치 처리에서 인증 세션을 정리한다.

## 자동 검사

- `node --test .github/scripts/backup-policy.test.mjs`: 두 백업 형식의 제외 목록과 manifest의 복원 agent·Auto Backup 모드 연결.
- `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.slowclock.backup.SlowClockBackupAgentTest`: Android에서 실제 복원 완료 콜백을 실행한다. 이전 설치 값 제거, 다른 preferences 보존, 재실행 안전성을 확인한다. 테스트 preferences에는 별도 접두사를 붙여 기존 로그인·설정을 보존한다.

## 기기 이전 확인

이전 버전 백업을 새 버전 앱으로 복원한 뒤, 첫 실행 전 기기별 preferences가 비어 있는지 확인한다. 첫 실행에는 새 로그인을 요구하고 공유 목록은 코드 재등록 전 표시되지 않아야 한다. 원래 기기에서 앱만 업데이트하면 기존 로그인·공유 설정이 유지되어야 한다.

2026-09-06 API 34 전용 에뮬레이터에서 콜백 계측 검사를 통과했다. Google 클라우드 백업·제조사 기기 이전 전체 왕복은 별도 기기 검증 대상이다.

근거: [Android Auto Backup의 사용자 정의 agent](https://developer.android.com/identity/data/autobackup#ImplementingBackupAgent), [복원 완료 콜백](https://developer.android.com/reference/android/app/backup/BackupAgent#onRestoreFinished()).
