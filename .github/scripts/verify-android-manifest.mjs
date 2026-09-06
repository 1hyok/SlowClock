#!/usr/bin/env node

import path from 'node:path';
import process from 'node:process';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';

// app 의 merged manifest(processDebugMainManifest) 기준 허용 목록. 라이브러리가 병합하는 권한도 여기
// 없으면 실패한다 — Firebase Analytics 의 광고 ID(AD_ID·ACCESS_ADSERVICES_*)는 app manifest 가
// tools:node="remove" 로 빼므로 목록에 없다. 새 권한은 Play 데이터 보안·권한 선언 양식과 함께 검토한다.
export const ALLOWED_PERMISSIONS = new Set([
  'android.permission.ACCESS_NETWORK_STATE',
  'android.permission.FOREGROUND_SERVICE',
  'android.permission.FOREGROUND_SERVICE_DATA_SYNC',
  // 알람이 울리는 동안 소리를 재생하는 포그라운드 서비스의 타입 권한. 이것이 없으면
  // startForeground 가 SecurityException 으로 죽는다 (#122).
  'android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK',
  'android.permission.INTERNET',
  'android.permission.POST_NOTIFICATIONS',
  // 기기를 껐다 켜면 AlarmManager 의 예약이 전부 취소된다. 이 방송을 받아야 걸어 둔 알람을
  // 다시 걸 수 있다. Play 데이터 보안 양식에는 수집 항목이 늘지 않는다 — 기기 밖으로 나가는
  // 데이터가 없고 기기 안 장부만 다시 읽는다 (#127).
  'android.permission.RECEIVE_BOOT_COMPLETED',
  'android.permission.SCHEDULE_EXACT_ALARM',
  // 알람이 본업인 앱에 자동으로 부여되고 사용자가 회수할 수 없다. 앱을 오래 안 열어도 대기
  // 버킷이 RESTRICTED 로 떨어지지 않게 해, 고령 사용자가 앱을 열지 않아도 알람이 계속 울린다.
  // Play 는 알람·타이머 앱에 이 권한을 명시적으로 허용한다 (#122).
  'android.permission.USE_EXACT_ALARM',
  'android.permission.USE_FULL_SCREEN_INTENT',
  'android.permission.VIBRATE',
  'android.permission.WAKE_LOCK',
  'com.google.android.c2dm.permission.RECEIVE',
  'com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE',
  'com.google.android.providers.gsf.permission.READ_GSERVICES',
]);

// exported 인데 permission 이 없는 컴포넌트 허용 목록. firebase-ui-auth 가 끌어오는 Facebook SDK 의
// CustomTabActivity 는 Facebook 로그인을 쓰지 않아도 병합된다 — 의존성 제외는 별도 이슈.
export const ALLOWED_UNPROTECTED_EXPORTED_COMPONENTS = new Set([
  'androidx.activity.ComponentActivity',
  'androidx.compose.ui.tooling.PreviewActivity',
  'com.example.slowclock.MainActivity',
  'com.facebook.CustomTabActivity',
  'com.google.firebase.auth.internal.GenericIdpActivity',
  'com.google.firebase.auth.internal.RecaptchaActivity',
]);

function attributes(source) {
  return Object.fromEntries(
    [...source.matchAll(/android:([A-Za-z][A-Za-z0-9]*)\s*=\s*"([^"]*)"/g)].map((match) => [
      match[1],
      match[2],
    ]),
  );
}

export function inspectManifest(
  source,
  {
    allowedPermissions = ALLOWED_PERMISSIONS,
    allowedUnprotectedExportedComponents = ALLOWED_UNPROTECTED_EXPORTED_COMPONENTS,
  } = {},
) {
  const violations = [];
  const application = /<application\b([^>]*)>/s.exec(source);
  if (!application) {
    violations.push('application declaration is missing');
  } else {
    const applicationAttributes = attributes(application[1]);
    if (applicationAttributes.usesCleartextTraffic !== 'false') {
      violations.push('application must explicitly set android:usesCleartextTraffic="false"');
    }
  }

  for (const match of source.matchAll(/<uses-permission\b([^>]*)\/?\s*>/gs)) {
    const permission = attributes(match[1]).name;
    if (!permission) {
      violations.push('uses-permission without android:name');
      continue;
    }
    const isAndroidxDynamicReceiverPermission = permission.endsWith('.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION');
    if (!allowedPermissions.has(permission) && !isAndroidxDynamicReceiverPermission) {
      violations.push(`permission is not allowlisted: ${permission}`);
    }
  }

  for (const match of source.matchAll(/<(activity|activity-alias|service|receiver|provider)\b([^>]*)\/?\s*>/gs)) {
    const [, kind, rawAttributes] = match;
    const component = attributes(rawAttributes);
    if (component.exported !== 'true') {
      continue;
    }
    const name = component.name ?? '(missing android:name)';
    if (kind === 'provider') {
      violations.push(`exported provider is forbidden: ${name}`);
      continue;
    }
    if (!component.permission && !allowedUnprotectedExportedComponents.has(name)) {
      violations.push(`unprotected exported ${kind} is not allowlisted: ${name}`);
    }
  }

  return violations;
}

async function main() {
  const manifestPath = process.argv[2];
  if (!manifestPath || process.argv.length !== 3) {
    throw new Error('Usage: verify-android-manifest.mjs <merged-AndroidManifest.xml>');
  }

  const source = await readFile(manifestPath, 'utf8');
  const violations = inspectManifest(source);
  if (violations.length > 0) {
    throw new Error(violations.map((violation) => `Manifest policy: ${violation}`).join('\n'));
  }
  console.log(`Manifest policy: passed (${manifestPath})`);
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : '';
if (import.meta.url === invokedPath) {
  main().catch((error) => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
