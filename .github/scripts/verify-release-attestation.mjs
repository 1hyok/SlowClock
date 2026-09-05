import { createHash } from "node:crypto";
import { createReadStream } from "node:fs";
import { appendFile, lstat, readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const SLSA_PROVENANCE_V1 = "https://slsa.dev/provenance/v1";
const SHA256_PATTERN = /^[0-9a-f]{64}$/;

function normalizeDigest(value) {
  const normalized = value.toLowerCase().replace(/^sha256:/, "");
  if (!SHA256_PATTERN.test(normalized)) {
    throw new Error("SHA-256 digest must contain exactly 64 hexadecimal characters.");
  }
  return normalized;
}

export async function sha256File(artifactPath) {
  const artifact = await lstat(artifactPath);
  if (!artifact.isFile() || artifact.isSymbolicLink()) {
    throw new Error("Release artifact must be a regular, non-symlink file.");
  }

  const hash = createHash("sha256");
  for await (const chunk of createReadStream(artifactPath)) {
    hash.update(chunk);
  }
  return hash.digest("hex");
}

export async function assertArtifactDigest(artifactPath, expectedDigest) {
  const expected = normalizeDigest(expectedDigest);
  const actual = await sha256File(artifactPath);
  if (actual !== expected) {
    throw new Error("Release artifact changed after its original digest was recorded.");
  }
  return actual;
}

export async function verifyAttestationSubject({
  artifactPath,
  expectedDigest,
  verificationResult,
}) {
  const actual = await assertArtifactDigest(artifactPath, expectedDigest);
  if (!Array.isArray(verificationResult) || verificationResult.length !== 1) {
    throw new Error("Exactly one verified provenance attestation is required.");
  }

  const statement = verificationResult[0]?.verificationResult?.statement;
  if (statement?.predicateType !== SLSA_PROVENANCE_V1) {
    throw new Error("Verified attestation must use SLSA provenance v1.");
  }
  if (!Array.isArray(statement.subject) || statement.subject.length !== 1) {
    throw new Error("Verified attestation must contain exactly one subject.");
  }

  const attested = normalizeDigest(statement.subject[0]?.digest?.sha256 ?? "");
  if (attested !== actual) {
    throw new Error("Verified attestation subject digest does not match the upload artifact.");
  }
  return actual;
}

async function main(args) {
  const [command, artifactPath, expectedOrOutput, verificationPath] = args;
  if (command === "digest" && artifactPath && expectedOrOutput && !verificationPath) {
    const digest = await sha256File(artifactPath);
    await appendFile(expectedOrOutput, `sha256=${digest}\n`, "utf8");
    return;
  }
  if (command === "assert" && artifactPath && expectedOrOutput && !verificationPath) {
    await assertArtifactDigest(artifactPath, expectedOrOutput);
    console.log("Release APK digest is unchanged at the upload boundary.");
    return;
  }
  if (command === "verify" && artifactPath && expectedOrOutput && verificationPath) {
    const verificationResult = JSON.parse(await readFile(verificationPath, "utf8"));
    await verifyAttestationSubject({
      artifactPath,
      expectedDigest: expectedOrOutput,
      verificationResult,
    });
    console.log("Release APK digest matches the verified attestation subject.");
    return;
  }
  throw new Error(
    "Usage: digest <artifact> <github-output> | assert <artifact> <sha256> | verify <artifact> <sha256> <verification-json>",
  );
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).catch((error) => {
    console.error(`::error::${error.message}`);
    process.exitCode = 1;
  });
}
