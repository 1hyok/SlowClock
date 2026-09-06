import assert from 'node:assert/strict';
import test from 'node:test';

import { inspectManifest } from './verify-android-manifest.mjs';

const manifest = ({ permissions = '', components = '', cleartext = 'false' } = {}) => `
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
  ${permissions}
  <application android:usesCleartextTraffic="${cleartext}">
    ${components}
  </application>
</manifest>`;

test('current allowed permissions and protected exported components pass', () => {
  const violations = inspectManifest(
    manifest({
      permissions: `
        <uses-permission android:name="android.permission.INTERNET" />
        <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
        <uses-permission android:name="com.example.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" />`,
      components: `
        <activity android:name="com.example.slowclock.MainActivity" android:exported="true" />
        <service android:name="example.SystemService" android:exported="true"
                 android:permission="android.permission.BIND_JOB_SERVICE" />
        <provider android:name="example.PrivateProvider" android:exported="false" />`,
    }),
  );

  assert.deepEqual(violations, []);
});

test('new permissions fail closed until reviewed and allowlisted', () => {
  const violations = inspectManifest(
    manifest({
      permissions: '<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />',
    }),
  );

  assert.deepEqual(violations, [
    'permission is not allowlisted: android.permission.ACCESS_FINE_LOCATION',
  ]);
});

test('cleartext traffic must stay explicitly disabled', () => {
  assert.deepEqual(inspectManifest(manifest({ cleartext: 'true' })), [
    'application must explicitly set android:usesCleartextTraffic="false"',
  ]);
  assert.deepEqual(inspectManifest('<manifest><application /></manifest>'), [
    'application must explicitly set android:usesCleartextTraffic="false"',
  ]);
});

test('unprotected exports and every exported provider fail closed', () => {
  const violations = inspectManifest(
    manifest({
      components: `
        <receiver android:name="example.OpenReceiver" android:exported="true" />
        <provider android:name="example.OpenProvider" android:exported="true"
                  android:permission="example.PRIVATE" />`,
    }),
  );

  assert.deepEqual(violations, [
    'unprotected exported receiver is not allowlisted: example.OpenReceiver',
    'exported provider is forbidden: example.OpenProvider',
  ]);
});
