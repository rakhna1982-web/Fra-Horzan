const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_driver_123";
const BOB_UID = "bob_driver_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write driver profile, garage, or records", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Rogue",
      credits: 25000,
      xp: 0,
      level: 1,
      selectedCarId: "vortex_gtr",
      totalDriftScore: 0,
      topSpeedKmh: 0,
      boardsSmashed: 0,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("garage").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("records").get()
  );
});

test("Driver profile: valid create and partial update succeed; ghost fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Horizon",
      credits: 25000,
      xp: 0,
      level: 1,
      selectedCarId: "vortex_gtr",
      totalDriftScore: 0,
      topSpeedKmh: 0,
      boardsSmashed: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update mirroring Kotlin repository update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 28500,
      xp: 450,
      level: 2,
      totalDriftScore: 14200,
      topSpeedKmh: 268,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      credits: 999999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Immutable createdAt change must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Shadow/ghost field injection must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Future timestamp tampering must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      credits: 30000,
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Garage subcollection: valid create, list, upgrade/paint update; invalid bounds & @DocumentId 'id' rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garage")
      .doc("vortex_gtr")
      .set({
        userId: ALICE_UID,
        carId: "vortex_gtr",
        paintHex: "#FF4D6D",
        engineLevel: 1,
        tiresLevel: 1,
        nitroLevel: 1,
        createdAt: created,
        updatedAt: created,
      })
  );

  // List subcollection succeeds for owner, fails for other user
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("garage").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("garage").get()
  );

  // Valid paint + engine upgrade update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garage")
      .doc("vortex_gtr")
      .update({
        paintHex: "#00E5FF",
        engineLevel: 2,
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Out-of-bounds engineLevel (>5) must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garage")
      .doc("vortex_gtr")
      .update({
        engineLevel: 6,
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Invalid paint hex must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garage")
      .doc("vortex_gtr")
      .update({
        paintHex: "red",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Including @DocumentId 'id' in payload must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("garage")
      .doc("baja_raptor")
      .set({
        id: "baja_raptor",
        userId: ALICE_UID,
        carId: "baja_raptor",
        paintHex: "#FFB703",
        engineLevel: 1,
        tiresLevel: 1,
        nitroLevel: 1,
        createdAt: created,
        updatedAt: created,
      })
  );
});

test("Records subcollection: valid create, list, update; invalid zoneType & stars rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("records")
      .doc("canyon_speed_trap")
      .set({
        userId: ALICE_UID,
        zoneId: "canyon_speed_trap",
        zoneType: "SPEED_TRAP",
        bestValue: 245,
        stars: 2,
        carId: "vortex_gtr",
        createdAt: created,
        updatedAt: created,
      })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").get()
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("records")
      .doc("canyon_speed_trap")
      .update({
        bestValue: 298,
        stars: 3,
        carId: "vortex_gtr",
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Invalid zoneType must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("records")
      .doc("bad_zone")
      .set({
        userId: ALICE_UID,
        zoneId: "bad_zone",
        zoneType: "UNKNOWN_TYPE",
        bestValue: 100,
        stars: 1,
        carId: "vortex_gtr",
        createdAt: created,
        updatedAt: created,
      })
  );

  // Stars > 3 must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("records")
      .doc("canyon_speed_trap")
      .update({
        stars: 4,
        updatedAt: new Date(Date.now() - 500),
      })
  );
});
