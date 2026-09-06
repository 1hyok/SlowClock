const admin = require("firebase-admin");
const {onDocumentWritten} = require("firebase-functions/v2/firestore");

admin.initializeApp();

exports.sendFcmToShareCodeWatchers = onDocumentWritten(
    {document: "schedules/{scheduleId}"},
    async (event) => {
      if (!event.data) return null;
      const before = event.data.before.data();
      const after = event.data.after.data();
      // 삭제 이벤트에는 after 데이터가 없다. 공유 대상과 제목은 삭제 전 값에서 읽는다.
      const schedule = after || before;
      if (!schedule || typeof schedule.sharedCode !== "string" ||
          !schedule.sharedCode.trim() ||
          !event.params || typeof event.params.scheduleId !== "string" ||
          !event.params.scheduleId.trim()) return null;

      const tokensSnapshot = await admin
          .firestore()
          .collection("shareCodeWatchers")
          .doc(schedule.sharedCode)
          .collection("tokens")
          .get();
      const recipients = new Map();
      tokensSnapshot.forEach((doc) => {
        const token = doc.data().fcmToken;
        // 문서의 userId 필드는 소유자가 쓸 수 있다. 수신 UID는 규칙이 보호하는 경로에서 읽는다.
        if (typeof doc.id === "string" && doc.id.trim() &&
            typeof token === "string" && token.trim()) {
          recipients.set(JSON.stringify([doc.id, token]), {uid: doc.id, token});
        }
      });
      if (recipients.size === 0) return null;

      let notificationTitle = "일정이 변경되었습니다";
      if (!event.data.before.exists) {
        notificationTitle = "일정이 추가되었습니다";
      } else if (!event.data.after.exists) {
        notificationTitle = "일정이 삭제되었습니다";
      } else if (before.completed !== after.completed) {
        notificationTitle = after.completed === true ?
          "완료되었습니다" : "상태 미완료로 바꿨습니다";
      }

      // data-only 메시지만 보내야 background에서도 앱이 현재 로그인/공유 코드를 검사한다.
      // sendEach는 호출마다 최대 500개다. 서로 다른 UID의 동일 토큰은 별개 수신자다.
      // https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
      const messages = [...recipients.values()].map(({uid, token}) => ({
        token,
        data: {
          type: "shared_schedule",
          schemaVersion: "1",
          recipientUid: uid,
          shareCode: schedule.sharedCode,
          scheduleId: event.params.scheduleId,
          title: notificationTitle,
          body: typeof schedule.title === "string" ? schedule.title : "",
        },
        android: {priority: "high"},
      }));
      let successCount = 0;
      let failureCount = 0;
      for (let offset = 0; offset < messages.length; offset += 500) {
        const result = await admin.messaging().sendEach(
            messages.slice(offset, offset + 500),
        );
        successCount += result.successCount;
        failureCount += result.failureCount;
      }
      console.log("공유 일정 알림 전송 결과", {successCount, failureCount});
      return {successCount, failureCount};
    },
);
