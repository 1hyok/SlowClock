import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import test from "node:test";

// 출시 런북(docs/play-release.md)의 답안을 그대로 Play Console 에 옮겨 적는다. 그래서 이 문서가
// manifest·빌드 파일과 어긋나면 콘솔 선언이 실물과 달라지고, 선언 누락으로 릴리스가 반려된다.
// #122 에서 포그라운드 서비스 유형을 바꾸고 #113 에서 R8 을 켰을 때 이 문서만 옛 값으로 남아
// 있었다(#138). 사람이 두 곳을 맞춰 주기를 기대하는 대신 여기서 대조한다.
const runbook = await readFile(new URL("../../docs/play-release.md", import.meta.url), "utf8");
const manifest = await readFile(
    new URL("../../app/src/main/AndroidManifest.xml", import.meta.url),
    "utf8",
);
const appBuild = await readFile(new URL("../../app/build.gradle.kts", import.meta.url), "utf8");

/** manifest 가 실제로 요구하는 권한. tools:node="remove" 로 빼는 것은 제외한다. */
function declaredPermissions(source) {
    return [...source.matchAll(/<uses-permission\b([^>]*)>/g)]
        .filter((match) => !/tools:node\s*=\s*"remove"/.test(match[1]))
        .map((match) => /android:name\s*=\s*"([^"]+)"/.exec(match[1])?.[1])
        .filter(Boolean)
        .map((name) => name.replace(/^android\.permission\./, ""));
}

/**
 * Play Console 이 따로 선언을 받는 권한. 나머지(INTERNET·VIBRATE 등)는 양식에 적을 자리가 없다.
 *
 * 포그라운드 서비스 유형 권한은 접두어로 잡는다 — 유형이 늘면 그것도 선언 대상이다.
 */
function needsConsoleDeclaration(permission) {
    return (
        permission.startsWith("FOREGROUND_SERVICE_") ||
        ["SCHEDULE_EXACT_ALARM", "USE_EXACT_ALARM", "USE_FULL_SCREEN_INTENT"].includes(permission)
    );
}

test("런북의 권한 선언 답안이 콘솔 선언 대상 권한을 전부 담는다", () => {
    // 콘솔 「앱 액세스 권한」 선언에서 빠진 권한은 심사에서 되돌아온다.
    const permissions = declaredPermissions(manifest).filter(needsConsoleDeclaration);
    assert.ok(permissions.length > 0, "manifest 에서 선언 대상 권한을 읽지 못했다");

    for (const permission of permissions) {
        assert.ok(
            runbook.includes(permission),
            `docs/play-release.md 가 ${permission} 을 적지 않았다. manifest 를 고쳤으면 런북도 고친다.`,
        );
    }
});

test("런북이 적은 포그라운드 서비스 유형이 manifest 와 같다", () => {
    const type = /android:foregroundServiceType="([^"]+)"/.exec(manifest)?.[1];
    assert.ok(type, "manifest 에서 포그라운드 서비스 유형을 읽지 못했다");

    assert.match(runbook, new RegExp(`\`${type}\``));
    // 옛 값이 남아 있으면 그 문장을 그대로 콘솔에 옮겨 적게 된다.
    assert.doesNotMatch(
        runbook,
        /유형 선언 대상이 아니다/,
        "포그라운드 서비스 유형을 쓰는 앱은 콘솔 선언 대상이다",
    );
});

test("런북의 R8 설명이 빌드 파일과 같다", () => {
    const minifyEnabled = /isMinifyEnabled = true/.test(appBuild);
    assert.ok(minifyEnabled, "릴리스에서 R8 이 꺼졌다면 런북 문장부터 다시 본다");

    assert.doesNotMatch(
        runbook,
        /R8 을 켜지 않는다|isMinifyEnabled = false/,
        "R8 을 켠 뒤에도 런북이 끈 것으로 적혀 있다",
    );
});

test("스토어 문안이 앱에 없는 기능을 약속하지 않는다", async () => {
    // 가족 그룹을 만드는 화면은 앱에 없다. familyGroups 는 계정 삭제 때 정리하는 용도로만 남아
    // 있고, 앱에 있는 것은 공유 코드를 입력해 남의 일정을 보는 경로까지다.
    const listing = await readFile(new URL("../../docs/play/listing.md", import.meta.url), "utf8");

    assert.doesNotMatch(listing, /가족 그룹/);
    // 보호자 푸시는 결제 미설정으로 운영에서 나가지 않는다(#102). 살아나기 전에는 약속하지 않는다.
    assert.doesNotMatch(listing, /알림을 받습니다/);
});
