import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { chmod, copyFile, mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";

const scripts = dirname(fileURLToPath(import.meta.url));
const bundleMapping = "BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map";
const mapping = "com.example.Original -> a:\n    java.lang.String title -> a\n";

async function temporaryDirectory(t) {
    const directory = await mkdtemp(join(tmpdir(), "slowclock-release-verification-"));
    t.after(() => rm(directory, { recursive: true, force: true }));
    return directory;
}

async function executable(path, source) {
    await writeFile(path, source);
    await chmod(path, 0o755);
}

async function bundleFixture(t, { external = mapping, embedded = mapping } = {}) {
    const directory = await temporaryDirectory(t);
    for (const path of ["scripts", "bin", "app/build/outputs/bundle/release", "app/build/outputs/mapping/release"]) {
        await mkdir(join(directory, path), { recursive: true });
    }
    for (const script of ["verify-play-release-bundle.sh", "jarsigner-verification-policy.sh"]) {
        await copyFile(join(scripts, "../../scripts", script), join(directory, "scripts", script));
    }
    await executable(join(directory, "bin/jarsigner"), '#!/usr/bin/env bash\nprintf "jar verified.\\n"\n');
    await executable(join(directory, "bin/keytool"), '#!/usr/bin/env bash\nprintf "SHA256: AA:BB\\n"\n');
    if (external !== null) {
        await writeFile(join(directory, "app/build/outputs/mapping/release/mapping.txt"), external);
    }
    const entries = Object.fromEntries([
        "BundleConfig.pb", "base/manifest/AndroidManifest.xml", "base/resources.pb", "base/dex/classes.dex",
    ].map((name) => [name, "fixture"]));
    if (embedded !== null) entries[bundleMapping] = embedded;
    const archive = spawnSync("python3", ["-c", [
        "import json, sys, zipfile",
        "with zipfile.ZipFile(sys.argv[1], 'w') as archive:",
        "    for name, value in json.load(sys.stdin).items(): archive.writestr(name, value)",
    ].join("\n"), join(directory, "app/build/outputs/bundle/release/app-release.aab")], {
        input: JSON.stringify(entries), encoding: "utf8",
    });
    assert.equal(archive.status, 0, archive.stderr);
    return spawnSync("bash", [join(directory, "scripts/verify-play-release-bundle.sh"), "--skip-build"], {
        env: { ...process.env, PATH: `${join(directory, "bin")}:${process.env.PATH}` }, encoding: "utf8",
    });
}

test("AAB verifier accepts a matching embedded and external mapping", async (t) => {
    const result = await bundleFixture(t);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /AAB 내 파일과 일치/);
});

for (const external of [null, ""]) {
    test(`AAB verifier rejects ${external === null ? "missing" : "empty"} external mapping`, async (t) => {
        const result = await bundleFixture(t, { external });
        assert.notEqual(result.status, 0);
        assert.match(result.stderr, /R8 mapping을 찾을 수 없거나 비어/);
    });
}

test("AAB verifier rejects an absent embedded mapping", async (t) => {
    const result = await bundleFixture(t, { embedded: null });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /AAB 필수 항목.*proguard\.map/);
});

test("AAB verifier rejects a stale external mapping even when both files are nonempty", async (t) => {
    const result = await bundleFixture(t, { external: `${mapping}# other build\n` });
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /일치하지 않습니다/);
});

function getter(name, attributes = "", parameters = "") {
    return `<method name="${name}" visibility="public" static="false" return="java.lang.String" ${attributes}>${parameters}</method>`;
}

const schedule = `<class name="Schedule">${getter("getTitle")}${getter("getStartTime")}</class>`;
const user = `<class name="User">${getter("getShareCode")}${getter("getFcmToken")}</class>`;
const profile = '<class name="PublicProfile"/>';
const models = (classes) => `<package name="com.example.slowclock.data.model">${classes}</package>`;
const mainKey = '<package name="com.example.slowclock.navigation"><class name="MainKey"/></package>';
const validXml = `<api>${models(schedule + user + profile)}${mainKey}</api>`;

async function dexFixture(t, xmlFiles, exitCode = 0) {
    const directory = await temporaryDirectory(t);
    const dexdump = join(directory, "dexdump");
    await executable(dexdump, [
        "#!/usr/bin/env python3",
        "import pathlib, sys",
        "assert sys.argv[1:3] == ['-l', 'xml']",
        `if ${exitCode}: sys.exit(${exitCode})`,
        "sys.stdout.buffer.write(pathlib.Path(sys.argv[3] + '.xml').read_bytes())",
    ].join("\n"));
    const files = [];
    for (const [index, xml] of xmlFiles.entries()) {
        const path = join(directory, `classes${index}.dex`);
        // 모든 문자열은 남아 있다. XML 정의만 지운 경우를 문자열 검색은 놓친다.
        await writeFile(path, "Lcom/example/slowclock/data/model/Schedule; getTitle getStartTime getShareCode getFcmToken");
        await writeFile(`${path}.xml`, xml);
        files.push(path);
    }
    return spawnSync("python3", [join(scripts, "verify-dex-reflection.py"), ...files], {
        env: { ...process.env, DEXDUMP: dexdump }, encoding: "utf8",
    });
}

