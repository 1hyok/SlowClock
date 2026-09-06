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
      if (!schedule || !schedule.sharedCode) return null;

      const tokensSnapshot = await admin
          .firestore()
          .collection("shareCodeWatchers")
          .doc(schedule.sharedCode)
          .collection("tokens")
          .get();
      const uniqueTokens = new Set();
      tokensSnapshot.forEach((doc) => {
        const token = doc.data().fcmToken;
        if (typeof token === "string" && token.trim()) uniqueTokens.add(token);
      });
      const tokens = [...uniqueTokens];
      if (tokens.length === 0) return null;

      let notificationTitle = "일정이 변경되었습니다";
      if (!event.data.before.exists) {
        notificationTitle = "일정이 추가되었습니다";
      } else if (!event.data.after.exists) {
        notificationTitle = "일정이 삭제되었습니다";
      } else if (before.completed !== after.completed) {
        notificationTitle = after.completed === true ?
          "완료되었습니다" : "상태 미완료로 바꿨습니다";
      }

      // multicast는 호출마다 최대 500개다. 토큰이나 일정 내용을 로그에 남기지 않는다.
      // https://firebase.google.com/docs/cloud-messaging/send/admin-sdk
      let successCount = 0;
      let failureCount = 0;
      for (let offset = 0; offset < tokens.length; offset += 500) {
        const result = await admin.messaging().sendEachForMulticast({
          notification: {
            title: notificationTitle,
            body: typeof schedule.title === "string" ? schedule.title : "",
          },
          tokens: tokens.slice(offset, offset + 500),
        });
        successCount += result.successCount;
        failureCount += result.failureCount;
      }
      console.log("공유 일정 알림 전송 결과", {successCount, failureCount});
      return {successCount, failureCount};
    },
);
