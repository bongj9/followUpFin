package com.bongj9.briefing.adapter.dart;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DartClientTest {

    @Test
    void 회사코드_파일에서_상장사만_종목코드로_찾게_한다() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                    <list><corp_code>00434003</corp_code><corp_name>다코</corp_name><stock_code> </stock_code></list>
                    <list><corp_code>00126380</corp_code><corp_name>삼성전자</corp_name><stock_code>005930</stock_code></list>
                </result>""";

        assertThat(DartClient.parseCorpCodes(zip(xml))).containsExactly(java.util.Map.entry("005930", "00126380"));
    }

    @Test
    void 키가_틀려_zip이_아닌_응답이_오면_이유를_알려준다() {
        byte[] error = "{\"status\":\"010\"}".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> DartClient.parseCorpCodes(error)).hasMessageContaining("인증키");
    }

    private static byte[] zip(String content) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("CORPCODE.xml"));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