test("DEX verifier accepts required definitions distributed across DEX files", async (t) => {
    const result = await dexFixture(t, [`<api>${models(schedule + user + profile)}</api>`, `<api>${mainKey}</api>`]);
    assert.equal(result.status, 0, result.stderr);
    assert.match(result.stdout, /2 DEX file/);
});

test("DEX verifier rejects a method name present only on an unrelated class", async (t) => {
    const xml = validXml.replace(getter("getTitle"), "").replace("</api>", `<package name="unrelated"><class name="Example">${getter("getTitle")}</class></package></api>`);
    const result = await dexFixture(t, [xml]);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /Schedule\.getTitle\(\)/);
});

test("DEX verifier rejects a referenced class with no class definition", async (t) => {
    const result = await dexFixture(t, [validXml.replace(profile, '<reference name="PublicProfile"/>')]);
    assert.notEqual(result.status, 0);
    assert.match(result.stderr, /class definition is missing.*PublicProfile/);
});

for (const [label, replacement] of [
    ["private", getter("getTitle").replace('visibility="public"', 'visibility="private"')],
    ["static", getter("getTitle").replace('static="false"', 'static="true"')],
    ["parameterized", getter("getTitle", "", '<parameter name="value" type="int"/>')],
    ["void", getter("getTitle").replace('return="java.lang.String"', 'return="void"')],
]) {
    test(`DEX verifier rejects a ${label} method in place of a getter`, async (t) => {
        const result = await dexFixture(t, [validXml.replace(getter("getTitle"), replacement)]);
        assert.notEqual(result.status, 0);
        assert.match(result.stderr, /Schedule\.getTitle\(\)/);
    });
}

test("DEX verifier fails closed on tool errors and invalid XML", async (t) => {
    const toolFailure = await dexFixture(t, [validXml], 1);
    assert.notEqual(toolFailure.status, 0);
    const invalidXml = await dexFixture(t, ["not XML"]);
    assert.notEqual(invalidXml.status, 0);
});

for (const exitCode of [0, 1]) {
    test(`APK verifier preserves apksigner ${exitCode === 0 ? "success" : "signature failure"}`, async (t) => {
        const directory = await temporaryDirectory(t);
        const toolDirectory = join(directory, "sdk/build-tools/36.1.0");
        await mkdir(toolDirectory, { recursive: true });
        const callPath = join(directory, "arguments.json");
        await executable(join(toolDirectory, "apksigner"), [
            "#!/usr/bin/env python3", "import json, os, pathlib, sys",
            "pathlib.Path(os.environ['APK_TEST_CALL_PATH']).write_text(json.dumps(sys.argv[1:]))",
            `sys.exit(${exitCode})`,
        ].join("\n"));
        const apkPath = join(directory, "signed release.apk");
        await writeFile(apkPath, "fixture APK");
        const result = spawnSync("bash", [join(scripts, "../../scripts/verify-release-apk.sh"), apkPath], {
            env: {
                ...process.env, APKSIGNER: "", ANDROID_SDK_ROOT: join(directory, "sdk"),
                APK_TEST_CALL_PATH: callPath,
            }, encoding: "utf8",
        });
        assert.equal(result.status, exitCode, result.stderr);
        assert.deepEqual(JSON.parse(await readFile(callPath, "utf8")), ["verify", "--verbose", "--print-certs", apkPath]);
    });
}

test("APK verifier rejects missing artifacts and missing SDK verifier", async (t) => {
    const directory = await temporaryDirectory(t);
    const apkPath = join(directory, "missing.apk");
    const env = { ...process.env, APKSIGNER: join(directory, "missing-apksigner") };
    const missingArtifact = spawnSync("bash", [join(scripts, "../../scripts/verify-release-apk.sh"), apkPath], { env, encoding: "utf8" });
    assert.notEqual(missingArtifact.status, 0);
    assert.match(missingArtifact.stderr, /missing or empty/);
    await writeFile(apkPath, "fixture");
    const missingTool = spawnSync("bash", [join(scripts, "../../scripts/verify-release-apk.sh"), apkPath], { env, encoding: "utf8" });
    assert.notEqual(missingTool.status, 0);
    assert.match(missingTool.stderr, /apksigner is unavailable/);
});

test("both APK upload workflows verify APK signatures before attesting the digest", async () => {
    for (const name of ["release-distribution", "firebase-wif-canary"]) {
        const source = await readFile(join(scripts, `../workflows/${name}.yml`), "utf8");
        const signature = source.indexOf('bash scripts/verify-release-apk.sh "${apk_files[0]}"');
        const digest = source.indexOf('digest "${apk_files[0]}"');
        assert.ok(signature > 0 && signature < digest, name);
    }
});
