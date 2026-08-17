import { initializeApp } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import { getDatabase } from 'firebase/database';

const firebaseConfig = {
  apiKey: "AIzaSyCb-XvC0Ru-Q0uYd54lCu0YoBiaBS2IVYE",
  authDomain: "d-ledger-8c1e4.firebaseapp.com",
  databaseURL: "https://d-ledger-8c1e4-default-rtdb.asia-southeast1.firebasedatabase.app",
  projectId: "d-ledger-8c1e4",
  storageBucket: "d-ledger-8c1e4.firebasestorage.app",
  messagingSenderId: "304945823090",
  appId: "1:304945823090:android:ddcd8e2401a80cdf0071eb"
};

const app = initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const db = getDatabase(app);
