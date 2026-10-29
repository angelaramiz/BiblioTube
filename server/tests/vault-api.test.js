import { describe, it } from "node:test";
import assert from "node:assert/strict";
import { createApp } from "../src/createApp.js";

const JWT = "test-jwt-token";
const AUTH = { Authorization: `Bearer ${JWT}` };

async function listen(app) {
  const server = app.listen(0);
  await new Promise((r) => server.on("listening", r));
  return server;
}

async function req(port, path, opts = {}) {
  const res = await fetch(`http://127.0.0.1:${port}${path}`, opts);
  const body = await res.json();
  return { status: res.status, body };
}

/** Fabrica mock que captura el JWT y entrega el cliente falso. */
function factoryFor(fakeClient, captured) {
  return (jwt) => {
    captured.jwt = jwt;
    return fakeClient;
  };
}

/** Query thenable para listado de videos (eq/order encadenables + await). */
function videosListQuery(rows, captured = {}) {
  const q = {
    eq: (col, val) => {
      captured.eq = { col, val };
      return q;
    },
    order: (col, opts) => {
      captured.orders = [...(captured.orders ?? []), [col, opts]];
      return q;
    },
    then: (resolve) => resolve({ data: rows, error: null }),
  };
  return q;
}

describe("vault auth", () => {
  it("401 sin JWT en rutas protegidas", async () => {
    // Arrange
    const app = createApp(() => {
      throw new Error("no debe llamarse sin auth");
    });
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act + Assert
      for (const [method, path] of [
        ["GET", "/api/categories"],
        ["POST", "/api/categories"],
        ["PATCH", "/api/categories/1"],
        ["DELETE", "/api/categories/1"],
        ["GET", "/api/videos"],
        ["POST", "/api/videos"],
        ["PATCH", "/api/videos/1"],
        ["DELETE", "/api/videos/1"],
      ]) {
        const { status, body } = await req(port, path, { method });
        assert.equal(status, 401, `${method} ${path}`);
        assert.equal(body.success, false);
        assert.ok(body.error);
      }
    } finally {
      server.close();
    }
  });

  it("reenvia el JWT del header a la factoria (RLS por auth.uid)", async () => {
    // Arrange
    const captured = {};
    const fake = {
      from: () => ({ select: () => ({ order: async () => ({ data: [], error: null }) }) }),
    };
    const app = createApp(factoryFor(fake, captured));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      await req(port, "/api/categories", { headers: AUTH });
      // Assert
      assert.equal(captured.jwt, JWT);
    } finally {
      server.close();
    }
  });

  it("cliente fijo (compat) sigue funcionando con Bearer", async () => {
    // Arrange
    const fake = {
      from: () => ({ select: () => ({ order: async () => ({ data: [], error: null }) }) }),
    };
    const app = createApp(fake);
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", { headers: AUTH });
      // Assert
      assert.equal(status, 200);
      assert.deepEqual(body.data, []);
    } finally {
      server.close();
    }
  });
});

describe("GET /api/categories", () => {
  it("200 + lista ordenada", async () => {
    // Arrange
    const rows = [{ id: "1", nombre: "A", orden: 0 }];
    const fake = {
      from: (t) => {
        assert.equal(t, "categories");
        return { select: () => ({ order: async () => ({ data: rows, error: null }) }) };
      },
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", { headers: AUTH });
      // Assert
      assert.equal(status, 200);
      assert.equal(body.success, true);
      assert.deepEqual(body.data, rows);
    } finally {
      server.close();
    }
  });

  it("500 cuando Supabase falla (sin filtrar detalle)", async () => {
    // Arrange
    const fake = {
      from: () => ({ select: () => ({ order: async () => ({ data: null, error: { message: "db secreta" } }) }) }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", { headers: AUTH });
      // Assert
      assert.equal(status, 500);
      assert.equal(body.success, false);
      assert.ok(!JSON.stringify(body).includes("db secreta"));
    } finally {
      server.close();
    }
  });
});

describe("POST /api/categories", () => {
  it("201 + creada", async () => {
    // Arrange
    const created = { id: "c1", nombre: "Musica", color: "#7C3AED", orden: 0 };
    let sent;
    const fake = {
      from: () => ({
        insert: (row) => {
          sent = row;
          return { select: () => ({ single: async () => ({ data: created, error: null }) }) };
        },
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ nombre: "Musica" }),
      });
      // Assert
      assert.equal(status, 201);
      assert.deepEqual(body.data, created);
      assert.equal(sent.nombre, "Musica");
    } finally {
      server.close();
    }
  });

  it("400 con nombre invalido", async () => {
    // Arrange
    const app = createApp(() => ({ from: () => { throw new Error("no debe llegar a BD"); } }));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ nombre: "" }),
      });
      // Assert
      assert.equal(status, 400);
      assert.equal(body.success, false);
    } finally {
      server.close();
    }
  });
});

