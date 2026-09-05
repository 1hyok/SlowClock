import assert from "node:assert/strict";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";

import {
    checkExternalUrl,
    extractMarkdownLinks,
    markdownAnchors,
    verifyMarkdownLinks,
} from "./verify-markdown-links.mjs";

test("extracts inline and reference links but ignores fenced and inline code", () => {
    const links = extractMarkdownLinks([
        "[local](docs/guide.md#setup)",
        "[reference]: https://example.com/docs",
        "`[inline](missing.md)`",
        "```md",
        "[fenced](missing.md)",
        "```",
    ].join("\n"));

    assert.deepEqual(links, [
        { destination: "docs/guide.md#setup", line: 1 },
        { destination: "https://example.com/docs", line: 2 },
    ]);
});

test("models GitHub heading anchors including Unicode, duplicates, and explicit ids", () => {
    const anchors = markdownAnchors([
        "# 설치 & 실행",
        "## Setup!",
        "## Setup!",
        "## Before <em>after</em>",
        "## Safe <<script>> tail",
        "<a id=\"stable-target\"></a>",
        "```md",
        "# Hidden",
        "```",
    ].join("\n"));

    assert.deepEqual([...anchors], [
        "설치-실행",
        "setup",
        "setup-1",
        "before-after",
        "safe-tail",
        "stable-target",
    ]);
});

test("checks relative files and Markdown anchors and fails closed on escapes", async (t) => {
    const root = await mkdtemp(path.join(os.tmpdir(), "slowclock-markdown-links-"));
    t.after(() => rm(root, { recursive: true, force: true }));
    await mkdir(path.join(root, "docs"));
    await writeFile(path.join(root, "README.md"), [
        "[valid](docs/guide.md#setup)",
        "[missing](docs/missing.md)",
        "[anchor](docs/guide.md#absent)",
        "[escape](../../outside.md)",
    ].join("\n"));
    await writeFile(path.join(root, "docs/guide.md"), "# Setup\n");

    const result = await verifyMarkdownLinks(root);
    assert.equal(result.files, 2);
    assert.deepEqual(result.problems.map(({ reason }) => reason), [
        "target does not exist",
        "heading anchor does not exist",
        "path escapes repository",
    ]);
});

test("external checks accept bot-blocking responses and reject missing or server errors", async () => {
    const response = (status) => ({ status });
    assert.equal(await checkExternalUrl("https://example.com", async () => response(403)), null);
    assert.equal(await checkExternalUrl("https://example.com", async () => response(404)), "HTTP 404");
    assert.equal(await checkExternalUrl("https://example.com", async () => response(503)), "HTTP 503");

    const methods = [];
    assert.equal(
        await checkExternalUrl("https://example.com", async (_url, options) => {
            methods.push(options.method);
            return response(options.method === "HEAD" ? 405 : 200);
        }),
        null,
    );
    assert.deepEqual(methods, ["HEAD", "GET"]);
});

test("external mode deduplicates URLs and reports network failures at their first use", async (t) => {
    const root = await mkdtemp(path.join(os.tmpdir(), "slowclock-markdown-external-"));
    t.after(() => rm(root, { recursive: true, force: true }));
    await writeFile(
        path.join(root, "README.md"),
        "[one](https://example.invalid/a)\n[two](https://example.invalid/a#fragment)\n",
    );

    const calls = [];
    const result = await verifyMarkdownLinks(root, {
        external: true,
        fetchImplementation: async (url) => {
            calls.push(String(url));
            throw new Error("network unavailable");
        },
    });

    assert.equal(calls.length, 4);
    assert.equal(result.externalLinks, 2);
    assert.equal(result.problems.length, 2);
    assert.match(result.problems[0].reason, /network unavailable/);
});
