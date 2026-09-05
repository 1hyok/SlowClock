# R8 규칙. 릴리스 빌드에서 코드 축소와 난독화를 켤 때 지켜야 할 자리를 적는다(#113).
#
# 판정 기준은 하나다. 이름을 리플렉션으로 읽는 곳이 있으면 그 이름을 남겨야 한다.
# 빌드도 테스트도 통과하고 기기에서만 조용히 비는 종류의 고장이라, 여기서 빠지면 늦게 안다.

# ---------------------------------------------------------------------------
# Firestore 문서 매핑
# ---------------------------------------------------------------------------
# Firestore 는 문서를 data class 로 옮길 때 프로퍼티 이름을 리플렉션으로 읽는다. 이름이 바뀌면
# 예외 없이 null 로 채워진다. 모델은 데이터를 담기만 하므로 통째로 남겨도 크기 손해가 거의 없다.
-keep class com.example.slowclock.data.model.** { *; }

# @DocumentId·@PropertyName 은 Firestore 가 런타임에 읽는다. 애노테이션 자체가 지워지면
# 필드 이름을 남겨 두어도 문서 id 가 들어오지 않는다.
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-keep class com.google.firebase.firestore.DocumentId
-keep class com.google.firebase.firestore.PropertyName
-keep class com.google.firebase.firestore.Exclude
-keep class com.google.firebase.firestore.ServerTimestamp

# ---------------------------------------------------------------------------
# kotlinx.serialization
# ---------------------------------------------------------------------------
# Navigation 3 의 화면 키가 @Serializable 이다. 백스택을 저장하고 되살릴 때 serializer 를
# 이름으로 찾으므로, 컴파일러가 만든 $serializer 와 Companion 이 남아야 한다.
-keepattributes InnerClasses
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **$* {
    static **$* *;
}
-keepclassmembers class <1>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.slowclock.**$$serializer { *; }

# 화면 키는 백스택 저장·복원에 이름으로 쓰인다.
-keep class com.example.slowclock.navigation.** { *; }

# ---------------------------------------------------------------------------
# 진단
# ---------------------------------------------------------------------------
# 크래시 보고서의 줄 번호를 남긴다. 매핑 파일이 원본 파일명을 되돌려 준다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
