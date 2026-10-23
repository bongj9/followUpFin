package com.bongj9.briefing.domain;

import java.util.List;

// DART 공시 제목을 쉬운 이름·중요도·"여기를 보세요" 안내로 바꾼다
// 위에서부터 처음 맞는 규칙을 쓴다 (제목 앞의 [기재정정] 같은 표시는 무시)
// ponytail: 제목 글자로만 고른다. 규칙에 없는 공시는 원래 제목 그대로 중간 중요도로 둔다
public record DisclosureGuide(String name, Importance importance, String tip) {

    public enum Importance { HIGH, MID, LOW }

    private record Rule(String keyword, String name, Importance importance, String tip) {
    }

    private static final List<Rule> RULES = List.of(
            // 거래·상장 경고
            new Rule("매매거래정지", "거래 정지", Importance.HIGH, "주식 거래가 멈췄습니다. 이유와 재개 날짜를 꼭 확인하세요."),
            new Rule("상장폐지", "상장폐지 관련", Importance.HIGH, "상장이 폐지될 수 있다는 뜻이라 매우 큰 악재입니다. 사유와 일정을 확인하세요."),
            new Rule("관리종목", "관리종목 지정 관련", Importance.HIGH, "재무나 공시에 문제가 있어 거래소가 주의를 준 것입니다. 보통 악재입니다."),
            new Rule("조회공시", "거래소 조회공시", Importance.HIGH, "주가가 크게 움직였을 때 거래소가 이유를 묻고 회사가 답한 공시입니다."),
            new Rule("풍문또는보도에대한해명", "보도 해명", Importance.HIGH, "언론 보도에 대한 회사의 입장입니다. '미확정'이면 아직 정해진 게 없다는 뜻입니다."),
            // 실적
            new Rule("(잠정)실적", "잠정 실적 발표", Importance.HIGH, "매출·영업이익이 작년 같은 기간보다 늘었는지, 시장 예상보다 좋은지 보세요. 예상보다 좋으면 보통 호재입니다."),
            new Rule("결산실적공시예고", "실적 발표 예정 안내", Importance.MID, "실적을 언제 발표할지 알리는 안내입니다. 숫자는 그날 나옵니다."),
            // 주식 수가 바뀌는 일
            new Rule("유상증자결정", "유상증자", Importance.HIGH, "새 주식을 찍어 돈을 모읍니다. 주식 수가 늘어 기존 주주 몫이 줄어서 보통 악재로 봅니다. 증자 규모와 할인율을 보세요."),
            new Rule("무상증자결정", "무상증자", Importance.HIGH, "주주에게 주식을 공짜로 나눠 줍니다. 회사 가치는 그대로지만 단기 호재로 받아들여지는 경우가 많습니다."),
            new Rule("전환사채권발행결정", "전환사채(CB) 발행", Importance.HIGH, "나중에 주식으로 바뀔 수 있는 빚입니다. 주식 수가 늘 수 있어 보통 부담 요인입니다. 전환가격을 보세요."),
            new Rule("신주인수권부사채권발행결정", "신주인수권부사채(BW) 발행", Importance.HIGH, "새 주식을 살 권리가 붙은 빚입니다. 주식 수가 늘 수 있어 보통 부담 요인입니다."),
            new Rule("자기주식취득결과", "자사주 매입 완료", Importance.MID, "앞서 발표한 자사주 매입을 끝냈다는 보고입니다. 계획한 만큼 다 샀는지 보세요."),
            new Rule("자기주식취득", "자사주 매입", Importance.HIGH, "회사가 자기 주식을 삽니다. 보통 호재입니다. 금액이 시가총액의 몇 %인지 보세요."),
            new Rule("자기주식처분", "자사주 매각", Importance.HIGH, "회사가 갖고 있던 주식을 시장에 팝니다. 보통 부담 요인입니다."),
            new Rule("주식소각", "주식 소각", Importance.HIGH, "주식을 없애 주식 수를 줄입니다. 한 주의 가치가 올라 보통 호재입니다."),
            new Rule("감자결정", "감자(자본 줄이기)", Importance.HIGH, "주식 수나 자본을 줄입니다. 손실을 메우려는 감자라면 보통 큰 악재입니다."),
            // 사업
            new Rule("단일판매ㆍ공급계약", "대형 공급계약", Importance.HIGH, "계약 금액이 최근 매출의 몇 %인지 보세요. 클수록 영향이 큽니다."),
            new Rule("합병결정", "합병", Importance.HIGH, "다른 회사와 합칩니다. 합병 비율과 목적을 보세요."),
            new Rule("분할결정", "회사 분할", Importance.HIGH, "회사를 나눕니다. 어떤 사업이 떨어져 나가는지, 주주가 새 회사 주식을 받는지 보세요."),
            new Rule("양수결정", "자산·회사 인수", Importance.MID, "다른 회사나 자산을 삽니다. 금액이 자산총액의 몇 %인지 보세요."),
            new Rule("양도결정", "자산·회사 매각", Importance.MID, "자산이나 사업을 팝니다. 매각 금액과 이유를 보세요."),
            new Rule("신규시설투자", "시설 투자", Importance.MID, "공장·설비에 투자합니다. 금액이 자기자본의 몇 %인지 보세요."),
            new Rule("소송", "소송", Importance.MID, "소송 금액이 자기자본에 비해 얼마나 큰지 보세요."),
            // 주주환원
            new Rule("배당결정", "배당 결정", Importance.HIGH, "배당금이 작년보다 늘었는지, 주가 대비 몇 %(시가배당률)인지 보세요."),
            new Rule("기업가치제고계획", "밸류업(주주가치 개선) 계획", Importance.MID, "배당·자사주 같은 주주환원 계획입니다. 구체적인 숫자와 기한이 있는지 보세요."),
            // 지분
            new Rule("최대주주변경", "최대주주 변경", Importance.HIGH, "회사의 주인이 바뀝니다. 새 최대주주가 누구인지 보세요."),
            new Rule("주식등의대량보유상황보고서", "5% 이상 대주주 지분 변동", Importance.MID, "누가 사고팔았는지, 보유 목적이 '경영참가'인지 보세요."),
            new Rule("최대주주등소유주식변동", "최대주주 지분 변동", Importance.MID, "최대주주 쪽이 주식을 사고팔았습니다. 많이 팔았다면 눈여겨보세요."),
            new Rule("임원ㆍ주요주주특정증권등거래계획", "임원·대주주 매매 계획", Importance.MID, "임원이나 대주주가 앞으로 주식을 사고팔 계획을 미리 알린 것입니다."),
            new Rule("임원ㆍ주요주주특정증권등소유상황", "임원·대주주 주식 보유 보고", Importance.LOW, null),
            // 정기·안내
            new Rule("사업보고서", "사업보고서 (1년 결산)", Importance.MID, "1년 실적과 사업 내용을 정리한 보고서입니다. 핵심 숫자는 보통 잠정 실적 공시로 먼저 나옵니다."),
            new Rule("반기보고서", "반기보고서", Importance.MID, "6개월 실적 정리입니다. 핵심 숫자는 보통 잠정 실적 공시로 먼저 나옵니다."),
            new Rule("분기보고서", "분기보고서", Importance.MID, "분기 실적 정리입니다. 핵심 숫자는 보통 잠정 실적 공시로 먼저 나옵니다."),
            new Rule("기업설명회(IR)", "IR(실적 설명회) 안내", Importance.MID, "설명회 날짜 안내입니다. 내용은 그날 발표됩니다."),
            new Rule("주주총회", "주주총회", Importance.LOW, null),
            new Rule("주주명부폐쇄", "배당·주총 기준일 안내", Importance.LOW, null),
            // 회사 주가와 거의 상관없는 서류 (펀드·채권 발행, 계열사 거래 등)
            new Rule("증권발행실적보고서", "증권 발행 결과", Importance.LOW, null),
            new Rule("투자설명서", "투자설명서", Importance.LOW, null),
            new Rule("일괄신고", "증권 발행 서류", Importance.LOW, null),
            new Rule("증권신고서", "증권 발행 서류", Importance.LOW, null),
            new Rule("효력발생안내", "증권 발행 서류", Importance.LOW, null),
            new Rule("자산유동화", "자산유동화 서류", Importance.LOW, null),
            new Rule("자산양도등의등록", "자산유동화 서류", Importance.LOW, null),
            new Rule("소액공모", "소액공모 서류", Importance.LOW, null),
            new Rule("증권발행결과", "증권 발행 결과", Importance.LOW, null),
            new Rule("특수관계인", "계열사·특수관계인 거래", Importance.LOW, null),
            new Rule("출자계열회사", "계열사 거래", Importance.LOW, null));

    public static DisclosureGuide of(String reportNm) {
        String title = reportNm.replaceFirst("^\\[[^]]*\\]", "");
        for (Rule rule : RULES) {
            if (title.contains(rule.keyword())) {
                return new DisclosureGuide(rule.name(), rule.importance(), rule.tip());
            }
        }
        return new DisclosureGuide(title, Importance.MID, null);
    }
}
