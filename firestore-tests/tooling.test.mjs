// 규칙 테스트가 쓰는 CLI(firebase-tools) 쪽 전이 의존성을 보안 패치 판으로 고정했다(#225).
// 고정한 판이 CLI 가 실제로 부르는 진입점과 API 를 그대로 주는지 여기서 잠근다.
// 에뮬레이터가 필요 없는 검사라 규칙 테스트와 같은 실행에 얹어 둔다.
//
// 모듈은 루트가 아니라 소비하는 패키지의 자리에서 고른다. 루트에 호이스팅된 사본을
// 보면 소비자 밑에 중첩된 취약 사본이 가려진다.
import assert from "node:assert/strict";
import { createRequire } from "node:module";
import { test } from "node:test";

const require = createRequire(import.meta.url);
const fromFirebaseTools = createRequire(require.resolve("firebase-tools/package.json"));
const fromGaxios = createRequire(fromFirebaseTools.resolve("gaxios/package.json"));
const fromExpress = createRequire(fromFirebaseTools.resolve("express/package.json"));
const fromPubSub = createRequire(fromFirebaseTools.resolve("@google-cloud/pubsub/package.json"));

/**
 * firebase-tools 의 `auth:import` 이 부르는 진입점. 5.x 에서 7.x 로 올렸다.
 * 6.0.0 이 `csv-parse/lib/sync` 경로와 옵션 이름을 바꿨으므로 진입점째로 확인한다.
 * https://github.com/advisories/GHSA-8cw4-87c7-c6xx
 */
test("csv-parse 의 CommonJS 진입점이 그대로 파싱한다", async () => {
    const { parse } = fromFirebaseTools("csv-parse");
    const parser = parse("uid,email\nuid-owner,owner@example.com\n");
    const records = [];
    for await (const record of parser) {
        records.push(record);
    }
    assert.deepEqual(records, [
        ["uid", "email"],
        ["uid-owner", "owner@example.com"],
    ]);
});

/**
 * 에뮬레이터 허브가 얹혀 있는 express 의 쿼리 파서. 6.16.0 미만은 대괄호 키에
 * 쉼표가 섞이면 배열 한도를 그냥 넘겼다.
 * https://github.com/advisories/GHSA-x5fp-wj9c-mxmx
 */
test("qs 가 대괄호 키의 배열 한도를 지킨다", () => {
    const qs = fromExpress("qs");
    assert.throws(() => qs.parse("a[]=1,2,3,4", {
        comma: true,
        arrayLimit: 3,
        throwOnLimitExceeded: true,
    }), RangeError);
});

/**
 * gaxios 가 멀티파트 경계값에 쓰는 uuid. 11.1.1 미만은 작은 버퍼에 잘린 값을
 * 조용히 썼다. https://github.com/advisories/GHSA-w5hq-g745-h8pq
 */
test("uuid 가 출력 버퍼 경계를 검사한다", () => {
    const { v4, v5 } = fromGaxios("uuid");
    assert.throws(() => v5("slowclock", v5.DNS, new Uint8Array(8)), RangeError);
    assert.equal(typeof v4(), "string");
});

/**
 * @google-cloud/pubsub 은 @opentelemetry/core `^1.30.1` 을 적었지만 2.8.0 에서도
 * 로드된다. firebase-tools 자신의 overrides 도 같은 판을 박는다.
 * https://github.com/advisories/GHSA-8988-4f7v-96qf
 */
test("pubsub 모듈이 OpenTelemetry 2.x 위에서 로드된다", () => {
    const { PubSub } = fromFirebaseTools("@google-cloud/pubsub");
    assert.equal(typeof PubSub, "function");
    assert.match(fromPubSub("@opentelemetry/core/package.json").version, /^2\./);
});
