# 갤창랭킹 모바일 (Gallchang_Mobile)

특정 디시인사이드 갤러리의 **사용자별 글/댓글 랭킹**을 모바일에서 바로 조회하는 Android 앱입니다.

## 다운로드

최신 APK를 아래 링크에서 바로 받을 수 있습니다. (Android 7.0+ / `minSdk 24`)

- [갤창랭킹_모바일.apk](https://github.com/daniel970/Gallchang_mobile/raw/main/release/%EA%B0%A4%EC%B0%BD%EB%9E%AD%ED%82%B9_%EB%AA%A8%EB%B0%94%EC%9D%BC.apk)

설치 시 "출처를 알 수 없는 앱" 설치를 허용해야 합니다.

## 주요 기능

- **범위 선택**: 페이지 기준 또는 **날짜 기준**으로 수집 (토글 전환)
- **랭킹 모드**: 작성글 랭킹 / 댓글 랭킹 선택 가능
- **로그인 사용자만 집계**: 고정닉(로그인 사용자)만 취합하며, 유동(비로그인 IP)은 자동 제외
- **빠른 수집**: 페이지당 100개 요청 + 동시 요청(기본 3 워커)로 최적화
- **백그라운드 지속 수집**: 포그라운드 서비스 + 알림으로 앱을 내려도 중단되지 않음
- **진행률 %**: 날짜 수집 시에도 결정적 진행률 표시
- **결과 UI**: 50개씩 페이지네이션 + **닉네임 실시간 검색**
- **기록(히스토리)**: 수집 결과를 자동 저장 → 언제든 다시 열람 · 개별/전체 삭제
- 파일로 저장하거나 공유하는 기능은 없습니다 (앱 내 조회 전용)

## 화면 구성

1. **입력 카드** — 갤 ID, 범위(페이지/날짜), 랭킹 모드
2. **진행 카드** — 상태(대기/수집 중/완료/중단), 진행률, 최근 로그, 집계 요약
3. **랭킹 리스트** — 순위 · 닉 · ID · 주지표(글수 또는 댓글수) · 지분% · 보조 통계
4. **기록** — 툴바 메뉴에서 진입, 과거 수집 결과 목록/상세

## 사용

1. 갤 ID 입력 (영문/숫자/밑줄만, URL은 지원하지 않음)
2. **범위 선택**
   - 페이지 모드: 시작/끝 페이지 (1 이상, 시작 ≤ 끝)
   - 날짜 모드: 시작/끝 날짜 선택
3. **랭킹 모드 선택**: 작성글 또는 댓글
4. **크롤 시작** → 진행 카드에서 진행률 확인, 완료 시 랭킹 리스트 표시
5. 랭킹에서 닉네임 검색 또는 이전/다음 페이지 이동
6. 수집 중에는 **중지** 탭으로 즉시 취소 가능
7. 툴바의 **기록** 아이콘으로 과거 결과 재열람

## 빌드

Android Studio에서 이 폴더를 열고 빌드하거나, 동일 워크스페이스의 Gradle Wrapper로 명령줄 빌드:

```powershell
..\CommentBotMobile\gradlew.bat -p . :app:assembleDebug -x test
```

산출물 경로:

- `app/build/outputs/apk/debug/app-debug.apk`

## 주요 모듈

- `DcClient.kt` — OkHttp 기반 목록 페이지 요청 + 갤 타입(`mgallery`/`board`/`mini`) 자동 판별 (캐시)
- `DcParser.kt` — Jsoup으로 목록 행 파싱, 공지/숫자 혼합 문자열 견고 처리
- `ArticleDate.kt` — 작성일 문자열을 `LocalDate`로 정규화
- `CrawlerEngine.kt` — 코루틴 기반 페이지 순회, 동시 요청(`Semaphore`), 재시도·백오프, 사용자별 집계
- `CrawlerController.kt` — 엔진 수명 관리 + UI 상태 스냅샷(Activity 재생성 대응)
- `CrawlerService.kt` — 포그라운드 서비스 + 진행 알림
- `CrawlHistory.kt` — 히스토리 저장/조회/삭제 (앱 내부 저장소 JSON)
- `UserRankAdapter.kt` — 모드별 주지표/지분% 전환, RecyclerView 어댑터
- `MainActivity` / `HistoryActivity` / `HistoryDetailActivity` — 3개 화면 UI

## 권한

- `INTERNET` — 목록 페이지 요청
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` — 백그라운드 지속 수집 (Android 10+)
- `POST_NOTIFICATIONS` — 진행 알림 (Android 13+, 런타임 요청)

## 알려진 제약

- 목록 페이지만 파싱하므로 개별 글 본문·댓글 본문은 수집하지 않습니다
- **반고정닉**은 HTML 상 유동과 구분되지 않아 제외됩니다 (DC 사이트 특성)
- 유동 닉·통피 자동 병합은 지원하지 않습니다
