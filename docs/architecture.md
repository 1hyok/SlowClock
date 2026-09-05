# 아키텍처 규약

Google 앱 아키텍처 가이드(UI → Domain(선택) → Data) 위에 프레젠테이션 계층은 MVI 로 쓴다. 계약은 Afternote-FE(#1811)와 RuleUp-ASM/Android 의 `MviViewModel` 과 같다.

## MVI 계약

`core/ui/src/main/java/com/example/slowclock/ui/mvi/` 에 있다.

```kotlin
abstract class MviViewModel<I : MviIntent, S : UiState, E : ReducerEvent>(initialState: S) : ViewModel() {
    val uiState: StateFlow<S>
    protected val currentState: S
    abstract fun onIntent(intent: I)                     // 화면이 부르는 유일한 진입점
    protected abstract fun reduce(state: S, event: E): S  // 순수 전이
    protected fun dispatch(event: E)                     // 상태를 바꾸는 유일한 수단
}
```

- Intent 는 사용자가 하려는 것(`SaveSchedule`, `ConsumeError`), ReducerEvent 는 상태가 겪은 것(`Loading`, `Loaded`, `SaveFailed`). Intent 하나가 ReducerEvent 를 0개에서 N개까지 낳는다.
- 화면 ViewModel 은 `MutableStateFlow` 를 직접 갖지 않는다. 비동기 작업의 중간 상태도 `dispatch(Loading)` → 호출 → `dispatch(Loaded)` 로 낸다.
- `reduce` 에는 부수효과를 두지 않는다. 저장소 호출·로깅은 `onIntent` 쪽이다.

## 일회성 신호

Effect 타입은 없다. 스낵바 문구·화면 이탈 같은 일회성 신호는 `UiState` 의 nullable 필드로 두고, 화면이 `ObserveSignal(signal, consumed = Intent.ConsumeXxx, onIntent) { }` 로 소비한다. `Channel`·`SharedFlow` 는 쓰지 않는다. 근거: https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events

## 화면 2단

- `XxxScreen(onNavigate..., modifier, viewModel = hiltViewModel())`: 상태를 수집하고 신호를 소비한다. 네비게이션 콜백만 받는다. 네비게이션은 MVI 밖이다.
- `internal fun XxxContent(state, onIntent, modifier)`: 상태만 그린다. 프리뷰·스크린샷 테스트 진입점이다.

## 파일 배치

기능 패키지 안에 `XxxContract.kt`(Intent·UiState·ReducerEvent), `XxxViewModel.kt`, `XxxScreen.kt`. 참조 구현은 `feature/profile` 의 Profile 이다.

## 테스트

Intent 를 넣고 `uiState` 만 본다. 저장소는 mockk, 코루틴은 `Dispatchers.setMain(UnconfinedTestDispatcher())`. 베이스 계약은 `core/ui` 의 `MviViewModelTest` 가 잠근다.
