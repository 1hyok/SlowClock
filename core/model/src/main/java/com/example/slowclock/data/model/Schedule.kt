package com.example.slowclock.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

// Schedule 모델
data class Schedule(
    @DocumentId val id: String = "",
    val userId: String = "",
    val familyGroupId: String = "",
    val sharedCode: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: Timestamp = Timestamp.now(),
    val endTime: Timestamp? = null,
    val completed: Boolean = false,
    val recurring: Boolean = false,
    val recurringType: String? = null,
    /**
     * 반복 일정에서 이미 끝낸 회차의 날짜(yyyy-MM-dd).
     *
     * `completed` 는 문서에 하나뿐이라 반복 일정에 쓰면 한 번 완료한 뒤 영영 완료로 남는다.
     * 그래서 되풀이하는 일정의 완료 여부는 이 목록이 정한다(#130).
     */
    val completedDates: List<String> = emptyList(),
    val createdAt: Timestamp = Timestamp.now(),
    val updatedAt: Timestamp = Timestamp.now(),
    /**
     * 이 값이 어느 회차를 가리키는지(yyyy-MM-dd). 화면에 내려보낼 때 저장소가 채운다.
     *
     * 문서에는 저장하지 않는다. 문서 하나가 여러 회차로 펼쳐지므로 이 값은 문서의 속성이 아니라
     * 「지금 보고 있는 회차」 다. `@get:Exclude` 가 없으면 일정을 고칠 때마다 문서에 섞여 들어간다.
     */
    @get:Exclude val occurrenceDate: String = "",
)
