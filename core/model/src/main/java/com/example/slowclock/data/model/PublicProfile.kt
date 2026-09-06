package com.example.slowclock.data.model

/**
 * 다른 사용자에게 보여도 되는 값만 담는다. 공유 일정의 소유자 이름을 보여 주는 데 쓴다.
 *
 * `users` 문서에는 이메일과 FCM 토큰이 함께 있어 통째로 열 수 없다. Firestore 는 필드 단위 읽기
 * 제한이 없으므로 공개할 값만 따로 둔다.
 *
 * 공유 코드는 여기에 두지 않는다. 사람을 찾는 열쇠라 이름 옆에 둘 값이 아니고, 코드를 문서 이름으로
 * 삼는 등록부(`shareCodes`)가 그 자리를 대신한다(#174).
 */
data class PublicProfile(
    val id: String = "",
    val name: String = "",
)
