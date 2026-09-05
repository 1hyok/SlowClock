#!/usr/bin/env node

import { readdir, readFile, stat } from "node:fs/promises";
import path from "node:path";
import { pathToFileURL } from "node:url";

// wiki/ 는 GitHub 위키 미러라 `[Text](Page)` 처럼 확장자 없는 위키 링크를 쓴다 — 저장소 경로 검사 대상이 아니다.
const ignoredDirectories = new Set([".codex", ".git", ".gradle", ".idea", "build", "node_modules", "wiki"]);

function maskCode(source) {
    const lines = source.split("\n");
    let fence = null;
    return lines.map((line) => {
        const marker = /^\s*(```+|~~~+)/.exec(line)?.[1] ?? null;
        if (marker) {
            if (fence === null) {
                fence = marker[0];
            } else if (marker[0] === fence) {
                fence = null;
            }
            return "";
        }
        if (fence !== null) {
            return "";
        }
        return line.replace(/`+[^`]*`+/g, "");
    }).join("\n");
}

export function extractMarkdownLinks(source) {
    const masked = maskCode(source);
    const links = [];
    const patterns = [
        /!?\[[^\]\n]*\]\(\s*(<[^>]+>|[^\s)]+)(?:\s+(?:"[^"]*"|'[^']*'|\([^)]*\)))?\s*\)/g,
        /^\s{0,3}\[[^\]\n]+\]:\s*(<[^>]+>|\S+)/gm,
    ];

    for (const pattern of patterns) {
        for (const match of masked.matchAll(pattern)) {
            const destination = match[1].startsWith("<") ? match[1].slice(1, -1) : match[1];
            const line = masked.slice(0, match.index).split("\n").length;
            links.push({ destination, line });
        }
    }
    return links;
}

function stripInlineHtml(value) {
    let result = "";
    let tagDepth = 0;
    for (const character of value) {
        if (character === "<") {
            tagDepth += 1;
        } else if (character === ">" && tagDepth > 0) {
            tagDepth -= 1;
        } else if (tagDepth === 0) {
            result += character;
        }
    }
    return result;
}

function githubSlug(value) {
    return stripInlineHtml(value)
        .trim()
        .toLocaleLowerCase("en-US")
        .replace(/[^\p{L}\p{N}\p{M}\s_-]/gu, "")
        .replace(/\s+/g, "-");
}

