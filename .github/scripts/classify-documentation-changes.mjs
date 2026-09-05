#!/usr/bin/env node

import path from "node:path";
import { pathToFileURL } from "node:url";

export function isDocumentationPath(filename) {
    return (
        filename === "README.md" ||
        (typeof filename === "string" && filename.startsWith("docs/") && filename.length > "docs/".length)
    );
}

function requireChangedFileCount(value) {
    const serialized = String(value ?? "");
    if (!/^[1-9][0-9]*$/.test(serialized)) {
        throw new Error(`changed_files must be a positive integer: ${serialized || "<empty>"}`);
    }

    const count = Number(serialized);
    if (!Number.isSafeInteger(count)) {
        throw new Error(`changed_files exceeds the safe integer range: ${serialized}`);
    }
    return count;
}

export function flattenPaginatedFiles(pages) {
    if (!Array.isArray(pages) || pages.length === 0) {
        throw new Error("paginated pull request files must contain at least one page");
    }

    const files = [];
    for (const page of pages) {
        if (!Array.isArray(page)) {
            throw new Error("each paginated pull request files response must be an array");
        }
        files.push(...page);
    }

    if (files.length === 0) {
        throw new Error("pull request files response must not be empty");
    }
    return files;
}

function documentationStateOf(file, index) {
    if (file === null || typeof file !== "object" || Array.isArray(file)) {
        throw new Error(`pull request file at index ${index} is not an object`);
    }
    if (typeof file.filename !== "string" || file.filename.length === 0) {
        throw new Error(`pull request file at index ${index} has no valid filename`);
    }

    const paths = [file.filename];
    if (file.status === "renamed") {
        if (typeof file.previous_filename !== "string" || file.previous_filename.length === 0) {
            throw new Error(`renamed pull request file at index ${index} has no valid previous_filename`);
        }
        paths.push(file.previous_filename);
    } else if (Object.hasOwn(file, "previous_filename")) {
        if (typeof file.previous_filename !== "string" || file.previous_filename.length === 0) {
            throw new Error(`pull request file at index ${index} has an invalid previous_filename`);
        }
        paths.push(file.previous_filename);
    }

    return paths.every(isDocumentationPath);
}

export function classifyDocumentationChanges(files, expectedChangedFileCount) {
    const expectedCount = requireChangedFileCount(expectedChangedFileCount);
    if (!Array.isArray(files) || files.length === 0) {
        throw new Error("pull request files must be a non-empty array");
    }
    if (files.length !== expectedCount) {
        throw new Error(`pull request file count mismatch: expected ${expectedCount}, received ${files.length}`);
    }

    return files.every(documentationStateOf);
}

export function classifyPaginatedDocumentationChanges(pages, expectedChangedFileCount) {
    return classifyDocumentationChanges(flattenPaginatedFiles(pages), expectedChangedFileCount);
}

async function main() {
    const expectedChangedFileCount = process.argv[2];
    let input = "";
    process.stdin.setEncoding("utf8");
    for await (const chunk of process.stdin) {
        input += chunk;
    }
    const pages = JSON.parse(input);
    const docsOnly = classifyPaginatedDocumentationChanges(pages, expectedChangedFileCount);
    process.stdout.write(`${docsOnly}\n`);
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.message : error);
        process.exitCode = 1;
    });
}
