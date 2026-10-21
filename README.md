# followUpFin

팔로우한 종목의 뉴스와 공시를 한곳에 모아 계속 보여주는 서비스.

- 미국 종목: 티커만 입력하면 회사명·업종을 자동으로 채우고, [Finnhub](https://finnhub.io) 회사 뉴스를 수집
- 국내 종목: [DART](https://opendart.fss.or.kr) 공시 + 네이버 뉴스 검색
- 업종별 묶음 피드, 10분마다 수집, 같은 기사·공시는 한 번만 저장
- 공식 API만 사용. 기사 본문은 저장하지 않고 제목·요약·원문 링크만 보여준다

## 실행

```bash
cp secret.example.yml secret.yml   # 발급받은 API 키 입력 (git에 올라가지 않음)
./gradlew bootRun
```

http://localhost:8080

필요한 키: Finnhub(미국 뉴스), DART(국내 공시). 네이버는 국내 뉴스용으로 선택.
서버에서는 파일 대신 환경변수로 넣어도 된다 (`FINNHUB_API_KEY`, `DART_API_KEY`).

## 스택

Java 21 · Spring Boot 4 · Spring Data JPA · H2(개발) · Vanilla JS

## 다음 단계

- LLM으로 한국어 요약·번역, 종목 관련성 판단, 호재/악재 분류
- 여러 종목에 걸린 같은 기사 합치기
- KIS 보유 종목 연동, MariaDB, 서버 배포
