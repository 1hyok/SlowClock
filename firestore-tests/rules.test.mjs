// Firestore 보안 규칙 회귀 테스트.
//
// 두 가지를 잠근다.
//   1. 앱이 실제로 하는 접근이 통과하는가 (규칙을 좁히다 기능을 깨지 않게)
//   2. 남의 데이터 접근이 막히는가
//
// 실행: firestore-tests 에서 `npm test`. firebase 에뮬레이터가 규칙 파일을 읽어 올린다.
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, describe, it } from "node:test";
import { fileURLToPath } from "node:url";

import {
    assertFails,
    assertSucceeds,
    initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
    collection,
    deleteDoc,
    doc,
    getDoc,
    getDocs,
    query,
    setDoc,
    updateDoc,
    where,
} from "firebase/firestore";

const OWNER = "uid-owner";
const OTHER = "uid-other";
const SHARE_CODE = "ABC123";

let testEnv;

before(async () => {
    testEnv = await initializeTestEnvironment({
        projectId: "slowclock-rules-test",
        firestore: {
            rules: await readFile(fileURLToPath(new URL("../firestore.rules", import.meta.url)), "utf8"),
            host: "127.0.0.1",
            port: 8080,
        },
    });
});

after(async () => {
    await testEnv?.cleanup();
});

beforeEach(async () => {
    await testEnv.clearFirestore();
    // 규칙을 끄고 넣는 초기 데이터. 앱이 로그인 뒤 갖게 되는 상태다.
    await testEnv.withSecurityRulesDisabled(async (context) => {
        const db = context.firestore();
        await setDoc(doc(db, "users", OWNER), {
            id: OWNER,
            name: "느린 사용자",
            email: "owner@example.com",
            shareCode: SHARE_CODE,
            fcmToken: "token-owner",
        });
        await setDoc(doc(db, "users", OTHER), {
            id: OTHER,
            name: "다른 사용자",
            email: "other@example.com",
            shareCode: "ZZZ999",
            fcmToken: "token-other",
        });
        await setDoc(doc(db, "schedules", "own-1"), {
            id: "own-1",
            userId: OWNER,
            title: "약 먹기",
            completed: false,
            sharedCode: "",
        });
        await setDoc(doc(db, "schedules", "shared-1"), {
            id: "shared-1",
            userId: OWNER,
            title: "병원 가기",
            completed: false,
            sharedCode: SHARE_CODE,
        });
        await setDoc(doc(db, "schedules", "other-1"), {
            id: "other-1",
            userId: OTHER,
            title: "남의 일정",
            completed: false,
            sharedCode: "",
        });
        await setDoc(doc(db, "notifications", "n-own"), { userId: OWNER, message: "알림" });
        await setDoc(doc(db, "notifications", "n-other"), { userId: OTHER, message: "남의 알림" });
        await setDoc(doc(db, "scheduleRecommendations", "r-1"), { title: "산책하기" });
    });
});

function owner() {
    return testEnv.authenticatedContext(OWNER).firestore();
}

function other() {
    return testEnv.authenticatedContext(OTHER).firestore();
}

function anonymous() {
    return testEnv.unauthenticatedContext().firestore();
}

describe("users", () => {
    it("본인 문서를 읽고 쓴다", async () => {
        const db = owner();
        await assertSucceeds(getDoc(doc(db, "users", OWNER)));
        await assertSucceeds(updateDoc(doc(db, "users", OWNER), { fcmToken: "새 토큰" }));
    });

    it("남의 문서에는 쓰지 못한다", async () => {
        await assertFails(updateDoc(doc(other(), "users", OWNER), { name: "바뀐 이름" }));
    });

    it("로그인하지 않으면 아무것도 못 읽는다", async () => {
        await assertFails(getDoc(doc(anonymous(), "users", OWNER)));
    });

    it("공유 코드 중복 확인 질의가 통과한다", async () => {
        // UserRepository.generateUniqueShareCode 가 쓰는 질의다.
        const db = owner();
        await assertSucceeds(getDocs(query(collection(db, "users"), where("shareCode", "==", "NEW123"))));
    });

    it("지금은 로그인한 누구나 남의 사용자 문서를 읽는다", async () => {
        // 배포본 규칙의 users 읽기 허용(allow read: if request.auth != null)이 그대로 통과시킨다. 이름뿐 아니라
        // 이메일·FCM 토큰까지 함께 노출되므로 좁혀야 한다. 좁히는 작업은 공개 프로필 분리가
        // 필요해 별도 이슈로 다룬다. 그때 이 테스트는 assertFails 로 뒤집는다.
        await assertSucceeds(getDoc(doc(other(), "users", OWNER)));
    });

    it("공유 일정 소유자 이름 조회 질의가 통과한다", async () => {
        // UserRepository.getUserNames 가 쓰는 질의다.
        const db = other();
        await assertSucceeds(getDocs(query(collection(db, "users"), where("id", "in", [OWNER]))));
    });
});

