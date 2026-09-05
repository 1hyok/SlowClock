import assert from "node:assert/strict";
import { mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";

import {
  assertArtifactDigest,
  sha256File,
  verifyAttestationSubject,
} from "./verify-release-attestation.mjs";

async function fixture(t, contents = "signed release apk") {
  const directory = await mkdtemp(join(tmpdir(), "release-attestation-"));
  t.after(() => rm(directory, { recursive: true, force: true }));
  const artifactPath = join(directory, "app-release.apk");
  await writeFile(artifactPath, contents);
  const digest = await sha256File(artifactPath);
  return { artifactPath, digest };
}

function verifiedResult(digest, overrides = {}) {
  return [
    {
      verificationResult: {
        statement: {
          predicateType: "https://slsa.dev/provenance/v1",
          subject: [{ name: "app-release.apk", digest: { sha256: digest } }],
          ...overrides,
        },
      },
    },
  ];
}

test("accepts one verified subject matching the current artifact", async (t) => {
  const { artifactPath, digest } = await fixture(t);
  const actual = await verifyAttestationSubject({
    artifactPath,
    expectedDigest: digest,
    verificationResult: verifiedResult(digest),
  });
  assert.equal(actual, digest);
});

test("fails when the artifact changes after the digest was recorded", async (t) => {
  const { artifactPath, digest } = await fixture(t);
  await writeFile(artifactPath, "mutated release apk");
  await assert.rejects(
    assertArtifactDigest(artifactPath, digest),
    /changed after its original digest/,
  );
});

test("fails when the verified subject digest differs", async (t) => {
  const { artifactPath, digest } = await fixture(t);
  const differentDigest = "0".repeat(64);
  await assert.rejects(
    verifyAttestationSubject({
      artifactPath,
      expectedDigest: digest,
      verificationResult: verifiedResult(differentDigest),
    }),
    /subject digest does not match/,
  );
});

test("fails closed on multiple verified attestations", async (t) => {
  const { artifactPath, digest } = await fixture(t);
  await assert.rejects(
    verifyAttestationSubject({
      artifactPath,
      expectedDigest: digest,
      verificationResult: [...verifiedResult(digest), ...verifiedResult(digest)],
    }),
    /Exactly one verified provenance attestation/,
  );
});

test("rejects a non-provenance predicate", async (t) => {
  const { artifactPath, digest } = await fixture(t);
  await assert.rejects(
    verifyAttestationSubject({
      artifactPath,
      expectedDigest: digest,
      verificationResult: verifiedResult(digest, { predicateType: "https://example.invalid" }),
    }),
    /SLSA provenance v1/,
  );
});
