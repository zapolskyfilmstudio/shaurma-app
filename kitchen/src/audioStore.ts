export type SoundKey = "new" | "alarm";

const DB_NAME = "kitchen_audio";
const STORE_NAME = "sounds";
const DB_VERSION = 1;

type StoredSound = {
  data: ArrayBuffer;
  mimeType: string;
  fileName: string;
};

export type SoundStatus = {
  loaded: boolean;
  fileName: string | null;
};

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME);
      }
    };
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
  });
}

async function readStoredSound(key: SoundKey): Promise<StoredSound | null> {
  const db = await openDb();
  const raw = await new Promise<unknown>((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, "readonly");
    const request = transaction.objectStore(STORE_NAME).get(key);
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
  db.close();

  if (!raw) return null;

  if (raw instanceof File || raw instanceof Blob) {
    const data = await raw.arrayBuffer();
    return {
      data,
      mimeType: raw.type || "audio/mpeg",
      fileName: raw instanceof File ? raw.name : `${key}.mp3`,
    };
  }

  const stored = raw as Partial<StoredSound>;
  if (stored.data instanceof ArrayBuffer) {
    return {
      data: stored.data,
      mimeType: stored.mimeType || "audio/mpeg",
      fileName: stored.fileName || `${key}.mp3`,
    };
  }

  return null;
}

export async function saveSound(key: SoundKey, file: File): Promise<void> {
  const stored: StoredSound = {
    data: await file.arrayBuffer(),
    mimeType: file.type || "audio/mpeg",
    fileName: file.name,
  };

  const db = await openDb();
  await new Promise<void>((resolve, reject) => {
    const transaction = db.transaction(STORE_NAME, "readwrite");
    transaction.objectStore(STORE_NAME).put(stored, key);
    transaction.oncomplete = () => resolve();
    transaction.onerror = () => reject(transaction.error);
  });
  db.close();
}

export async function loadSoundUrl(key: SoundKey): Promise<string | null> {
  const stored = await readStoredSound(key);
  if (!stored) return null;
  const blob = new Blob([stored.data], { type: stored.mimeType || "audio/mpeg" });
  return URL.createObjectURL(blob);
}

export async function getSoundStatus(key: SoundKey): Promise<SoundStatus> {
  const stored = await readStoredSound(key);
  return {
    loaded: stored !== null,
    fileName: stored?.fileName ?? null,
  };
}