describe("schedules", () => {
    it("본인 일정을 날짜로 조회하고 만들고 지운다", async () => {
        const db = owner();
        await assertSucceeds(getDocs(query(collection(db, "schedules"), where("userId", "==", OWNER))));
        await assertSucceeds(
            setDoc(doc(db, "schedules", "new-1"), { id: "new-1", userId: OWNER, title: "새 일정", sharedCode: "" }),
        );
        await assertSucceeds(deleteDoc(doc(db, "schedules", "own-1")));
    });

    it("남의 비공개 일정은 읽지 못한다", async () => {
        await assertFails(getDoc(doc(other(), "schedules", "own-1")));
    });

    it("남의 일정을 자기 것으로 만들지 못한다", async () => {
        await assertFails(
            setDoc(doc(other(), "schedules", "steal-1"), { id: "steal-1", userId: OWNER, title: "가로채기" }),
        );
    });

    it("공유 코드로 조회한 일정은 읽고 완료만 바꾼다", async () => {
        const db = other();
        await assertSucceeds(getDocs(query(collection(db, "schedules"), where("sharedCode", "==", SHARE_CODE))));
        await assertSucceeds(updateDoc(doc(db, "schedules", "shared-1"), { completed: true }));
    });

    it("공유 일정이라도 제목은 바꾸지 못한다", async () => {
        await assertFails(updateDoc(doc(other(), "schedules", "shared-1"), { title: "바뀐 제목" }));
    });
});

describe("notifications", () => {
    it("본인 알림을 조회하고 지운다", async () => {
        // NotificationRepository.deleteAllNotificationsOf 가 쓰는 경로다.
        const db = owner();
        await assertSucceeds(getDocs(query(collection(db, "notifications"), where("userId", "==", OWNER))));
        await assertSucceeds(deleteDoc(doc(db, "notifications", "n-own")));
    });

    it("남의 알림은 읽지도 지우지도 못한다", async () => {
        const db = other();
        await assertFails(getDoc(doc(db, "notifications", "n-own")));
        await assertFails(deleteDoc(doc(db, "notifications", "n-own")));
    });
});

describe("shareCodeWatchers", () => {
    it("자기 토큰만 등록하고 지운다", async () => {
        // UserRepository.registerShareCodeWatcher / unregisterShareCodeWatcher 가 쓰는 경로다.
        const db = other();
        const ref = doc(db, "shareCodeWatchers", SHARE_CODE, "tokens", OTHER);
        await assertSucceeds(setDoc(ref, { fcmToken: "token-other" }));
        await assertSucceeds(deleteDoc(ref));
    });

    it("남의 토큰 자리에는 쓰지 못한다", async () => {
        const ref = doc(other(), "shareCodeWatchers", SHARE_CODE, "tokens", OWNER);
        await assertFails(setDoc(ref, { fcmToken: "가짜" }));
    });
});

describe("scheduleRecommendations", () => {
    it("로그인한 사용자는 읽고, 아무도 쓰지 못한다", async () => {
        const db = owner();
        await assertSucceeds(getDoc(doc(db, "scheduleRecommendations", "r-1")));
        await assertFails(setDoc(doc(db, "scheduleRecommendations", "r-2"), { title: "가짜 추천" }));
    });
});

describe("선언되지 않은 컬렉션", () => {
    it("규칙에 없는 컬렉션은 기본 거부다", async () => {
        const db = owner();
        await assertFails(getDoc(doc(db, "unknownCollection", "x")));
        await assertFails(setDoc(doc(db, "unknownCollection", "x"), { a: 1 }));
    });
});

// 이 파일이 규칙 파일을 실제로 읽었는지 확인한다. 경로가 어긋나면 위 테스트가 모두 통과해 버린다.
describe("테스트 자체 점검", () => {
    it("규칙 파일이 비어 있지 않다", async () => {
        const rules = await readFile(fileURLToPath(new URL("../firestore.rules", import.meta.url)), "utf8");
        assert.match(rules, /service cloud\.firestore/);
    });
});