export function markdownAnchors(source) {
    const anchors = new Set();
    const occurrences = new Map();
    let fence = null;

    for (const line of source.split("\n")) {
        const marker = /^\s*(```+|~~~+)/.exec(line)?.[1] ?? null;
        if (marker) {
            if (fence === null) {
                fence = marker[0];
            } else if (marker[0] === fence) {
                fence = null;
            }
            continue;
        }
        if (fence !== null) {
            continue;
        }

        const explicit = /<(?:a|span)\s+(?:[^>]*?\s)?id=["']([^"']+)["'][^>]*>/gi;
        for (const match of line.matchAll(explicit)) {
            anchors.add(match[1]);
        }

        const heading = /^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$/.exec(line)?.[1];
        if (!heading) {
            continue;
        }
        const base = githubSlug(heading.replace(/\[([^\]]+)\]\([^)]*\)/g, "$1"));
        const count = occurrences.get(base) ?? 0;
        occurrences.set(base, count + 1);
        anchors.add(count === 0 ? base : `${base}-${count}`);
    }
    return anchors;
}

async function collectMarkdownFiles(directory, root = directory) {
    const files = [];
    for (const entry of await readdir(directory, { withFileTypes: true })) {
        if (entry.isDirectory()) {
            if (!ignoredDirectories.has(entry.name)) {
                files.push(...await collectMarkdownFiles(path.join(directory, entry.name), root));
            }
        } else if (entry.isFile() && entry.name.toLowerCase().endsWith(".md")) {
            files.push(path.relative(root, path.join(directory, entry.name)));
        }
    }
    return files.sort();
}

function splitDestination(destination) {
    const hashIndex = destination.indexOf("#");
    return hashIndex === -1
        ? { pathname: destination, fragment: "" }
        : { pathname: destination.slice(0, hashIndex), fragment: destination.slice(hashIndex + 1) };
}

function isExternal(destination) {
    return /^https?:\/\//i.test(destination);
}

function isIgnoredScheme(destination) {
    return /^(?:mailto|tel|sms|data):/i.test(destination);
}

export async function checkExternalUrl(url, fetchImplementation = fetch) {
    const target = new URL(url);
    target.hash = "";
    let lastError = null;

    for (let attempt = 0; attempt < 2; attempt += 1) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), 15_000);
        try {
            let response = await fetchImplementation(target, {
                method: "HEAD",
                redirect: "follow",
                signal: controller.signal,
                headers: { "user-agent": "SlowClock-Markdown-Link-Check/1.0" },
            });
            if (response.status === 405 || response.status === 501) {
                response = await fetchImplementation(target, {
                    method: "GET",
                    redirect: "follow",
                    signal: controller.signal,
                    headers: { "user-agent": "SlowClock-Markdown-Link-Check/1.0" },
                });
            }
            if (response.status === 404 || response.status === 410 || response.status >= 500) {
                return `HTTP ${response.status}`;
            }
            return null;
        } catch (error) {
            lastError = error;
        } finally {
            clearTimeout(timer);
        }
    }
    return lastError instanceof Error ? lastError.message : String(lastError);
}

export async function verifyMarkdownLinks(repositoryRoot, { external = false, fetchImplementation = fetch } = {}) {
    const root = path.resolve(repositoryRoot);
    const markdownFiles = await collectMarkdownFiles(root);
    const sourceCache = new Map();
    const anchorCache = new Map();
    const problems = [];
    const externalReferences = new Map();

    async function sourceOf(relativePath) {
        if (!sourceCache.has(relativePath)) {
            sourceCache.set(relativePath, await readFile(path.join(root, relativePath), "utf8"));
        }
        return sourceCache.get(relativePath);
    }

    for (const relativePath of markdownFiles) {
        const source = await sourceOf(relativePath);
        for (const { destination, line } of extractMarkdownLinks(source)) {
            if (isExternal(destination)) {
                if (external && !externalReferences.has(destination)) {
                    externalReferences.set(destination, { file: relativePath, line });
                }
                continue;
            }
            if (isIgnoredScheme(destination)) {
                continue;
            }

            const { pathname, fragment } = splitDestination(destination);
            let decodedPath;
            let decodedFragment;
            try {
                decodedPath = decodeURIComponent(pathname);
                decodedFragment = decodeURIComponent(fragment);
            } catch {
                problems.push({ file: relativePath, line, destination, reason: "invalid percent encoding" });
                continue;
            }

            const targetPath = decodedPath.length === 0
                ? relativePath
                : path.relative(root, path.resolve(path.dirname(path.join(root, relativePath)), decodedPath));
            const absoluteTarget = path.resolve(root, targetPath);
            if (absoluteTarget !== root && !absoluteTarget.startsWith(`${root}${path.sep}`)) {
                problems.push({ file: relativePath, line, destination, reason: "path escapes repository" });
                continue;
            }

            try {
                await stat(absoluteTarget);
            } catch {
                problems.push({ file: relativePath, line, destination, reason: "target does not exist" });
                continue;
            }

            if (decodedFragment && absoluteTarget.toLowerCase().endsWith(".md")) {
                if (!anchorCache.has(targetPath)) {
                    anchorCache.set(targetPath, markdownAnchors(await sourceOf(targetPath)));
                }
                if (!anchorCache.get(targetPath).has(decodedFragment)) {
                    problems.push({ file: relativePath, line, destination, reason: "heading anchor does not exist" });
                }
            }
        }
    }

    if (external) {
        const entries = [...externalReferences.entries()];
        let cursor = 0;
        await Promise.all(Array.from({ length: Math.min(4, entries.length) }, async () => {
            while (cursor < entries.length) {
                const index = cursor;
                cursor += 1;
                const [url, reference] = entries[index];
                const reason = await checkExternalUrl(url, fetchImplementation);
                if (reason !== null) {
                    problems.push({ ...reference, destination: url, reason });
                }
            }
        }));
    }

    return { files: markdownFiles.length, externalLinks: externalReferences.size, problems };
}

async function main() {
    const external = process.argv.includes("--external");
    const positional = process.argv.slice(2).filter((argument) => argument !== "--external");
    if (positional.length > 1) {
        throw new Error("usage: verify-markdown-links.mjs [--external] [repository-root]");
    }
    const result = await verifyMarkdownLinks(positional[0] ?? process.cwd(), { external });
    for (const problem of result.problems) {
        const message = `${problem.destination}: ${problem.reason}`.replaceAll("%", "%25").replaceAll("\n", "%0A");
        console.error(`::error file=${problem.file},line=${problem.line}::${message}`);
    }
    console.log(
        `Checked ${result.files} Markdown files${external ? ` and ${result.externalLinks} external links` : ""}.`,
    );
    if (result.problems.length > 0) {
        throw new Error(`${result.problems.length} Markdown link problem(s) found`);
    }
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.message : error);
        process.exitCode = 1;
    });
}
