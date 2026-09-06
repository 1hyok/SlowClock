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

/** 공유 코드를 받아 감시자로 등록한 가족. 앱에서 「보호자」 자리다. */
const OTHER = "uid-other";

/** 코드를 받은 적 없는 제3자. 로그인만 한 계정이 어디까지 닿는지 이 계정으로 잰다(#174). */
const STRANGER = "uid-stranger";

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
        // 코드를 받은 적 없는 사람에게도 공유 일정이 열리는지 보려면, 감시자가 붙지 않은
        // 다른 코드의 공유 일정이 하나 있어야 한다.
        await setDoc(doc(db, "schedules", "other-shared-1"), {
            id: "other-shared-1",
            userId: OTHER,
            title: "남의 공유 일정",
            completed: false,
            sharedCode: "ZZZ999",
        });
        await setDoc(doc(db, "publicProfiles", OWNER), { id: OWNER, name: "느린 사용자" });
        await setDoc(doc(db, "publicProfiles", OTHER), { id: OTHER, name: "다른 사용자" });
        await setDoc(doc(db, "shareCodes", SHARE_CODE), { userId: OWNER });
        await setDoc(doc(db, "shareCodes", "ZZZ999"), { userId: OTHER });
        // 가족이 코드를 입력해 감시자로 등록한 상태. 이 문서가 공유 읽기 권한의 근거다(#174).
        await setDoc(doc(db, "shareCodeWatchers", SHARE_CODE, "tokens", OTHER), {
            userId: OTHER,
            fcmToken: "token-other",
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

function stranger() {
    return testEnv.authenticatedContext(STRANGER).firestore();
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

    it("남의 사용자 문서는 읽지 못한다", async () => {
        // 이 문서에는 이메일과 FCM 토큰이 함께 있다. 이름은 publicProfiles 로 따로 낸다(#93).
        await assertFails(getDoc(doc(other(), "users", OWNER)));
    });

    it("남의 사용자 문서를 질의로도 못 읽는다", async () => {
        const db = other();
        await assertFails(getDocs(query(collection(db, "users"), where("id", "in", [OWNER]))));
    });
});

describe("publicProfiles", () => {
    it("공유 일정 소유자 이름을 문서 하나씩 읽는다", async () => {
        // UserRepository.getUserNames 가 쓰는 경로다. 소유자 uid 는 읽을 수 있는 공유 일정에서 나온다.
        await assertSucceeds(getDoc(doc(other(), "publicProfiles", OWNER)));
    });

    it("목록으로는 훑지 못한다", async () => {
        // 목록을 열어 두면 문서 이름을 몰라도 컬렉션을 통째로 가져갈 수 있다(#174).
        const db = other();
        await assertFails(getDocs(collection(db, "publicProfiles")));
        await assertFails(getDocs(query(collection(db, "publicProfiles"), where("id", "in", [OWNER]))));
    });

    it("본인 프로필만 쓴다", async () => {
        const db = owner();
        await assertSucceeds(setDoc(doc(db, "publicProfiles", OWNER), { id: OWNER, name: "새 이름" }));
        await assertFails(setDoc(doc(db, "publicProfiles", OTHER), { id: OTHER, name: "가로채기" }));
    });

    it("이름 밖의 값은 넣지 못한다", async () => {
        // 이메일이나 토큰이 공개 프로필로 새어 나가지 않게 필드를 묶는다.
        const db = owner();
        await assertFails(
            setDoc(doc(db, "publicProfiles", OWNER), {
                id: OWNER,
                name: "느린 사용자",
                email: "owner@example.com",
            }),
        );
    });

    it("공유 코드는 공개 프로필에 담지 못한다", async () => {
        // 코드는 사람을 찾는 열쇠라 이름 옆에 둘 값이 아니다(#174).
        await assertFails(
            setDoc(doc(owner(), "publicProfiles", OWNER), {
                id: OWNER,
                name: "느린 사용자",
                shareCode: SHARE_CODE,
            }),
        );
    });

    it("로그인하지 않으면 읽지 못한다", async () => {
        await assertFails(getDoc(doc(anonymous(), "publicProfiles", OWNER)));
    });
});

describe("shareCodes", () => {
    it("비어 있는 코드는 본인 것으로 만들 수 있다", async () => {
        // UserRepository.generateUniqueShareCode 가 쓰는 경로다.
        await assertSucceeds(setDoc(doc(owner(), "shareCodes", "NEW123"), { userId: OWNER }));
    });

    it("이미 임자가 있는 코드는 가져가지 못한다", async () => {
        // 중복 확인과 저장이 한 번의 만들기로 합쳐져 그 사이의 틈이 없다(#174).
        await assertFails(setDoc(doc(other(), "shareCodes", SHARE_CODE), { userId: OTHER }));
    });

    it("남의 이름으로 코드를 만들지 못한다", async () => {
        await assertFails(setDoc(doc(other(), "shareCodes", "NEW456"), { userId: OWNER }));
    });

    it("코드 등록부는 읽지 못한다", async () => {
        // 코드 목록은 그 자체가 열쇠 꾸러미다. 누구에게도 열지 않는다.
        const db = other();
        await assertFails(getDoc(doc(db, "shareCodes", SHARE_CODE)));
        await assertFails(getDocs(collection(db, "shareCodes")));
    });

    it("본인 코드만 반납한다", async () => {
        await assertSucceeds(deleteDoc(doc(owner(), "shareCodes", SHARE_CODE)));
        await assertFails(deleteDoc(doc(other(), "shareCodes", SHARE_CODE)));
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

    it("코드를 등록한 가족은 공유 일정을 조회하고 완료만 바꾼다", async () => {
        const db = other();
        await assertSucceeds(getDocs(query(collection(db, "schedules"), where("sharedCode", "==", SHARE_CODE))));
        await assertSucceeds(updateDoc(doc(db, "schedules", "shared-1"), { completed: true }));
    });

    it("공유 반복 일정은 회차별 완료 기록도 바꾼다", async () => {
        // 반복 일정은 completed 하나로 담을 수 없다. 회차마다 따로 남기므로 completedDates 도
        // 「완료만 바꾼다」 범위에 든다(#130).
        await assertSucceeds(
            updateDoc(doc(other(), "schedules", "shared-1"), { completedDates: ["2026-09-06"] }),
        );
    });

    it("공유 일정이라도 제목은 바꾸지 못한다", async () => {
        await assertFails(updateDoc(doc(other(), "schedules", "shared-1"), { title: "바뀐 제목" }));
    });

    it("코드를 받지 않은 제3자는 공유 일정을 읽지 못한다", async () => {
        // 「공유로 표시됐는가」 만 보면 로그인만 한 계정에게도 열린다. 병원·복약 같은 제목이
        // 그대로 나가므로 관계를 확인한다(#174).
        const db = stranger();
        await assertFails(getDoc(doc(db, "schedules", "shared-1")));
        await assertFails(getDocs(query(collection(db, "schedules"), where("sharedCode", "==", SHARE_CODE))));
    });

    it("코드를 받지 않은 제3자는 완료 상태를 바꾸지 못한다", async () => {
        // 읽기보다 이쪽이 더 아프다. 남이 완료로 바꿔 두면 어르신이 약을 이미 먹은 줄로 안다.
        const db = stranger();
        await assertFails(updateDoc(doc(db, "schedules", "shared-1"), { completed: true }));
        await assertFails(updateDoc(doc(db, "schedules", "shared-1"), { completedDates: ["2026-09-06"] }));
    });

    it("공유로 표시된 일정을 통째로 훑지 못한다", async () => {
        // 코드를 하나 받았다고 다른 집 일정까지 딸려 오면 안 된다.
        const db = other();
        await assertFails(getDocs(query(collection(db, "schedules"), where("sharedCode", ">", ""))));
        await assertFails(getDocs(collection(db, "schedules")));
    });

    it("한 코드에 공유 일정이 많아도 목록 조회가 통과한다", async () => {
        // 공유 읽기 규칙이 문서마다 감시자 등록을 확인한다. 규칙의 문서 접근 횟수에는 한도가
        // 있으므로, 일정이 늘어도 조회가 막히지 않는지 실제로 재 둔다(#174).
        await testEnv.withSecurityRulesDisabled(async (context) => {
            const db = context.firestore();
            for (let i = 0; i < 40; i += 1) {
                await setDoc(doc(db, "schedules", `bulk-${i}`), {
                    id: `bulk-${i}`,
                    userId: OWNER,
                    title: `일정 ${i}`,
                    completed: false,
                    sharedCode: SHARE_CODE,
                });
            }
        });
        const snapshot = await assertSucceeds(
            getDocs(query(collection(other(), "schedules"), where("sharedCode", "==", SHARE_CODE))),
        );
        assert.equal(snapshot.size, 41);
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
        const db = stranger();
        const ref = doc(db, "shareCodeWatchers", SHARE_CODE, "tokens", STRANGER);
        await assertSucceeds(setDoc(ref, { userId: STRANGER, fcmToken: "token-stranger" }));
        await assertSucceeds(deleteDoc(ref));
    });

    it("남의 토큰 자리에는 쓰지 못한다", async () => {
        const ref = doc(other(), "shareCodeWatchers", SHARE_CODE, "tokens", OWNER);
        await assertFails(setDoc(ref, { fcmToken: "가짜" }));
    });

    it("FCM 토큰이 없어도 등록된다", async () => {
        // 이 문서가 읽기 권한의 근거라, 토큰을 못 받았다고 만들지 못하면 가족 일정이 통째로
        // 안 보인다. 토큰은 알림을 보내기 위한 값일 뿐이다(#174).
        const ref = doc(stranger(), "shareCodeWatchers", SHARE_CODE, "tokens", STRANGER);
        await assertSucceeds(setDoc(ref, { userId: STRANGER }));
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