describe("PATCH /api/categories/:id", () => {
  it("200 + actualizada", async () => {
    // Arrange
    const updated = { id: "c1", nombre: "Nuevo" };
    const fake = {
      from: () => ({
        update: () => ({ eq: () => ({ select: () => ({ single: async () => ({ data: updated, error: null }) }) }) }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories/c1", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ nombre: "Nuevo" }),
      });
      // Assert
      assert.equal(status, 200);
      assert.deepEqual(body.data, updated);
    } finally {
      server.close();
    }
  });

  it("404 cuando no existe", async () => {
    // Arrange
    const fake = {
      from: () => ({
        update: () => ({
          eq: () => ({ select: () => ({ single: async () => ({ data: null, error: { code: "PGRST116" } }) }) }),
        }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories/nope", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ nombre: "X" }),
      });
      // Assert
      assert.equal(status, 404);
      assert.equal(body.success, false);
    } finally {
      server.close();
    }
  });

  it("400 con patch vacio", async () => {
    // Arrange
    const app = createApp(() => ({ from: () => { throw new Error("no debe llegar a BD"); } }));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status } = await req(port, "/api/categories/c1", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({}),
      });
      // Assert
      assert.equal(status, 400);
    } finally {
      server.close();
    }
  });
});

describe("DELETE /api/categories/:id", () => {
  it("200 + {deleted:true}", async () => {
    // Arrange
    const fake = {
      from: () => ({ delete: () => ({ eq: () => ({ select: async () => ({ data: [{ id: "c1" }], error: null }) }) }) }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories/c1", { method: "DELETE", headers: AUTH });
      // Assert
      assert.equal(status, 200);
      assert.deepEqual(body.data, { deleted: true });
    } finally {
      server.close();
    }
  });

  it("404 cuando no existe", async () => {
    // Arrange
    const fake = {
      from: () => ({ delete: () => ({ eq: () => ({ select: async () => ({ data: [], error: null }) }) }) }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status } = await req(port, "/api/categories/nope", { method: "DELETE", headers: AUTH });
      // Assert
      assert.equal(status, 404);
    } finally {
      server.close();
    }
  });
});

describe("GET /api/videos", () => {
  it("200 + lista ordenada prioridad/fecha aunque BD venga desordenada", async () => {
    // Arrange
    const rows = [
      { id: "a", prioridad: 0, fecha_guardado: "2026-01-01T00:00:00.000Z" },
      { id: "b", prioridad: 2, fecha_guardado: "2026-05-01T00:00:00.000Z" },
    ];
    const fake = { from: () => ({ select: () => videosListQuery(rows) }) };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", { headers: AUTH });
      // Assert
      assert.equal(status, 200);
      assert.deepEqual(body.data.map((r) => r.id), ["b", "a"]);
    } finally {
      server.close();
    }
  });

  it("reenvia category_id como filtro", async () => {
    // Arrange
    const captured = {};
    const fake = { from: () => ({ select: () => videosListQuery([], captured) }) };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      await req(port, "/api/videos?category_id=cat-9", { headers: AUTH });
      // Assert
      assert.deepEqual(captured.eq, { col: "category_id", val: "cat-9" });
    } finally {
      server.close();
    }
  });
});

