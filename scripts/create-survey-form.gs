/**
 * M-SPACE 학생 설문 구글폼 자동 생성 스크립트 (docs/설문-문항.md와 같은 내용)
 *
 * 쓰는 법
 *  1. https://script.google.com 에서 "새 프로젝트"를 만들어요.
 *  2. 기본 코드를 지우고 이 파일 내용을 전부 붙여 넣어요.
 *  3. 아래 DEMO_URL에 Vercel 데모 주소를 넣어요. (아직 없으면 비워 두고 나중에 폼 설명에서 고쳐도 돼요)
 *  4. 위쪽 함수 선택이 createSurveyForm인지 보고 ▶ 실행 → 권한 허용(내 드라이브에 폼·시트 만들기)
 *  5. 아래쪽 "실행 로그"에 나온 응답 주소를 학생들에게 보내요. 폼은 내 구글 드라이브에 생겨요.
 *
 * 개인정보: 학번·이름·전화번호 문항이 없고, 이메일 수집도 꺼요 (CLAUDE.md 6장).
 * 실행할 때마다 새 폼이 하나씩 생겨요. 다시 만들 필요가 없으면 한 번만 실행해요.
 */

const DEMO_URL = 'https://m-space-rho.vercel.app'; // Vercel 설문용 데모

function createSurveyForm() {
  const form = FormApp.create('M-SPACE실 온라인 예약 시스템 의견 조사');
  form
    .setDescription([
      'M-SPACE실 예약과 키 대여를 웹으로 옮기려고 해요. 아래 데모를 휴대폰으로 잠깐 써 보고 답해 주세요. 설문은 1분이면 끝나요.',
      '',
      '데모 주소: ' + (DEMO_URL || '(데모 주소)'),
      '데모는 가짜 데이터로 동작하고, 입력한 내용은 내 브라우저에만 저장돼요. 회원가입은 하지 않아도 되고, 해 본다면 실제 학번·전화번호 대신 아무 숫자나 넣어 주세요.',
      '이 설문은 학번·이름 등 개인정보를 받지 않아요.',
      '',
      '[데모에서 해 볼 것 3가지]',
      '1. 첫 화면에서 오늘·내일 방이 비어 있는지 보기',
      '2. 방 카드의 빈 시간을 누르고 "예약하기" → (로그인 화면이 나오면 "데모 학생으로 바로 시작하기") → "내 예약"에서 취소해 보기',
      '3. 날짜를 모레 이후로 바꿔 "시험기간"이라고 나오는 날에 예약해 보기 (시험기간은 키 없이 앱에서 "이용 시작"을 눌러요)',
    ].join('\n'))
    .setCollectEmail(false)          // 이메일 수집 안 함 (익명 설문)
    .setAllowResponseEdits(false)
    .setShowLinkToRespondAgain(false)
    .setProgressBar(true)
    .setConfirmationMessage('참여해 주셔서 고마워요! 의견은 운영 규칙과 화면을 정하는 데 반영할게요.');

  // ───────── 섹션 1. 지금 이용 방식 ─────────
  form.addSectionHeaderItem().setTitle('1. 지금 이용 방식');

  choice(form, 'Q1. 학년', ['1학년', '2학년', '3학년', '4학년', '기타(대학원·휴학 등)'], true);
  choice(form, 'Q2. 지난 학기에 M-SPACE실을 몇 번 정도 이용했나요?',
    ['안 써 봤어요', '1~2번', '3~5번', '6~10번', '10번 넘게'], true);
  checkbox(form, 'Q3. 지금 방식(학과실 방문, 서류 작성, 학생증 제출)에서 불편했던 점을 모두 골라 주세요.', [
    '비어 있는지 가 봐야 알 수 있었어요',
    '예약하려고 학과실에 직접 가야 했어요',
    '매번 학번·이름·전화번호를 서류에 썼어요',
    '학과실이 닫혀 있어서 예약·반납을 못 한 적이 있어요',
    '학생증을 맡겨 두는 게 불안했어요',
    '불편한 점이 없었어요',
  ], true);

  // ───────── 섹션 2. 데모 써 보기 ─────────
  form.addPageBreakItem()
    .setTitle('2. 데모 써 보기')
    .setHelpText('데모 주소: ' + (DEMO_URL || '(폼 맨 위 설명 참고)') + '\n아직 안 써 봤다면 지금 휴대폰으로 열어서 과제 3가지를 해 보고 답해 주세요.');

  scale(form, 'Q4. 첫 화면(현황판)에서 원하는 날짜·시간에 방이 비어 있는지 쉽게 알 수 있었나요?',
    '전혀 알기 어려웠어요', '바로 알 수 있었어요', true);
  scale(form, 'Q5. 예약 신청(방·시간·인원 고르기)은 쉬웠나요?', '매우 어려웠어요', '매우 쉬웠어요', true);
  scale(form, 'Q6. 예약 확인·취소("내 예약")는 쉬웠나요?', '매우 어려웠어요', '매우 쉬웠어요', true);
  form.addParagraphTextItem()
    .setTitle('Q7. 데모를 쓰면서 헷갈리거나 막힌 곳이 있었다면 적어 주세요.')
    .setRequired(false);
  scale(form, 'Q8. 이 시스템이 생기면 지금 방식 대신 쓰고 싶나요?', '지금 방식이 나아요', '꼭 쓰고 싶어요', true);

  // ───────── 섹션 3. 운영 규칙 의견 ─────────
  form.addPageBreakItem()
    .setTitle('3. 운영 규칙 의견')
    .setHelpText('아직 확정하지 않은 규칙이에요. 학과실과 정할 때 여러분 의견을 함께 반영해요.');

  choice(form, 'Q9. M-SPACE는 공식 규정상 한 번에 최대 2시간까지 예약할 수 있어요. 2시간이면 어떤가요?',
    ['충분해요', '조금 부족해요', '많이 부족해요', '잘 모르겠어요'], true);
  choice(form, 'Q10. 한 사람이 하루에 예약할 수 있는 횟수 (현재안: 1건)', ['1건', '2건', '3건 이상'], true);
  choice(form, 'Q11. 며칠 앞까지 예약할 수 있으면 좋을까요? (현재안: 7일)', ['3일', '7일', '14일'], true);
  choice(form,
    'Q12. 노쇼(예약해 놓고 시작 15분이 지나도 키를 안 받음)가 2주에 2번이면 7일 동안 예약을 못 하게 하려고 해요. 어떻게 생각하나요?',
    ['적당해요', '너무 엄해요', '더 엄해야 해요', '잘 모르겠어요'], true);
  choice(form, 'Q13. 키를 받을 때 학생증은 어떻게 하는 게 좋을까요?',
    ['확인만 하고 바로 돌려받기', '반납할 때까지 맡기기', '상관없어요'], true);
  scale(form, 'Q14. 시험기간처럼 방을 열어둘 때, 키 없이 앱에서 "이용 시작"을 누르는 방식은 어떤가요?',
    '불편해요', '좋아요', false);

  // ───────── 섹션 4. 추가 의견 ─────────
  form.addPageBreakItem().setTitle('4. 추가 의견');

  checkbox(form, 'Q15. 있으면 좋겠는 기능을 모두 골라 주세요.', [
    '반납 30분 전 알림 (문자·카카오톡 등)',
    '예약 시작 전 알림',
    '같이 쓰는 사람(팀원)도 예약 내용 보기',
    '예약 시간 연장',
    '방 안 QR로 이용 시작하기',
  ], false);
  form.addParagraphTextItem().setTitle('Q16. 그 밖에 하고 싶은 말').setRequired(false);

  // 응답이 자동으로 쌓이는 구글 시트 (결과 분석용)
  const sheet = SpreadsheetApp.create('M-SPACE 설문 응답');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());

  const respondUrl = form.getPublishedUrl();
  Logger.log('✅ 폼을 만들었어요.');
  Logger.log('학생에게 보낼 응답 주소: ' + form.shortenFormUrl(respondUrl));
  Logger.log('응답 주소(전체): ' + respondUrl);
  Logger.log('폼 편집 주소(나만): ' + form.getEditUrl());
  Logger.log('응답 시트(나만): ' + sheet.getUrl());
}

/** 객관식 (하나만 고르기) */
function choice(form, title, options, required) {
  form.addMultipleChoiceItem().setTitle(title).setChoiceValues(options).setRequired(required);
}

/** 체크박스 (여러 개 고르기) + 기타 직접 입력 */
function checkbox(form, title, options, required) {
  form.addCheckboxItem().setTitle(title).setChoiceValues(options).showOtherOption(true).setRequired(required);
}

/** 1~5점 선형 배율 */
function scale(form, title, lowLabel, highLabel, required) {
  form.addScaleItem().setTitle(title).setBounds(1, 5).setLabels(lowLabel, highLabel).setRequired(required);
}
