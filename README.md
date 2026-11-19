# followUpFin

팔로우한 종목의 뉴스와 공시를 한곳에 모아 계속 보여주는 서비스.

- 미국 종목: 티커만 입력하면 회사명·업종을 자동으로 채우고, [Finnhub](https://finnhub.io) 회사 뉴스를 수집
- 국내 종목: [DART](https://opendart.fss.or.kr) 공시 + 구글 뉴스 RSS 검색 (키 불필요)
- 업종별 묶음 피드, 10분마다 수집, 같은 기사·공시는 한 번만 저장
- 공식 API만 사용. 기사 본문은 저장하지 않고 제목·요약·원문 링크만 보여준다

## 실행

```bash
cp secret.example.yml secret.yml   # 발급받은 API 키 입력 (git에 올라가지 않음)
docker compose up -d               # PostgreSQL
./gradlew bootRun                  # 화면(frontend/)도 npm으로 빌드해서 함께 띄운다 (Node 필요)
```

화면만 고칠 때는 bootRun(8080)을 켜 둔 채로 `cd frontend && npm run dev` → http://localhost:5173 (API·SSE는 8080으로 넘긴다, 저장하면 바로 반영). 지표 계산 테스트는 `npm test`.

H2(`./data/briefing`)를 쓰던 예전 버전의 관심종목·뉴스는 PostgreSQL로 옮겨지지 않는다. 종목은 다시 추가하면 뉴스는 다음 수집 때 다시 쌓인다.

http://localhost:8080

필요한 키: Finnhub(미국 뉴스), DART(국내 공시). 국내 뉴스는 키가 필요 없다.

## 상시 실행

수집은 앱이 켜져 있는 동안만 10분마다 돈다. 꺼져 있던 동안의 공시는 어제·오늘 것만 다시 확인하고, 구글 뉴스는 최근 하루치만 가져온다.

**이 맥** (회사 서버 전 임시): `./deploy/mac-install.sh` 한 번. 로그인하면 켜지고 죽으면 다시 켠다. 코드를 바꾼 뒤 다시 실행하면 새 버전으로 바뀐다. 상시 실행 중에는 8080이 차 있어 `bootRun`을 띄우려면 먼저 끈다 (`launchctl bootout gui/$(id -u)/com.bongj9.followupfin`).

**회사 서버** (리눅스, Java 21 · PostgreSQL 16):

```bash
./gradlew bootJar                                   # 이 맥에서 빌드 (화면 포함)
scp build/libs/followUpFin-0.0.1-SNAPSHOT.jar 서버:/tmp/followUpFin.jar
# 서버에서
sudo useradd -r -s /usr/sbin/nologin followupfin
sudo install -d -o followupfin -m 700 /opt/followupfin
sudo install -o followupfin -m 600 /tmp/followUpFin.jar /opt/followupfin/followUpFin.jar
sudo install -o followupfin -m 600 secret.yml /opt/followupfin/secret.yml   # 키 + spring.datasource.url/password (서버 DB)
sudo cp deploy/followupfin.service /etc/systemd/system/ && sudo systemctl daemon-reload
sudo systemctl enable --now followupfin            # 로그: journalctl -u followupfin -f
```

- 키는 `secret.yml` 한 파일에 두고 앱 계정만 읽게 한다. Anthropic 키는 이 앱 전용으로 만들고 콘솔에서 월 지출 한도를 건다.
- 서버에서 DART · 토스 · Finnhub · 구글 뉴스 · SEC · Anthropic API로 나가는 길이 열려 있어야 한다 (사내망 방화벽 확인).
- 영상 요약·아침 리포트(`/views/*.json`)는 이 맥의 볼트 파일이라 서버엔 없다. 그 화면은 비어 보인다.
- 기본은 127.0.0.1에서만 열린다. 다른 PC에서 보려면 `SERVER_ADDRESS=0.0.0.0`과 함께 인증부터 붙인다 (보유 수량이 보인다).

## 스택

Java 21 · Spring Boot 4 · Spring Data JPA · PostgreSQL (테스트는 H2) · React 19 · TypeScript · Vite · Tailwind CSS · shadcn/ui · Lightweight Charts

## 다음 단계

- LLM으로 한국어 요약·번역, 종목 관련성 판단, 호재/악재 분류
- 여러 종목에 걸린 같은 기사 합치기
- KIS 보유 종목 연동, MariaDB, 서버 배포
