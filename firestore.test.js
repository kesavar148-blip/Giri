const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function nowTs() {
  return new Date(Date.now() - 2000);
}

function validPrefs(uid) {
  const ts = nowTs();
  return {
    userId: uid,
    displayName: "Tony Stark",
    preferredName: "Sir",
    city: "Malibu",
    wakeWord: "Hey Jarvis",
    personality: "butler",
    customInstructions: "Keep telemetry concise.",
    selectedModel: "gemini-3.8-flash",
    memoryEnabled: true,
    autoSpeakEnabled: true,
    confirmActionsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validTask(uid) {
  const ts = nowTs();
  return {
    userId: uid,
    title: "Calibrate arc reactor",
    notes: "Check palladium output",
    priority: "high",
    dueLabel: "Today 5:00 PM",
    completed: false,
    createdAt: ts,
    updatedAt: ts,
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

test("Unauthenticated user cannot read or write tasks or preferences", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("messages").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("notes").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
});

test("Owner can create, read, and update their UserPreferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(docRef.set(validPrefs(ALICE_UID)));
  await assertSucceeds(docRef.get());
  await assertSucceeds(docRef.update({ wakeWord: "Jarvis", updatedAt: nowTs() }));
});

test("Shadow update with ghost field on UserPreferences is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb.collection("users").doc(ALICE_UID);
  await assertSucceeds(docRef.set(validPrefs(ALICE_UID)));
  await assertFails(docRef.update({ isAdmin: true, updatedAt: nowTs() }));
});

test("Cross-user access to tasks, notes, messages, and memories is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set(validTask(ALICE_UID)));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_2").set(validTask(BOB_UID)));
});

test("Owner can manage tasks, notes, messages, and memories while invalid payloads fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const userDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Tasks
  const taskRef = userDoc.collection("tasks").doc("t_1");
  await assertSucceeds(taskRef.set(validTask(ALICE_UID)));
  await assertSucceeds(taskRef.update({ completed: true, updatedAt: nowTs() }));
  await assertFails(taskRef.update({ userId: BOB_UID, updatedAt: nowTs() }));

  // Notes
  const noteRef = userDoc.collection("notes").doc("n_1");
  await assertSucceeds(noteRef.set({
    userId: ALICE_UID,
    title: "Flight Log",
    content: "Mark VII thrusters nominal.",
    tag: "engineering",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));
  await assertSucceeds(noteRef.update({ content: "Mark VII thrusters upgraded.", updatedAt: nowTs() }));

  // Messages (create and delete allowed, update forbidden)
  const msgRef = userDoc.collection("messages").doc("m_1");
  await assertSucceeds(msgRef.set({
    userId: ALICE_UID,
    role: "user",
    content: "Hey Jarvis, run diagnostics.",
    attachmentLabel: "",
    modelUsed: "gemini-3.8-flash",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));
  await assertFails(msgRef.update({ content: "Tampered", updatedAt: nowTs() }));
  await assertSucceeds(msgRef.delete());

  // Memories
  const memRef = userDoc.collection("memories").doc("mem_1");
  await assertSucceeds(memRef.set({
    userId: ALICE_UID,
    key: "coffee_order",
    value: "Black espresso, double shot",
    createdAt: nowTs(),
    updatedAt: nowTs(),
  }));
  await assertSucceeds(memRef.delete());
});
