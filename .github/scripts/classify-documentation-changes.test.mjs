import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import test from "node:test";

import {
    classifyDocumentationChanges,
    classifyPaginatedDocumentationChanges,
    isDocumentationPath,
} from "./classify-documentation-changes.mjs";

const scriptPath = fileURLToPath(new URL("./classify-documentation-changes.mjs", import.meta.url));

test("allows only the root README and files below docs", () => {
    assert.equal(isDocumentationPath("README.md"), true);
    assert.equal(isDocumentationPath("docs/qa/status.md"), true);
    assert.equal(isDocumentationPath("docs/nested/README.md"), true);
    assert.equal(isDocumentationPath("readme.md"), false);
    assert.equal(isDocumentationPath("docs"), false);
    assert.equal(isDocumentationPath("feature/docs/status.md"), false);

    assert.equal(
        classifyDocumentationChanges(
            [{ filename: "README.md" }, { filename: "docs/qa/status.md" }],
            2,
        ),
        true,
    );
    assert.equal(
        classifyDocumentationChanges(
            [{ filename: "docs/qa/status.md" }, { filename: "app/build.gradle.kts" }],
            2,
        ),
        false,
    );
});

test("renames are documentation-only only when both paths are allowed", () => {
    assert.equal(
        classifyDocumentationChanges(
            [
                {
                    filename: "docs/qa/new-name.md",
                    previous_filename: "docs/qa/old-name.md",
                    status: "renamed",
                },
            ],
            1,
        ),
        true,
    );
    assert.equal(
        classifyDocumentationChanges(
            [
                {
                    filename: "docs/qa/moved.md",
                    previous_filename: "app/src/main/java/Old.kt",
                    status: "renamed",
                },
            ],
            1,
        ),
        false,
    );
    assert.throws(
        () => classifyDocumentationChanges([{ filename: "docs/qa/moved.md", status: "renamed" }], 1),
        /previous_filename/,
    );
});

test("pagination, malformed objects, empty responses, and count mismatches fail closed", () => {
    assert.equal(
        classifyPaginatedDocumentationChanges(
            [[{ filename: "README.md" }], [{ filename: "docs/release/distribution.md" }]],
            "2",
        ),
        true,
    );
    assert.throws(() => classifyPaginatedDocumentationChanges([], 1), /at least one page/);
    assert.throws(() => classifyPaginatedDocumentationChanges([[]], 1), /must not be empty/);
    assert.throws(() => classifyPaginatedDocumentationChanges([[{ filename: "README.md" }]], 2), /count mismatch/);
    assert.throws(() => classifyPaginatedDocumentationChanges([[{}]], 1), /valid filename/);
    assert.throws(() => classifyPaginatedDocumentationChanges([{ filename: "README.md" }], 1), /must be an array/);
});

test("the CLI consumes gh api slurped pages and prints only the classification", () => {
    const success = spawnSync(process.execPath, [scriptPath, "2"], {
        input: JSON.stringify([[{ filename: "README.md" }, { filename: "docs/qa/status.md" }]]),
        encoding: "utf8",
    });
    assert.equal(success.status, 0);
    assert.equal(success.stdout, "true\n");
    assert.equal(success.stderr, "");

    const nonDocumentation = spawnSync(process.execPath, [scriptPath, "1"], {
        input: JSON.stringify([[{ filename: ".github/workflows/pr-validation.yml" }]]),
        encoding: "utf8",
    });
    assert.equal(nonDocumentation.status, 0);
    assert.equal(nonDocumentation.stdout, "false\n");

    const malformed = spawnSync(process.execPath, [scriptPath, "2"], {
        input: JSON.stringify([[{ filename: "README.md" }]]),
        encoding: "utf8",
    });
    assert.notEqual(malformed.status, 0);
    assert.equal(malformed.stdout, "");
    assert.match(malformed.stderr, /count mismatch/);
});