describe("POST /api/videos", () => {
  it("201 + plataforma auto-detectada cuando no se envia", async () => {
    // Arrange
    let sent;
    const created = { id: "v1", titulo: "t", plataforma: "youtube" };
    const fake = {
      from: () => ({
        insert: (row) => {
          sent = row;
          return { select: () => ({ single: async () => ({ data: created, error: null }) }) };
        },
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "t", url: "https://youtu.be/abc" }),
      });
      // Assert
      assert.equal(status, 201);
      assert.equal(body.success, true);
      assert.equal(sent.plataforma, "youtube");
    } finally {
      server.close();
    }
  });

  it("400 con URL invalida (no llega a BD)", async () => {
    // Arrange
    const app = createApp(() => ({ from: () => { throw new Error("no debe llegar a BD"); } }));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "t", url: "nota-url" }),
      });
      // Assert
      assert.equal(status, 400);
      assert.equal(body.success, false);
    } finally {
      server.close();
    }
  });
});

describe("PATCH /api/videos/:id", () => {
  it("200 + actualizado", async () => {
    // Arrange
    const updated = { id: "v1", titulo: "nuevo" };
    const fake = {
      from: () => ({
        update: () => ({ eq: () => ({ select: () => ({ single: async () => ({ data: updated, error: null }) }) }) }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos/v1", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "nuevo" }),
      });
      // Assert
      assert.equal(status, 200);
      assert.deepEqual(body.data, updated);
    } finally {
      server.close();
    }
  });

  it("404 cuando no existe", async () => {
    // Arrange
    const fake = {
      from: () => ({
        update: () => ({ eq: () => ({ select: () => ({ single: async () => ({ data: null, error: { code: "PGRST116" } }) }) }) }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status } = await req(port, "/api/videos/nope", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "x" }),
      });
      // Assert
      assert.equal(status, 404);
    } finally {
      server.close();
    }
  });
});

describe("DELETE /api/videos/:id", () => {
  it("200 + {deleted:true} y 404 si no existe", async () => {
    // Arrange
    const hits = [];
    const fake = {
      from: () => ({
        delete: () => ({
          eq: (_c, v) => ({
            select: async () => (v === "v1" ? { data: [{ id: "v1" }], error: null } : { data: [], error: null }),
          }),
        }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const okRes = await req(port, "/api/videos/v1", { method: "DELETE", headers: AUTH });
      const missRes = await req(port, "/api/videos/nope", { method: "DELETE", headers: AUTH });
      hits.push(okRes.status, missRes.status);
      // Assert
      assert.equal(okRes.status, 200);
      assert.deepEqual(okRes.body.data, { deleted: true });
      assert.equal(missRes.status, 404);
      assert.deepEqual(hits, [200, 404]);
    } finally {
      server.close();
    }
  });
});

describe("vault errores 500 (sin filtrar detalle)", () => {
  it("factoria que lanza -> 500", async () => {
    // Arrange
    const app = createApp(() => { throw new Error("secreto-factoria"); });
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/categories", { headers: AUTH });
      // Assert
      assert.equal(status, 500);
      assert.equal(body.success, false);
      assert.ok(!JSON.stringify(body).includes("secreto-factoria"));
    } finally {
      server.close();
    }
  });

  it("Supabase que lanza en listado -> 500", async () => {
    // Arrange
    const fake = { from: () => { throw new Error("db-secreta"); } };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos", { headers: AUTH });
      // Assert
      assert.equal(status, 500);
      assert.ok(!JSON.stringify(body).includes("db-secreta"));
    } finally {
      server.close();
    }
  });

  it("PATCH video con error no-PGRST116 -> 500", async () => {
    // Arrange
    const fake = {
      from: () => ({
        update: () => ({
          eq: () => ({ select: () => ({ single: async () => ({ data: null, error: { code: "XX500", message: "secreto" } }) }) }),
        }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status, body } = await req(port, "/api/videos/v1", {
        method: "PATCH",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "x" }),
      });
      // Assert
      assert.equal(status, 500);
      assert.ok(!JSON.stringify(body).includes("secreto"));
    } finally {
      server.close();
    }
  });

  it("POST video con fallo de insert -> 500", async () => {
    // Arrange
    const fake = {
      from: () => ({
        insert: () => ({ select: () => ({ single: async () => ({ data: null, error: { message: "boom" } }) }) }),
      }),
    };
    const app = createApp(factoryFor(fake, {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act
      const { status } = await req(port, "/api/videos", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ titulo: "t", url: "https://a.com/x" }),
      });
      // Assert
      assert.equal(status, 500);
    } finally {
      server.close();
    }
  });
});

