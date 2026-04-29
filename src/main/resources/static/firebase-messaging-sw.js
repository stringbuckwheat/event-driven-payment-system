importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

console.log('[SW] 로드됨');

firebase.initializeApp({
                         apiKey: "AIzaSyB0roGGhjnsGX9-PWa6-GiLBFH8xW-hHkg",
                         authDomain: "edps-d865a.firebaseapp.com",
                         projectId: "edps-d865a",
                         storageBucket: "edps-d865a.firebasestorage.app",
                         messagingSenderId: "538962144191",
                         appId: "1:538962144191:web:53f7dc6f0a9c3ea84a0f0a"
                       });

console.log('[SW] Firebase 초기화됨');

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  console.log('[SW] 백그라운드 메시지 수신', payload);
  const title = payload.data?.title ?? '알림';
  const body = payload.data?.body ?? '';
  self.registration.showNotification(title, { body });
});