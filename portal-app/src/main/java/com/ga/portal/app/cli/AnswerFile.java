package com.ga.portal.app.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 답변 파일({@code inquiry answer --answer-file=<path>} — 포털 Phase 6 ⑦): 답변은 <b>파일로만</b> 받는다(명령행 인자·환경 변수는 셸 기록·프로세스 목록에 남는다).
 * 요건: 16 KiB 이하(상한 + 1바이트까지만 읽는다), 엄격한 UTF-8(잘못된 바이트는 거부 — 대체 문자로 바꾸지 않는다), 끝의 줄바꿈 하나를 뺀 뒤 1~4,000 코드
 * 포인트이고 공백만은 안 되며 NUL이 없다. 어긋나면 {@link Invalid} — 메시지는 규칙과 경로만, 내용은 싣지 않는다.
 */
final class AnswerFile {

    static final int MAX_BYTES = 16 * 1024;
    static final int MAX_CODE_POINTS = 4_000;

    /** 읽을 수 없거나 요건 밖 — 사용법 오류(64). */
    static final class Invalid extends Exception {
        Invalid(String message) {
            super(message, null, false, false);
        }
    }

    private AnswerFile() {
    }

    static String read(Path path) throws Invalid {
        byte[] bytes;
        try (InputStream in = Files.newInputStream(path)) {
            bytes = in.readNBytes(MAX_BYTES + 1);
        } catch (IOException | SecurityException e) {
            throw new Invalid("--answer-file " + path + " cannot be read (" + e.getClass().getSimpleName() + ")");
        }
        if (bytes.length > MAX_BYTES) {
            throw new Invalid("--answer-file is over " + MAX_BYTES + " bytes");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new Invalid("--answer-file is not valid UTF-8");
        }
        if (text.endsWith("\r\n")) {
            text = text.substring(0, text.length() - 2);
        } else if (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        int codePoints = text.codePointCount(0, text.length());
        if (codePoints < 1 || codePoints > MAX_CODE_POINTS || text.isBlank()) {
            throw new Invalid("--answer-file must hold 1 to " + MAX_CODE_POINTS + " code points, not only whitespace");
        }
        if (text.indexOf('\u0000') >= 0) {
            throw new Invalid("--answer-file must not contain NUL");
        }
        return text;
    }
}