describe("GET /api/preview (sin auth)", () => {
  it("200 + preview youtube via oEmbed inyectado", async () => {
    // Arrange
    const fetchFn = async () => ({
      ok: true,
      json: async () => ({ title: "T", author_name: "C", thumbnail_url: "https://img/x.jpg" }),
    });
    const app = createApp(() => { throw new Error("preview no usa BD"); }, { fetchFn });
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act (sin header auth)
      const { status, body } = await req(port, "/api/preview?url=https%3A%2F%2Fwww.youtube.com%2Fwatch%3Fv%3Dabc");
      // Assert
      assert.equal(status, 200);
      assert.equal(body.success, true);
      assert.equal(body.data.titulo, "T");
      assert.equal(body.data.plataforma, "youtube");
    } finally {
      server.close();
    }
  });

  it("400 sin url o con url invalida", async () => {
    // Arrange
    const app = createApp(() => { throw new Error("no BD"); });
    const server = await listen(app);
    const port = server.address().port;
    try {
      // Act + Assert
      const missing = await req(port, "/api/preview");
      assert.equal(missing.status, 400);
      const bad = await req(port, "/api/preview?url=nota-url");
      assert.equal(bad.status, 400);
    } finally {
      server.close();
    }
  });
});

describe("POST idempotente (sync multi-dispositivo)", () => {
  const UID = "u-1";
  const ID = "6f1c2b9e-1d3a-4c5b-9e8f-0a1b2c3d4e5f";

  function fakeDuplicate(inserted) {
    return {
      auth: { getUser: async () => ({ data: { user: { id: UID } }, error: null }) },
      from: () => ({
        insert: (row) => {
          inserted.row = row;
          return {
            select: () => ({
              single: async () => ({ data: null, error: { code: "23505", message: "duplicate key value" } }),
            }),
          };
        },
        update: (patch) => ({
          eq: (col, val) => ({
            select: () => ({
              single: async () => ({ data: { id: val, user_id: UID, ...patch }, error: null }),
            }),
          }),
        }),
      }),
    };
  }

  it("conserva el id del cliente al crear un video", async () => {
    const inserted = {};
    const app = createApp(factoryFor(fakeDuplicate(inserted), {}), { publicClient: fakeDuplicate(inserted) });
    const server = await listen(app);
    const port = server.address().port;
    try {
      const r = await req(port, "/api/videos", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body: JSON.stringify({ id: ID, titulo: "x", url: "https://youtu.be/a" }),
      });
      assert.equal(r.status, 200);
      assert.equal(r.body.data.id, ID);
      assert.equal(inserted.row.id, ID);
      assert.equal(inserted.row.user_id, UID);
    } finally {
      server.close();
    }
  });

  it("reintento del mismo id no duplica: responde 200 con la fila", async () => {
    const inserted = {};
    const app = createApp(factoryFor(fakeDuplicate(inserted), {}));
    const server = await listen(app);
    const port = server.address().port;
    try {
      const body = JSON.stringify({ id: ID, nombre: "Musica", color: "#7C3AED" });
      const r1 = await req(port, "/api/categories", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body,
      });
      const r2 = await req(port, "/api/categories", {
        method: "POST",
        headers: { ...AUTH, "Content-Type": "application/json" },
        body,
      });
      assert.equal(r1.status, 200);
      assert.equal(r2.status, 200);
      assert.equal(r1.body.data.id, ID);
      assert.equal(r2.body.data.id, ID);
      assert.equal(inserted.row.nombre, "Musica");
    } finally {
      server.close();
    }
  });
});
