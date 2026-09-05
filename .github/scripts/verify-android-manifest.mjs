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
  'android.permission.INTERNET',
  'android.permission.POST_NOTIFICATIONS',
  'android.permission.SCHEDULE_EXACT_ALARM',
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
