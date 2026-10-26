import { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  sanitizeAppName,
  buildVersionKey,
  normalizeVersionInfo,
  ok,
  fail,
  DEFAULT_VERSION,
} from "../src/version.js";

// --- sanitizeAppName ---
describe("sanitizeAppName", () => {
  it("usa 'bibliotube' cuando falta o es vacio", () => {
    assert.equal(sanitizeAppName(undefined), "bibliotube");
    assert.equal(sanitizeAppName(null), "bibliotube");
    assert.equal(sanitizeAppName(""), "bibliotube");
    assert.equal(sanitizeAppName("   "), "bibliotube");
  });

  it("normaliza a minusculas y recorta espacios", () => {
    assert.equal(sanitizeAppName("  BiblioTube  "), "bibliotube");
  });

  it("acepta guiones, guion bajo y punto", () => {
    assert.equal(sanitizeAppName("mi-app_2.0"), "mi-app_2.0");
  });

  it("rechaza null bytes, slashes y path traversal", () => {
    assert.throws(() => sanitizeAppName("../etc"), /invalid/i);
    assert.throws(() => sanitizeAppName("a/b"), /invalid/i);
    assert.throws(() => sanitizeAppName("a\\b"), /invalid/i);
    assert.throws(() => sanitizeAppName("a\0b"), /invalid/i);
  });

  it("rechaza tipos no string y nombres muy largos", () => {
    assert.throws(() => sanitizeAppName(123), /invalid/i);
    assert.throws(() => sanitizeAppName({}), /invalid/i);
    assert.throws(() => sanitizeAppName("a".repeat(65)), /invalid/i);
  });

  it("rechaza caracteres especiales (unicode, emojis, SQL)", () => {
    assert.throws(() => sanitizeAppName("app'; DROP TABLE--"), /invalid/i);
    assert.throws(() => sanitizeAppName("aplicación"), /invalid/i);
  });
});

// --- buildVersionKey ---
describe("buildVersionKey", () => {
  it("construye app_version_<app>", () => {
    assert.equal(buildVersionKey("bibliotube"), "app_version_bibliotube");
  });

  it("rechaza input vacio o invalido", () => {
    assert.throws(() => buildVersionKey(""), /invalid/i);
    assert.throws(() => buildVersionKey(null), /invalid/i);
  });
});

// --- normalizeVersionInfo ---
describe("normalizeVersionInfo", () => {
  it("acepta un VersionInfo valido (happy path)", () => {
    const out = normalizeVersionInfo({
      versionCode: 2,
      versionName: "1.0.1",
      apkUrl: "/public/bibliotube.apk",
    });
    assert.deepEqual(out, {
      versionCode: 2,
      versionName: "1.0.1",
      apkUrl: "/public/bibliotube.apk",
    });
  });

  it("devuelve DEFAULT cuando raw es null/undefined", () => {
    assert.deepEqual(normalizeVersionInfo(null), { ...DEFAULT_VERSION });
    assert.deepEqual(normalizeVersionInfo(undefined), { ...DEFAULT_VERSION });
  });

  it("rechaza versionCode no entero, <=0 o enorme", () => {
    assert.throws(() => normalizeVersionInfo({ versionCode: 0, versionName: "1.0.0", apkUrl: "/a.apk" }), /versionCode/i);
    assert.throws(() => normalizeVersionInfo({ versionCode: 1.5, versionName: "1.0.0", apkUrl: "/a.apk" }), /versionCode/i);
    assert.throws(() => normalizeVersionInfo({ versionCode: "2", versionName: "1.0.0", apkUrl: "/a.apk" }), /versionCode/i);
  });

  it("rechaza versionName con formato invalido", () => {
    assert.throws(() => normalizeVersionInfo({ versionCode: 1, versionName: "", apkUrl: "/a.apk" }), /versionName/i);
    assert.throws(() => normalizeVersionInfo({ versionCode: 1, versionName: "v1", apkUrl: "/a.apk" }), /versionName/i);
    assert.throws(() => normalizeVersionInfo({ versionCode: 1, versionName: 123, apkUrl: "/a.apk" }), /versionName/i);
  });

  it("rechaza apkUrl vacia o no string", () => {
    assert.throws(() => normalizeVersionInfo({ versionCode: 1, versionName: "1.0.0", apkUrl: "" }), /apkUrl/i);
    assert.throws(() => normalizeVersionInfo({ versionCode: 1, versionName: "1.0.0", apkUrl: null }), /apkUrl/i);
  });

  it("ignora campos extra sin mutar el input", () => {
    const raw = { versionCode: 3, versionName: "2.0.0", apkUrl: "/b.apk", extra: true };
    const frozen = Object.freeze({ ...raw });
    const out = normalizeVersionInfo(frozen);
    assert.deepEqual(out, { versionCode: 3, versionName: "2.0.0", apkUrl: "/b.apk" });
  });
});

// --- envelope ---
describe("envelope ok/fail", () => {
  it("ok envuelve data con success:true", () => {
    assert.deepEqual(ok({ a: 1 }), { success: true, data: { a: 1 } });
  });

  it("fail envuelve error con success:false", () => {
    const e = fail("algo fallo");
    assert.equal(e.success, false);
    assert.equal(e.error, "algo fallo");
  });

  it("fail exige mensaje no vacio", () => {
    assert.throws(() => fail(""), /error/i);
    assert.throws(() => fail(null), /error/i);
  });
});
