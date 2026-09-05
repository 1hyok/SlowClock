package com.example.slowclock.ui.mvi

/**
 * View → ViewModel. 사용자가 **하려는 것**이다 — `SaveSchedule` · `SelectDate` · `ConsumeError`.
 *
 * [MviViewModel.onIntent] 가 유일한 진입점이라, 화면이 부를 수 있는 것은 이 타입뿐이다.
 * Intent 하나가 [ReducerEvent] 를 0개에서 N개까지 낳는다. 네비게이션만 하는 Intent 는 0개,
 * 로드 Intent 는 `Loading` → `Loaded` 2개다.
 *
 * ## 왜 Effect 타입이 없는가
 *
 * MVI 가 요구하는 것은 「일회성 효과를 상태 전이에서 분리한다」 까지고, 그것을 `Channel` 로
 * 나르는 것은 참조 구현(RuleUp-ASM/Android)의 선택이다. 이 저장소는 ViewModel 이벤트를
 * `Channel` 로 쏘지 않고 [UiState] 의 nullable 신호로 흡수한다. producer(ViewModel)가
 * consumer(UI)보다 오래 사는 순간 `Channel` 은 전달을 보장하지 못하기 때문이다(구성 변경 ·
 * 프로세스 사망 — https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events).
 *
 * 그래서 베이스는 3타입이다. 일회성 신호는 [UiState] 안의 nullable 필드로 두고 소비는
 * `Intent.ConsumeXxx` 로 들어온다. 소비를 화면마다 `onXxxConsumed()` public fun 으로
 * 노출하면 배선을 빠뜨려도 컴파일이 통과한다. 소비 관용구는 [ObserveSignal] 이 정본이다.
 *
 * Afternote-FE #1800 판정과 같다.
 */
interface MviIntent

/**
 * ViewModel → View. 화면이 그리는 데 필요한 전부를 담는 단일 상태다.
 *
 * 일회성 신호도 nullable 필드로 여기에 함께 담는다. 별도 스트림을 두지 않는다.
 */
interface UiState

/**
 * [MviViewModel.reduce] 의 입력. 상태가 **겪은 것**이다 — `Loading` · `Loaded` · `SaveFailed`.
 *
 * 화면은 이 타입을 만들지 않는다. [MviIntent] 를 받은 ViewModel 만 [MviViewModel.dispatch] 로
 * 낸다. 이 분리가 없으면 비동기 중간 상태를 표현할 곳이 없어 다시 `_uiState.value = ...` 로
 * 돌아간다.
 */
interface ReducerEvent
