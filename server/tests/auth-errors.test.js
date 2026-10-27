import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/createApp.js";
import { isAuthError } from "../src/vault.js";

const AUTH = { Authorization: "Bearer test-jwt" };

async function listen(app) {
  const server = app.listen(0);
  await new Promise((r) => server.on("listening", r));
  return server;
}

async function req(port, path, opts = {}) {
  const res = await fetch(`http://127.0.0.1:${port}${path}`, opts);
  let body = null;
  try {
    body = await res.json();
  } catch {
    body = null;
  }
  return { status: res.status, body };
}

describe("isAuthError", () => {
  it("PGRST301 es auth", () => {
    assert.equal(isAuthError({ code: "PGRST301", message: "JWT cryptographic operation failed" }), true);
  });
  it("PGRST303 es auth", () => {
    assert.equal(isAuthError({ code: "PGRST303", message: "JWT expired" }), true);
  });
  it("mensaje jwt/expired matchea sin codigo", () => {
    assert.equal(isAuthError({ message: "jwt expired" }), true);
  });
  it("error de DB normal no es auth", () => {
    assert.equal(isAuthError({ code: "XX500", message: "db secreta" }), false);
  });
  it("null/primitivos no son auth", () => {
    assert.equal(isAuthError(null), false);
    assert.equal(isAuthError("boom"), false);
    assert.equal(isAuthError(undefined), false);
  });
});

describe("vault 401 (no 500) ante token invalido", () => {
  const jwtError = { code: "PGRST301", message: "JWT cryptographic operation failed" };
  const chainable = () => {
    const q = {
      order: () => q,
      then: (resolve) => resolve({ data: null, error: jwtError }),
    };
    return q;
  };
  const fakeDb = {
    from: () => ({ select: () => chainable() }),
  };
  const cases = [
    ["GET", "/api/categories", {}],
    ["GET", "/api/videos", {}],
  ];
  for (const [method, path, opts] of cases) {
    it(`${method} ${path} -> 401 auth expired`, async () => {
      // Arrange
      const app = createApp(() => fakeDb);
      const server = await listen(app);
      const port = server.address().port;
      try {
        // Act
        const { status, body } = await req(port, path, { method, headers: AUTH, ...opts });
        // Assert
        assert.equal(status, 401);
        assert.equal(body.success, false);
        assert.match(body.error, /auth/i);
      } finally {
        server.close();
      }
    });
  }
});
