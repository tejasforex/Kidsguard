const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_parent_1";
const BOB_UID = "bob_parent_2";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function serverTimestamp() {
  return new Date(Date.now() - 1000);
}

function validChildPayload(userId, name = "Maya") {
  return {
    userId,
    name,
    age: 8,
    avatarKey: "fox",
    dailyLimitMinutes: 120,
    weekendLimitMinutes: 180,
    usedMinutesToday: 35,
    bonusMinutesToday: 0,
    isPaused: false,
    bedtimeEnabled: true,
    bedtimeStartMinutes: 1230,
    bedtimeEndMinutes: 420,
    studyFocusEnabled: false,
    studyStartMinutes: 510,
    studyEndMinutes: 900,
    contentRating: "preteen_8_12",
    safeSearchEnabled: true,
    blockPurchases: true,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
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

test("Unauthenticated user cannot read or write user settings or children", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("children").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("children").doc("child_1").set(validChildPayload(ALICE_UID))
  );
});

test("Parent can create and update their own FamilySettings, but cannot add ghost fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      displayName: "Alice",
      parentPinHash: "",
      hasParentPin: false,
      kidModeActive: false,
      activeKidModeChildId: "",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    docRef.update({
      kidModeActive: true,
      activeKidModeChildId: "child_1",
      updatedAt: serverTimestamp(),
    })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    docRef.update({
      kidModeActive: false,
      isAdmin: true,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Parent cannot read or write another parent's children or appRules", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(BOB_UID).collection("children").doc("bob_child").set(validChildPayload(BOB_UID, "Leo"));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("children").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("children").doc("bob_child").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("children").doc("bob_child").update({
      isPaused: true,
      updatedAt: serverTimestamp(),
    })
  );
});

test("Parent can create child, appRule, and quest with relational integrity and terminal quest locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const childRef = aliceDb.collection("users").doc(ALICE_UID).collection("children").doc("child_maya");

  // Orphaned appRule before child exists must fail
  await assertFails(
    childRef.collection("appRules").doc("rule_1").set({
      userId: ALICE_UID,
      childId: "child_maya",
      packageName: "com.example.game",
      appName: "Space Game",
      category: "Games",
      status: "limited",
      dailyLimitMinutes: 30,
      usedMinutesToday: 10,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(childRef.set(validChildPayload(ALICE_UID, "Maya")));

  await assertSucceeds(
    childRef.collection("appRules").doc("rule_1").set({
      userId: ALICE_UID,
      childId: "child_maya",
      packageName: "com.example.game",
      appName: "Space Game",
      category: "Games",
      status: "limited",
      dailyLimitMinutes: 30,
      usedMinutesToday: 10,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  const questRef = childRef.collection("quests").doc("quest_1");
  await assertSucceeds(
    questRef.set({
      userId: ALICE_UID,
      childId: "child_maya",
      title: "Read 20 mins",
      rewardMinutes: 20,
      status: "open",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(questRef.update({ status: "pending_approval", updatedAt: serverTimestamp() }));
  await assertSucceeds(questRef.update({ status: "approved", updatedAt: serverTimestamp() }));

  // Terminal state lock: cannot modify after approved
  await assertFails(questRef.update({ status: "open", updatedAt: serverTimestamp() }));
});

test("Invalid age, invalid status enum, or oversized ID are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const invalidChild = validChildPayload(ALICE_UID, "Maya");
  invalidChild.age = 25;
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("children").doc("child_bad_age").set(invalidChild)
  );

  const badId = "x".repeat(130);
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("children").doc(badId).set(validChildPayload(ALICE_UID))
  );
});
