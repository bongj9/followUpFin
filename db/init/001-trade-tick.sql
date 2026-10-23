-- 토스 웹소켓으로 받은 체결 전부. 나머지 테이블(종목·뉴스·공시·급등락)은 JPA가 만든다
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- 같은 시각·가격·수량 체결이 실제로 여러 번 올 수 있어 기본키를 두지 않는다
CREATE TABLE trade_tick (
    time     TIMESTAMPTZ NOT NULL,
    symbol   TEXT        NOT NULL,
    price    NUMERIC     NOT NULL,
    volume   NUMERIC     NOT NULL,
    currency TEXT        NOT NULL
);

-- 하루 단위로 쪼개 저장 (hypertable). 기간 조회·삭제가 빠르다
SELECT create_hypertable('trade_tick', by_range('time', INTERVAL '1 day'));
CREATE INDEX ON trade_tick (symbol, time DESC);

-- 7일 지난 체결은 종목별로 묶어 압축 (보통 10분의 1 이하로 줄어든다). 삭제는 하지 않는다
ALTER TABLE trade_tick SET (timescaledb.compress, timescaledb.compress_segmentby = 'symbol', timescaledb.compress_orderby = 'time DESC');
SELECT add_compression_policy('trade_tick', INTERVAL '7 days');
