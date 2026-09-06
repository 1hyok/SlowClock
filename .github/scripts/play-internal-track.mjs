import { execFile } from "node:child_process";
import { appendFile, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";

// Google Play Android Publisher v3 로 internal track 에만 게시한다 (#852).
//
// production·open·closed track 은 이 파일 어디에도 없다. 승격은 사람이 Play Console 에서만
// 한다는 게 이슈의 비범위이자 완료 조건이라, 자동 승격 경로를 «지금은 안 쓴다» 로 두지 않고
// 아예 만들지 않는다. release-play-internal-policy.test.mjs 가 그 부재를 고정한다.
export const INTERNAL_TRACK = "internal";
export const RELEASE_NOTES_LANGUAGE = "ko-KR";
// Play 가 language 당 허용하는 release note 길이. 넘기면 track update 가 400 으로 떨어진다.
export const MAX_RELEASE_NOTES_LENGTH = 500;

const API_ROOT = "https://androidpublisher.googleapis.com";

function firstLine(value) {
    return String(value ?? "").split("\n", 1)[0].slice(0, 500);
}

export function toVersionCode(rawValue) {
    const value = typeof rawValue === "number" ? String(rawValue) : String(rawValue ?? "").trim();
    if (!/^[1-9][0-9]*$/.test(value)) {
        throw new Error(`versionCode 가 10진 양의 정수가 아니다: ${firstLine(rawValue)}`);
    }
    return Number(value);
}

/**
 * 주어진 텍스트에서 이슈 번호만 남긴다. PR 섹션 검증은 호출자가 먼저 한다.
 */
export function parseIssueReferences(body) {
    const seen = new Set();
    for (const match of String(body ?? "").matchAll(/#([1-9][0-9]*)/g)) {
        seen.add(`#${match[1]}`);
    }
    return [...seen];
}

async function readValidatedReleaseMetadata(bodyFile) {
    if (!bodyFile) {
        throw new Error("RELEASE_PR_BODY_FILE 이 필요하다.");
    }
    const privateDirectory = await mkdtemp(join(tmpdir(), "slowclock-play-notes-"));
    try {
        const outputPath = join(privateDirectory, "distribution-notes.txt");
        const renderer = fileURLToPath(new URL("./render-distribution-release-notes.sh", import.meta.url));
        // 같은 renderer 가 PR 템플릿을 검증하고 코드·주석을 제외한다. 본문을 shell 코드로 넣지 않는다.
        await promisify(execFile)("bash", [renderer, outputPath], {
            env: { ...process.env, EVENT_NAME: "push", RELEASE_PR_BODY_FILE: resolve(bodyFile) },
        });
        const lines = (await readFile(outputPath, "utf8")).split("\n");
        const issuesStart = lines.indexOf("포함 이슈");
        const changesStart = lines.indexOf("변경 내용", issuesStart + 1);
        if (issuesStart < 0 || changesStart < 0) {
            throw new Error("배포 renderer의 릴리스 노트 형식을 확인할 수 없다.");
        }
        return {
            issues: parseIssueReferences(lines.slice(issuesStart + 1, changesStart).join("\n")),
            changes: lines.slice(changesStart + 1).filter((line) => line.trim()),
        };
    } finally {
        await rm(privateDirectory, { recursive: true, force: true });
    }
}

/**
 * Play Console 의 «What's new» 로 그대로 보이는 문구. source SHA 를 항상 먼저 적어 두어
 * Console 에서 본 릴리스가 어느 commit 인지 사후에 되짚을 수 있게 한다.
 */
export function renderInternalReleaseNotes({ sourceRef, sourceSha, versionCode, issues = [], changes = [] }) {
    const ref = String(sourceRef ?? "").replace(/^refs\/heads\//, "") || "unknown";
    const sha = String(sourceSha ?? "unknown");
    const header = `SlowClock 내부 테스트 ${versionCode}\n기준: ${ref} @ ${sha.slice(0, 7)}`;
    const body = issues.length > 0 ? `\n포함 이슈: ${issues.join(", ")}` : "";
    const notes = `${header}${body}${changes.length ? `\n\n변경 내용\n${changes.join("\n")}` : ""}`;
    const characters = Array.from(notes);
    if (characters.length <= MAX_RELEASE_NOTES_LENGTH) {
        return notes;
    }
    // 잘렸다는 사실이 Console 에서 보여야 한다 — 이슈 목록이 조용히 사라지면 릴리스 기록과
    // Console 표시가 어긋난 걸 아무도 눈치채지 못한다.
    return `${characters.slice(0, MAX_RELEASE_NOTES_LENGTH - 1).join("")}…`;
}

/**
 * Play 가 이미 알고 있는 가장 큰 versionCode. 업로드된 bundle 과 모든 track 의 release 를 함께
 * 본다 — bundle 목록만 보면 사람이 Console 에서 직접 올린 산출물을 놓친다.
 */
export function maxVersionCode(bundlesResponse, tracksResponse) {
    const codes = [];
    for (const bundle of bundlesResponse?.bundles ?? []) {
        codes.push(toVersionCode(bundle.versionCode));
    }
    for (const track of tracksResponse?.tracks ?? []) {
        for (const release of track.releases ?? []) {
            for (const code of release.versionCodes ?? []) {
                codes.push(toVersionCode(code));
            }
        }
    }
    return codes.length === 0 ? 0 : Math.max(...codes);
}

export function createPlayClient({ accessToken, packageName, fetchImpl = globalThis.fetch }) {
    if (!accessToken) {
        throw new Error("PLAY_ACCESS_TOKEN 이 비어 있다.");
    }
    if (!packageName) {
        throw new Error("PLAY_PACKAGE_NAME 이 비어 있다.");
    }

    const application = encodeURIComponent(packageName);
    const editsRoot = `${API_ROOT}/androidpublisher/v3/applications/${application}/edits`;
    const uploadRoot = `${API_ROOT}/upload/androidpublisher/v3/applications/${application}/edits`;

    async function request(method, url, { body, contentType } = {}) {
        const headers = { authorization: `Bearer ${accessToken}` };
        if (contentType) {
            headers["content-type"] = contentType;
        }
        const response = await fetchImpl(url, { method, headers, body });
        const text = typeof response.text === "function" ? await response.text() : "";
        if (!response.ok) {
            // 토큰은 header 로만 흐르므로 URL·본문을 그대로 적어도 자격이 새지 않는다.
            throw new Error(`Play API ${method} ${url} 실패 (${response.status}): ${firstLine(text)}`);
        }
        return text ? JSON.parse(text) : {};
    }

    return {
        packageName,
        insertEdit: async () => {
            const edit = await request("POST", editsRoot);
            if (!edit?.id) {
                throw new Error("Play API 가 edit id 를 돌려주지 않았다.");
            }
            return edit.id;
        },
        deleteEdit: (editId) => request("DELETE", `${editsRoot}/${encodeURIComponent(editId)}`),
        listBundles: (editId) => request("GET", `${editsRoot}/${encodeURIComponent(editId)}/bundles`),
        listTracks: (editId) => request("GET", `${editsRoot}/${encodeURIComponent(editId)}/tracks`),
        uploadBundle: (editId, bytes) =>
            request("POST", `${uploadRoot}/${encodeURIComponent(editId)}/bundles?uploadType=media`, {
                body: bytes,
                contentType: "application/octet-stream",
            }),
        updateTrack: (editId, track, payload) =>
            request(
                "PUT",
                `${editsRoot}/${encodeURIComponent(editId)}/tracks/${encodeURIComponent(track)}`,
                { body: JSON.stringify(payload), contentType: "application/json" },
            ),
        commitEdit: (editId) => request("POST", `${editsRoot}/${encodeURIComponent(editId)}:commit`),
    };
}

// edit 를 열어 둔 채 실패하면 Play 쪽에 미완료 edit 가 남고, 다음 실행이 같은 앱에서 충돌한다.
// 정리 실패 자체는 원인 오류를 덮지 않는다 — 그래서 여기서 삼키고 경고만 남긴다.
async function discardEdit(client, editId, log) {
    try {
        await client.deleteEdit(editId);
        log(`미완료 edit ${editId} 를 정리했다.`);
    } catch (error) {
        log(`::warning::미완료 edit ${editId} 정리 실패: ${error.message}`);
    }
}

export async function readLatestVersionCode(client, { log = console.log } = {}) {
    const editId = await client.insertEdit();
    try {
        const bundles = await client.listBundles(editId);
        const tracks = await client.listTracks(editId);
        return maxVersionCode(bundles, tracks);
    } finally {
        // 읽기 전용 조회다. commit 하지 않고 반드시 되돌린다.
        await discardEdit(client, editId, log);
    }
}

/**
 * edit 생성 → bundle 업로드 → internal track 갱신 → commit. 각 단계의 응답을 다음 단계의 전제로
 * 다시 확인하고, 어느 단계에서 실패하든 edit 를 정리한 뒤 원인을 그대로 올린다.
 */
export async function publishInternalBundle(
    client,
    { bundle, versionCode, releaseNotes, log = console.log },
) {
    const expected = toVersionCode(versionCode);
    if (!bundle || bundle.length === 0) {
        throw new Error("업로드할 AAB 가 비어 있다.");
    }

    const editId = await client.insertEdit();
    try {
        // 같은 versionCode 재업로드는 Play 도 거절하지만, 그 거절은 수십 MB 를 올린 뒤에 온다.
        // 업로드 전에 목록으로 먼저 막는다.
        const existing = maxVersionCodeSet(await client.listBundles(editId));
        if (existing.has(expected)) {
            throw new Error(`versionCode ${expected} 는 이미 Play 에 올라가 있다 — 재사용할 수 없다.`);
        }

        const uploaded = await client.uploadBundle(editId, bundle);
        const uploadedCode = toVersionCode(uploaded?.versionCode);
        if (uploadedCode !== expected) {
            throw new Error(
                `업로드된 versionCode ${uploadedCode} 가 빌드한 ${expected} 와 다르다.`,
            );
        }

        const track = await client.updateTrack(editId, INTERNAL_TRACK, {
            track: INTERNAL_TRACK,
            releases: [
                {
                    versionCodes: [String(expected)],
                    status: "completed",
                    releaseNotes: [{ language: RELEASE_NOTES_LANGUAGE, text: releaseNotes }],
                },
            ],
        });
        if (track?.track !== INTERNAL_TRACK) {
            throw new Error(`internal 이 아닌 track 응답을 받았다: ${firstLine(track?.track)}`);
        }
        const assigned = (track.releases ?? []).flatMap((release) => release.versionCodes ?? []);
        if (!assigned.map(String).includes(String(expected))) {
            throw new Error(`internal track 이 versionCode ${expected} 를 받지 않았다.`);
        }

        const commit = await client.commitEdit(editId);
        if (commit?.id !== editId) {
            throw new Error(`commit 응답의 edit id 가 다르다: ${firstLine(commit?.id)}`);
        }
        log(`internal track commit 완료 — edit ${editId}, versionCode ${expected}.`);
        return { editId, versionCode: expected, track: INTERNAL_TRACK };
    } catch (error) {
        await discardEdit(client, editId, log);
        throw error;
    }
}

function maxVersionCodeSet(bundlesResponse) {
    return new Set((bundlesResponse?.bundles ?? []).map((bundle) => toVersionCode(bundle.versionCode)));
}

async function writeOutput(entries) {
    const outputPath = process.env.GITHUB_OUTPUT;
    if (!outputPath) {
        throw new Error("GITHUB_OUTPUT 이 필요하다.");
    }
    const body = Object.entries(entries)
        .map(([key, value]) => `${key}=${value}\n`)
        .join("");
    await appendFile(outputPath, body, "utf8");
}

function clientFromEnvironment() {
    return createPlayClient({
        accessToken: process.env.PLAY_ACCESS_TOKEN,
        packageName: process.env.PLAY_PACKAGE_NAME,
    });
}

async function main(argv) {
    const command = argv[0];
    if (command === "latest") {
        const latest = await readLatestVersionCode(clientFromEnvironment());
        await writeOutput({ latest_version_code: latest });
        console.log(`Play 가 알고 있는 최대 versionCode: ${latest}.`);
        return;
    }
    if (command === "notes") {
        const outputFile = argv[1];
        if (!outputFile) {
            throw new Error("notes <출력 파일> 형식으로 호출한다.");
        }
        const bodyFile = process.env.RELEASE_PR_BODY_FILE;
        const metadata = await readValidatedReleaseMetadata(bodyFile);
        const notes = renderInternalReleaseNotes({
            sourceRef: process.env.SOURCE_REF,
            sourceSha: process.env.SOURCE_SHA,
            versionCode: process.env.SLOWCLOCK_VERSION_CODE,
            ...metadata,
        });
        await writeFile(outputFile, notes, "utf8");
        console.log(notes);
        return;
    }
    if (command === "publish") {
        const bundlePath = argv[1];
        const notesPath = argv[2];
        if (!bundlePath || !notesPath) {
            throw new Error("publish <AAB 경로> <release note 경로> 형식으로 호출한다.");
        }
        const result = await publishInternalBundle(clientFromEnvironment(), {
            bundle: await readFile(bundlePath),
            versionCode: process.env.SLOWCLOCK_VERSION_CODE,
            releaseNotes: await readFile(notesPath, "utf8"),
        });
        await writeOutput({ edit_id: result.editId, version_code: result.versionCode });
        return;
    }
    throw new Error(`알 수 없는 명령: ${firstLine(command)}`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
    main(process.argv.slice(2)).catch((error) => {
        console.error(`::error::${error.message}`);
        process.exitCode = 1;
    });
}
