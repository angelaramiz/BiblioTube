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

const authed = (uid = "u-9") => ({
  auth: { getUser: async () => ({ data: { user: { id: uid } }, error: null }) },
});

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

describe("user_id forzado desde JWT (nunca del cliente)", () => {
  it("POST /api/videos inyecta user_id del token", async () => {
    // Arrange
    let sent;
    const fakeDb = {
      ...authed("u-9"),
      from: () => ({
        insert: (row) => {
          sent = row;
          return { select: () => ({ single: async () => ({ data: { ...row, id: "v1" }, error: null }) }) };
        },
      }),
    };
    const app = createApp(() => fakeDb);
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({
          titulo: "T",
          url: "https://www.youtube.com/watch?v=x",
          user_id: "intruso",
        }),
      });
      // Assert
      assert.equal(status, 201);
      assert.equal(sent.user_id, "u-9");
      assert.equal(body.data.user_id, "u-9");
    } finally {
      server.close();
    }
  });

  it("POST /api/categories inyecta user_id del token", async () => {
    // Arrange
    let sent;
    const fakeDb = {
      ...authed("u-7"),
      from: () => ({
        insert: (row) => {
          sent = row;
          return { select: () => ({ single: async () => ({ data: { ...row, id: "c1" }, error: null }) }) };
        },
      }),
    };
    const app = createApp(() => fakeDb);
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status } = await req(port, "/api/categories", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ nombre: "Musica", user_id: "intruso" }),
      });
      // Assert
      assert.equal(status, 201);
      assert.equal(sent.user_id, "u-7");
    } finally {
      server.close();
    }
  });

  it("getUser con error -> 401", async () => {
    // Arrange
    const fakeDb = {
      auth: { getUser: async () => ({ data: null, error: { message: "bad jwt" } }) },
      from: () => {
        throw new Error("no debe tocar BD sin usuario");
      },
    };
    const app = createApp(() => fakeDb);
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", { headers: AUTH });
      // Assert
      assert.equal(status, 401);
      assert.match(body.error, /auth/i);
    } finally {
      server.close();
    }
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
    ...authed(),
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
