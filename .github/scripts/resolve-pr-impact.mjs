#!/usr/bin/env node

import fs from "node:fs/promises";
import path from "node:path";
import process from "node:process";
import { pathToFileURL } from "node:url";

const SOURCE_FILE_PATTERN = /\.(?:java|kt)$/;
const KOTLIN_FILE_PATTERN = /\.kts?$/;
const JVM_LIBRARY_PLUGIN_PATTERN =
    /id\("(?:java-library|org\.jetbrains\.kotlin\.jvm)"\)|alias\(libs\.plugins\.kotlin\.jvm\)/;
const ANDROID_TEST_PLUGIN_PATTERN =
    /(?:id\("com\.android\.test"\)|alias\(libs\.plugins\.android\.test\))/;
const GLOBAL_GRADLE_PATHS = new Set([
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
]);
const IMPACT_POLICY_PATHS = new Set([
    ".github/scripts/resolve-pr-impact.mjs",
    ".github/scripts/resolve-pr-impact.test.mjs",
    ".github/workflows/pr-validation.yml",
    ".github/workflows/repository-quality.yml",
    ".github/workflows/lint.yml",
    ".github/workflows/unit-test.yml",
    ".github/workflows/screenshot.yml",
    ".github/workflows/codeql.yml",
]);
function normalizePath(value) {
    return String(value ?? "")
        .replaceAll("\\", "/")
        .replace(/^\.\//, "")
        .replace(/^\/+/, "");
}

export function changedPathsFromGithubFiles(payload) {
    const paths = new Set();

    function visit(value) {
        if (Array.isArray(value)) {
            value.forEach(visit);
            return;
        }
        if (!value || typeof value !== "object") {
            return;
        }
        for (const key of ["filename", "previous_filename"]) {
            const normalized = normalizePath(value[key]);
            if (normalized) {
                paths.add(normalized);
            }
        }
    }

    visit(payload);
    return [...paths].sort();
}

function moduleDirectory(projectPath) {
    return projectPath.slice(1).replaceAll(":", "/");
}

function accessorSegment(segment) {
    return segment.replace(/-([a-z0-9])/g, (_, character) => character.toUpperCase());
}

function projectAccessor(projectPath) {
    return projectPath
        .slice(1)
        .split(":")
        .map(accessorSegment)
        .join(".");
}

export function buildReverseDependencies(modules, dependencies) {
    const reverse = new Map(modules.map(({ projectPath }) => [projectPath, new Set()]));
    for (const [consumer, consumedProjects] of dependencies) {
        for (const dependency of consumedProjects) {
            reverse.get(dependency)?.add(consumer);
        }
    }
    return reverse;
}

export function reverseDependencyClosure(seeds, reverseDependencies) {
    const affected = new Set(seeds);
    const queue = [...affected];
    while (queue.length > 0) {
        const dependency = queue.shift();
        for (const consumer of reverseDependencies.get(dependency) ?? []) {
            if (!affected.has(consumer)) {
                affected.add(consumer);
                queue.push(consumer);
            }
        }
    }
    return affected;
}

function moduleForPath(filePath, modulesByLongestDirectory) {
    return modulesByLongestDirectory.find(
        ({ directory }) => filePath === directory || filePath.startsWith(`${directory}/`),
    );
}

function isGlobalGradlePath(filePath) {
    return (
        GLOBAL_GRADLE_PATHS.has(filePath) ||
        filePath === "gradlew" ||
        filePath === "gradlew.bat" ||
        filePath.startsWith("gradle/")
    );
}

function isProductionModulePath(filePath, module) {
    if (filePath === `${module.directory}/build.gradle.kts`) {
        return true;
    }
    const relative = filePath.slice(module.directory.length + 1);
    if (!relative.startsWith("src/")) {
        return true;
    }
    return (
        relative.startsWith("src/main/") ||
        relative.startsWith("src/debug/") ||
        relative.startsWith("src/release/") ||
        relative.startsWith("src/testFixtures/")
    );
}

function sortedProjectPaths(values) {
    return [...values].sort((left, right) => left.localeCompare(right));
}

export function isAndroidModuleBuild(buildSource) {
    return (
        !JVM_LIBRARY_PLUGIN_PATTERN.test(buildSource) &&
        !ANDROID_TEST_PLUGIN_PATTERN.test(buildSource)
    );
}

export function resolvePrImpact(changedFiles, modules, dependencies) {
    const moduleByProjectPath = new Map(modules.map((module) => [module.projectPath, module]));
    const modulesByLongestDirectory = [...modules].sort(
        (left, right) => right.directory.length - left.directory.length,
    );
    const reverseDependencies = buildReverseDependencies(modules, dependencies);
    const allProjects = new Set(modules.map(({ projectPath }) => projectPath));
    const productionSeeds = new Set();
    const unitOnlySeeds = new Set();
    const androidLintOnlySeeds = new Set();
    const screenshotOnlySeeds = new Set();
    const ktlintModules = new Set();

    let globalGradleChange = false;
    let globalKtlintChange = false;
    let screenshotInfrastructureChange = false;
    let compileAndroidTest = false;
    let runNodeTests = false;
    let codeqlJavaKotlin = false;
    let forceFull = false;
    let repositoryQualityFixtures = false;

    for (const rawPath of changedFiles) {
        const filePath = normalizePath(rawPath);
        if (!filePath) continue;
        let recognizedNonModulePath =
            filePath === "README.md" ||
            filePath.startsWith("docs/") ||
            filePath.startsWith(".github/") ||
            filePath.startsWith("scripts/") ||
            filePath.startsWith("git-hooks/") ||
            // Firestore 규칙과 그 테스트는 Android 모듈과 무관하다. 전용 워크플로(firestore-rules.yml)가
            // 에뮬레이터로 검증하므로 여기서 전체 검증을 강제하지 않는다.
            filePath.startsWith("firestore-tests/") ||
            [
                ".dockerignore",
                ".editorconfig",
                ".gitignore",
                ".mcp.json",
                "Dockerfile.screenshot",
                "firebase.json",
                "firestore.indexes.json",
                "firestore.rules",
            ].includes(filePath);

        if (IMPACT_POLICY_PATHS.has(filePath)) {
            // A planner or one of its consumers must not be able to classify its own
            // validation away. Policy changes therefore exercise every lane.
            forceFull = true;
        }
        if (
            filePath === "scripts/repository-quality.sh" ||
            filePath === "scripts/test-repository-quality.sh"
        ) {
            repositoryQualityFixtures = true;
            forceFull = true;
        }

        if (
            filePath.startsWith(".github/scripts/") ||
            filePath.startsWith(".github/workflows/") ||
            filePath.startsWith(".github/actions/") ||
            filePath.startsWith("scripts/")
        ) {
            runNodeTests = true;
        }
        if (filePath.startsWith(".github/workflows/") || filePath.startsWith(".github/actions/")) {
        }
        if (filePath === ".editorconfig") {
            globalKtlintChange = true;
        }
        if (filePath === "Dockerfile.screenshot" || filePath === ".dockerignore") {
            screenshotInfrastructureChange = true;
        }
        if (isGlobalGradlePath(filePath)) {
            globalGradleChange = true;
            codeqlJavaKotlin = true;
            compileAndroidTest = true;
            continue;
        }

        const module = moduleForPath(filePath, modulesByLongestDirectory);
        if (!module) {
            if (!recognizedNonModulePath) {
                forceFull = true;
            }
            continue;
        }
        const relative = filePath.slice(module.directory.length + 1);
        if (KOTLIN_FILE_PATTERN.test(filePath)) {
            ktlintModules.add(module.projectPath);
        }
        if (SOURCE_FILE_PATTERN.test(filePath) && isProductionModulePath(filePath, module)) {
            codeqlJavaKotlin = true;
        }
        if (filePath === `${module.directory}/build.gradle.kts`) {
            codeqlJavaKotlin = true;
        }

        if (relative.startsWith("src/test/") || relative.startsWith("src/testDebug/")) {
            unitOnlySeeds.add(module.projectPath);
        } else if (
            relative.startsWith("src/screenshotTest/") ||
            relative.startsWith("src/screenshotTestDebug/") ||
            relative.startsWith("src/screenshotTestRelease/")
        ) {
            screenshotOnlySeeds.add(module.projectPath);
        } else if (relative.startsWith("src/androidTest/")) {
            compileAndroidTest = true;
            androidLintOnlySeeds.add(module.projectPath);
        } else if (isProductionModulePath(filePath, module)) {
            productionSeeds.add(module.projectPath);
        }
    }

    if (forceFull || globalGradleChange) {
        productionSeeds.clear();
        allProjects.forEach((projectPath) => productionSeeds.add(projectPath));
        globalKtlintChange = true;
        screenshotInfrastructureChange = true;
        runNodeTests = true;
        codeqlJavaKotlin = true;
        compileAndroidTest = true;
    }

    const productionAffected = reverseDependencyClosure(productionSeeds, reverseDependencies);
    if (productionAffected.size > 0) {
        // androidTest compiles against the assembled app and therefore has every
        // production module as an upstream dependency. Compilation is cheap enough
        // to keep this boundary checked without booting an emulator.
        compileAndroidTest = true;
    }
    const unitAffected = new Set([...productionAffected, ...unitOnlySeeds]);
    const lintAffected = new Set([...productionAffected, ...androidLintOnlySeeds]);
    const screenshotAffected = new Set([...screenshotOnlySeeds]);
    if (screenshotInfrastructureChange) {
        modules.filter(({ screenshot }) => screenshot).forEach(({ projectPath }) => {
            screenshotAffected.add(projectPath);
        });
    } else {
        productionAffected.forEach((projectPath) => {
            if (moduleByProjectPath.get(projectPath)?.screenshot) {
                screenshotAffected.add(projectPath);
            }
        });
    }

    // Android 모듈은 testDebugUnitTest, 순수 JVM 모듈은 test. 역의존 모듈까지 포함한다.
    const unitTestModules = sortedProjectPaths(unitAffected);
    const unitTestTasks = unitTestModules.map((projectPath) =>
        moduleByProjectPath.get(projectPath)?.android === false
            ? `${projectPath}:test`
            : `${projectPath}:testDebugUnitTest`,
    );
    if (compileAndroidTest) {
        unitTestTasks.push(":app:compileDebugAndroidTestKotlin");
    }

    const ktlintTasks = globalKtlintChange
        ? ["ktlintCheck"]
        : sortedProjectPaths(ktlintModules).map((projectPath) => `${projectPath}:ktlintCheck`);
    const androidLintModules = sortedProjectPaths(
        [...lintAffected].filter((projectPath) => moduleByProjectPath.get(projectPath)?.android),
    );
    const androidLintTasks = androidLintModules.map((projectPath) => `${projectPath}:lintDebug`);
    const verifyManifest = androidLintModules.includes(":app");
    if (verifyManifest) {
        androidLintTasks.push(":app:processDebugMainManifest");
    }
    const screenshotModules = sortedProjectPaths(
        [...screenshotAffected].filter((projectPath) => moduleByProjectPath.get(projectPath)?.screenshot),
    );
    const screenshotTasks = screenshotModules.map(
        (projectPath) => `${projectPath}:validateScreenshotTest`,
    );

    return {
        changedFiles: [...changedFiles].map(normalizePath).filter(Boolean).sort(),
        ktlintTasks,
        androidLintTasks,
        verifyManifest,
        runNodeTests,
        unitTestTasks: [...new Set(unitTestTasks)],
        unitTestModules,
        screenshotModules,
        screenshotTasks,
        codeqlJavaKotlin,
        repositoryQualityFull: forceFull,
        repositoryQualityFixtures,
    };
}

async function pathExists(target) {
    try {
        await fs.access(target);
        return true;
    } catch {
        return false;
    }
}

export async function inspectModules(root) {
    const settings = await fs.readFile(path.join(root, "settings.gradle.kts"), "utf8");
    const projectPaths = [...settings.matchAll(/include\("(:[^"]+)"\)/g)].map(
        (match) => match[1],
    );
    const modules = [];
    for (const projectPath of projectPaths) {
        const directory = moduleDirectory(projectPath);
        const buildFile = path.join(root, directory, "build.gradle.kts");
        const source = await fs.readFile(buildFile, "utf8");
        modules.push({
            projectPath,
            directory,
            android: isAndroidModuleBuild(source),
            screenshot: await pathExists(path.join(root, directory, "src", "screenshotTest")),
            buildSource: source,
        });
    }
    return modules;
}

export function inspectDependencies(modules) {
    const accessorToProject = new Map(
        modules.map(({ projectPath }) => [projectAccessor(projectPath), projectPath]),
    );
    const knownProjects = new Set(modules.map(({ projectPath }) => projectPath));
    const dependencies = new Map();

    for (const module of modules) {
        const consumed = new Set();
        for (const match of module.buildSource.matchAll(/projects\.([A-Za-z0-9_.]+)/g)) {
            const projectPath = accessorToProject.get(match[1]);
            if (projectPath && projectPath !== module.projectPath) {
                consumed.add(projectPath);
            }
        }
        for (const match of module.buildSource.matchAll(/project\(\s*"(:[^"]+)"\s*\)/g)) {
            if (knownProjects.has(match[1]) && match[1] !== module.projectPath) {
                consumed.add(match[1]);
            }
        }
        dependencies.set(module.projectPath, consumed);
    }
    return dependencies;
}

export function githubOutputLines(impact) {
    const entries = {
        ktlint_required: impact.ktlintTasks.length > 0,
        ktlint_tasks: impact.ktlintTasks.join(" "),
        android_lint_required: impact.androidLintTasks.length > 0,
        android_lint_tasks: impact.androidLintTasks.join(" "),
        verify_manifest: impact.verifyManifest,
        unit_test_required: impact.runNodeTests || impact.unitTestTasks.length > 0,
        run_node_tests: impact.runNodeTests,
        unit_test_tasks: impact.unitTestTasks.join(" "),
        screenshot_required: impact.screenshotTasks.length > 0,
        screenshot_modules: impact.screenshotModules.join(" "),
        screenshot_tasks: impact.screenshotTasks.join(" "),
        codeql_java_kotlin: impact.codeqlJavaKotlin,
        repository_quality_full: impact.repositoryQualityFull,
        repository_quality_fixtures: impact.repositoryQualityFixtures,
    };
    return Object.entries(entries).map(([key, value]) => `${key}=${value}`);
}

export async function analyzeRepositoryImpact(root, githubFiles) {
    const modules = await inspectModules(root);
    const dependencies = inspectDependencies(modules);
    return resolvePrImpact(changedPathsFromGithubFiles(githubFiles), modules, dependencies);
}

async function main() {
    const [filesPath, rootArgument] = process.argv.slice(2);
    if (!filesPath) {
        throw new Error("GitHub pull request files JSON path is required");
    }
    const root = path.resolve(rootArgument ?? process.cwd());
    const payload = JSON.parse(await fs.readFile(path.resolve(filesPath), "utf8"));
    const impact = await analyzeRepositoryImpact(root, payload);
    process.stdout.write(`${githubOutputLines(impact).join("\n")}\n`);
}

const invokedPath = process.argv[1] ? pathToFileURL(path.resolve(process.argv[1])).href : "";
if (import.meta.url === invokedPath) {
    main().catch((error) => {
        console.error(error instanceof Error ? error.stack : error);
        process.exitCode = 1;
    });
}
