const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const crypto = require("node:crypto");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const PARENT_UID = "parent_alice_123";
const CHILD_UID = "child_sam_456";
const STRANGER_UID = "stranger_eve_789";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function sha256(input) {
  return crypto.createHash("sha256").update(input).digest("hex");
}

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

// --- 1. Unauthenticated access rejection ---
test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(PARENT_UID).get());
  await assertFails(
    unauthDb.collection("devices").where("memberUids", "array-contains", PARENT_UID).get()
  );
  await assertFails(unauthDb.collection("pairingCodes").doc("abc123").get());
});

// --- 2. User profile owner isolation & ghost field test ---
test("User can create own profile; stranger cannot read it; shadow ghost field rejected", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date();

  await assertSucceeds(
    parentDb.collection("users").doc(PARENT_UID).set({
      userId: PARENT_UID,
      displayName: "Parent Alice",
      privacyModeEnabled: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(strangerDb.collection("users").doc(PARENT_UID).get());

  // Shadow update with ghost field 'isAdmin'
  await assertFails(
    parentDb.collection("users").doc(PARENT_UID).update({
      isAdmin: true,
      updatedAt: now,
    })
  );
});

// --- 3. Pairing code security: no list, single-use atomic pairing ---
test("Pairing codes cannot be listed; valid code allows atomic pairing; reuse fails", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date();
  const expiresAt = new Date(Date.now() + 15 * 60 * 1000);
  const codeHash = sha256("7K4P92MX");
  const deviceId = "device_child_01";

  await assertSucceeds(
    parentDb.collection("pairingCodes").doc(codeHash).set({
      parentUid: PARENT_UID,
      label: "Kids Tablet",
      used: false,
      expiresAt,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Listing pairingCodes must always fail
  await assertFails(strangerDb.collection("pairingCodes").get());
  await assertFails(parentDb.collection("pairingCodes").get());

  // Atomic batch pairing by child
  const batch = childDb.batch();
  batch.update(childDb.collection("pairingCodes").doc(codeHash), {
    used: true,
    claimedByUid: CHILD_UID,
    claimedDeviceId: deviceId,
    updatedAt: now,
  });
  batch.set(childDb.collection("devices").doc(deviceId), {
    parentUid: PARENT_UID,
    childAuthUid: CHILD_UID,
    memberUids: [PARENT_UID, CHILD_UID],
    childName: "Sam",
    deviceName: "Pixel 8",
    androidVersion: "15",
    monitoringActive: true,
    usageAccessGranted: true,
    isOnline: true,
    totalScreenTimeMs: 0,
    appsUsedCount: 0,
    activeAlertsCount: 0,
    syncStatus: "pending",
    pairingCodeHash: codeHash,
    createdAt: now,
    updatedAt: now,
  });
  await assertSucceeds(batch.commit());

  // Reusing the same pairing code by stranger must fail
  const reuseBatch = strangerDb.batch();
  reuseBatch.update(strangerDb.collection("pairingCodes").doc(codeHash), {
    used: true,
    claimedByUid: STRANGER_UID,
    claimedDeviceId: "device_evil_02",
    updatedAt: now,
  });
  reuseBatch.set(strangerDb.collection("devices").doc("device_evil_02"), {
    parentUid: PARENT_UID,
    childAuthUid: STRANGER_UID,
    memberUids: [PARENT_UID, STRANGER_UID],
    childName: "Evil",
    deviceName: "Fake",
    androidVersion: "15",
    monitoringActive: true,
    usageAccessGranted: true,
    isOnline: true,
    totalScreenTimeMs: 0,
    appsUsedCount: 0,
    activeAlertsCount: 0,
    syncStatus: "pending",
    pairingCodeHash: codeHash,
    createdAt: now,
    updatedAt: now,
  });
  await assertFails(reuseBatch.commit());
});

// --- 4. Device queries & subcollections (limits parent-only, cross-user isolation) ---
test("Parent & Child access own device; stranger denied; child cannot modify limits", async () => {
  const parentDb = testEnv.authenticatedContext(PARENT_UID).firestore();
  const childDb = testEnv.authenticatedContext(CHILD_UID).firestore();
  const strangerDb = testEnv.authenticatedContext(STRANGER_UID).firestore();
  const now = new Date();
  const deviceId = "device_child_02";

  await assertSucceeds(
    parentDb.collection("devices").doc(deviceId).set({
      parentUid: PARENT_UID,
      childAuthUid: CHILD_UID,
      memberUids: [PARENT_UID, CHILD_UID],
      childName: "Lina",
      deviceName: "Galaxy A55",
      androidVersion: "14",
      monitoringActive: true,
      usageAccessGranted: true,
      isOnline: true,
      totalScreenTimeMs: 3600000,
      appsUsedCount: 4,
      activeAlertsCount: 0,
      syncStatus: "synced",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query with memberUids filter succeeds for parent; unfiltered fails
  await assertSucceeds(
    parentDb.collection("devices").where("memberUids", "array-contains", PARENT_UID).get()
  );
  await assertFails(parentDb.collection("devices").get());
  await assertFails(strangerDb.collection("devices").doc(deviceId).get());
  await assertFails(
    strangerDb.collection("devices").where("memberUids", "array-contains", PARENT_UID).get()
  );

  // Parent creates app limit; child can read it but cannot modify/delete it
  await assertSucceeds(
    parentDb
      .collection("devices")
      .doc(deviceId)
      .collection("limits")
      .doc("com.google.android.youtube")
      .set({
        deviceId,
        parentUid: PARENT_UID,
        memberUids: [PARENT_UID, CHILD_UID],
        packageName: "com.google.android.youtube",
        appName: "YouTube",
        dailyLimitMinutes: 30,
        warningEnabled: true,
        isActive: true,
        createdAt: now,
        updatedAt: now,
      })
  );

  await assertSucceeds(
    childDb
      .collection("devices")
      .doc(deviceId)
      .collection("limits")
      .where("memberUids", "array-contains", CHILD_UID)
      .get()
  );

  await assertFails(
    childDb
      .collection("devices")
      .doc(deviceId)
      .collection("limits")
      .doc("com.google.android.youtube")
      .update({
        dailyLimitMinutes: 999,
        updatedAt: now,
      })
  );

  // Child can write usage stats
  await assertSucceeds(
    childDb
      .collection("devices")
      .doc(deviceId)
      .collection("usage")
      .doc("2026-11-14")
      .set({
        deviceId,
        parentUid: PARENT_UID,
        memberUids: [PARENT_UID, CHILD_UID],
        date: "2026-11-14",
        totalScreenTimeMs: 3600000,
        appsCount: 4,
        topAppName: "YouTube",
        topAppPackage: "com.google.android.youtube",
        createdAt: now,
        updatedAt: now,
      })
  );

  // Stranger cannot read usage
  await assertFails(
    strangerDb
      .collection("devices")
      .doc(deviceId)
      .collection("usage")
      .where("memberUids", "array-contains", PARENT_UID)
      .get()
  );
});
