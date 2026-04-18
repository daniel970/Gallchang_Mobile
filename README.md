# DcinsideCrawlerMobileV2 — 갤창랭킹 모바일

`dcinisde-crawler.ver.2`(WinForms) 프로젝트의 모바일(Android) 버전입니다.
특정 디시인사이드 갤러리의 **사용자별 글 수(갤창 랭킹)** 를 모바일에서 바로 조회합니다.

## 특징

- 파일 저장 없이 **앱 내 리스트**로 바로 조회 (JSON/TSV/HTML 저장 기능 없음)
- 갤러리 URL 또는 갤 ID만 입력하면 타입(`mgallery` / `board` / `mini`) 자동 판별
- 페이지 범위 단위 크롤링, 실패 시 재시도 + 지수 백오프
- 사용자별 **글 수 / 지분% / 댓글·조회·추천 합계** 랭킹 카드 리스트
- 동점자는 같은 순위로 표시(1, 1, 3, 4 …)

## 결과 화면 구성

1. **입력 카드** — 갤러리 URL/ID, 시작/끝 페이지, 요청 간격(ms)
2. **진행 카드** — 현재 상태(대기/수집 중/완료/중단됨), 진행률, 최근 로그, 집계 요약
3. **랭킹 리스트** — 순위 · 닉 · ID/IP · 글 수 · 갤 지분% · 보조 통계

## 빌드

Android Studio에서 이 폴더(`DcinsideCrawlerMobileV2`)를 열고 빌드하세요.

워크스페이스 내 Gradle Wrapper를 재사용한 명령줄 빌드:

```powershell
..\CommentBotMobile\gradlew.bat -p . assembleDebug
```

산출물:

- `app/build/outputs/apk/debug/app-debug.apk`

## 사용

1. **갤러리 URL 또는 갤 ID** 입력
   - 예: `baseball_new11`
   - 예: `https://gall.dcinside.com/mgallery/board/lists/?id=baseball_new11`
2. **시작 / 끝 페이지** 입력 (1 이상, 시작 ≤ 끝)
3. **요청 간격(ms)** 입력 (권장 1000~1500ms, 차단 방지)
4. **크롤 시작** 탭 → 페이지별 진행이 카드에 표시되고, 완료 시 랭킹 리스트가 렌더링됩니다
5. 수집 중 **중지**를 눌러 즉시 취소 가능

## 주요 모듈

- `DcClient.kt` — OkHttp 기반 목록 HTML 가져오기 + 갤 타입 자동 판별 (캐시)
- `DcParser.kt` — Jsoup으로 목록 행(`tr.ub-content`) 파싱, 공지/숫자 혼합 문자열 견고 처리
- `CrawlerEngine.kt` — 코루틴 기반 페이지 순회, 재시도·백오프, 사용자별 집계
- `RateLimiter.kt` — 연속 실패 시 지수 백오프
- `GalleryUrlParser.kt` — URL 또는 ID에서 `board_id` 추출
- `UserRankAdapter.kt` / `UserRankMapper` — 랭킹 → UI 아이템 변환, RecyclerView 어댑터
- `MainActivity.kt` — 입력/진행/결과 3섹션 UI

## 알려진 제약

- 목록 페이지만 파싱하므로 개별 글 본문·댓글 내용은 수집하지 않습니다
- 유동 닉·통피(통신사 IP) 자동 병합은 포함하지 않습니다(원본 C# 버전의 `DataToText` 로직)
- 권한이 필요 없는 `INTERNET` 만 사용합니다
