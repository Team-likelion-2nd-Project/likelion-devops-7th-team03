// Load/Stress 겸용 스크립트 — PEAK_TPS 하나만 바꿔서 두 시나리오에 재사용한다
// (예: Load는 PEAK_TPS=23, Stress는 PEAK_TPS=230). 0 → PEAK_TPS로 5분 상승 →
// 10분 유지 → 5분 하강, 총 20분. 링크는 풀 하나(1..SLUG_COUNT)만 생성하고 그 안에서
// 앞쪽 20%(HOT_RATIO)를 인기 링크 구간으로 취급, 요청의 80%(HOT_TRAFFIC_RATIO)를
// 그 구간에 몰아준다 — 별도 풀을 안 만들어도 되니 INSERT/계산이 단순하다.
//
// User-Agent/Referer/visitor_id를 실제처럼 보내서 UA 파싱(yauaa)/referrer 분류/visitor
// 식별이 정확한지 나중에 Athena로 검증한다. VU마다 조합을 고정 배정해 같은 방문자는
// 항상 같은 헤더를 보낸다(세션처럼) — Athena에서 visitor_id로 묶어 대조 가능.
// visitor_id는 VisitorIdResolver가 UUID 형식만 신뢰하므로 진짜 UUID로 보내야 한다.
//
// 다른 트래픽과 안 섞이게 격리하려면 visitor_id/referrer가 아니라 **link_id로 필터링**
// (이 테스트 링크는 실제 트래픽이 칠 수 없어 완전 격리됨).
//
// 사용법:
//   BASE_URL=http://redirect-service:8080 SLUG_PREFIX=lt123456 SLUG_COUNT=300000 \
//     PEAK_TPS=230 k6 run load-test/normal-traffic.js
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://redirect-service:8080";
const SLUG_PREFIX = __ENV.SLUG_PREFIX || "test";
const SLUG_COUNT = Number(__ENV.SLUG_COUNT || 300000);
const PEAK_TPS = Number(__ENV.PEAK_TPS || 230);

// 링크는 하나의 풀(1..SLUG_COUNT)로 생성하고, 그 안에서 앞쪽 HOT_RATIO만큼을
// 인기 링크 구간으로 취급한다 (별도 풀 생성 없이 구간만 나눔). 80/20 원칙:
// 요청의 HOT_TRAFFIC_RATIO(80%)는 핫 구간에서, 나머지는 콜드 구간에서 균등 랜덤.
const HOT_RATIO = Number(__ENV.HOT_RATIO || 0.2);
const HOT_TRAFFIC_RATIO = Number(__ENV.HOT_TRAFFIC_RATIO || 0.8);
const HOT_COUNT = Math.max(1, Math.floor(SLUG_COUNT * HOT_RATIO));

// device_type(yauaa DeviceClass)별로 하나씩 — DESKTOP/MOBILE/TABLET 커버.
const UA_PROFILES = [
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36", // DESKTOP/Windows/Chrome
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15", // DESKTOP/macOS/Safari
  "Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", // MOBILE/iOS/Safari
  "Mozilla/5.0 (Linux; Android 14; SM-S911B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36", // MOBILE/Android/Chrome
  "Mozilla/5.0 (iPad; CPU OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1", // TABLET/iPadOS/Safari
];

// ReferrerCategoryResolver 매칭 규칙(INSTAGRAM/FACEBOOK/NAVER/GOOGLE/KAKAO/X/DIRECT/ETC)
// 카테고리 하나씩 커버. referrer: null이면 Referer 헤더 자체를 안 보냄(→DIRECT).
const REFERRER_PROFILES = [
  "https://www.instagram.com/",
  "https://www.facebook.com/",
  "https://www.naver.com/",
  "https://www.google.com/search?q=snipy",
  "https://talk.kakao.com/",
  "https://t.co/abc123",
  null, // DIRECT
  "https://random-blog-example.net/post/1", // ETC
];

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

// VU(__VU)마다 한 번만 배정하고 그 VU의 남은 이터레이션 동안 재사용 — 실제 세션처럼
// 같은 방문자는 계속 같은 UA/referrer/visitor_id를 유지한다.
const visitorProfiles = {};
function getVisitorProfile() {
  const vu = __VU;
  if (!visitorProfiles[vu]) {
    const profile = {
      visitorId: uuidv4(),
      userAgent: UA_PROFILES[vu % UA_PROFILES.length],
      referrer: REFERRER_PROFILES[vu % REFERRER_PROFILES.length],
    };
    visitorProfiles[vu] = profile;
    console.log(
      `visitor VU=${vu} visitor_id=${profile.visitorId} ua_profile=${vu % UA_PROFILES.length} referrer_profile=${vu % REFERRER_PROFILES.length}`
    );
  }
  return visitorProfiles[vu];
}

export const options = {
  scenarios: {
    ramped_load: {
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: 30,
      maxVUs: 250,
      stages: [
        { target: PEAK_TPS, duration: "2m" }, // 상승
        { target: PEAK_TPS, duration: "6m" }, // 유지
        { target: 0, duration: "2m" }, // 하강
      ], // 총 20분
    },
  },
  thresholds: {
    http_req_failed: ["rate==0"], // 에러율 0% (성능 요구사항)
    http_req_duration: ["p(99)<150"], // p99 < 150ms (성능 요구사항)
  },
};

export default function () {
  let n, pool;
  if (Math.random() < HOT_TRAFFIC_RATIO) {
    n = 1 + Math.floor(Math.random() * HOT_COUNT); // 핫 구간: 1..HOT_COUNT
    pool = "hot";
  } else {
    n = HOT_COUNT + 1 + Math.floor(Math.random() * (SLUG_COUNT - HOT_COUNT)); // 콜드 구간
    pool = "cold";
  }
  const slug = `${SLUG_PREFIX}${n}`;

  const visitor = getVisitorProfile();
  const headers = {
    "User-Agent": visitor.userAgent,
    Cookie: `visitor_id=${visitor.visitorId}`,
  };
  if (visitor.referrer) {
    headers["Referer"] = visitor.referrer;
  }

  // tags.name 고정(+ hot/cold 구분만 추가, 값 2개뿐이라 카디널리티 안전) — 안 주면
  // k6가 URL(슬러그별로 다름)을 라벨로 써서 시계열이 슬러그 개수만큼 폭발한다
  // (실제로 87만 개까지 간 적 있음).
  const res = http.get(`${BASE_URL}/${slug}`, {
    redirects: 0,
    headers,
    tags: { name: `redirect_${pool}` },
  });

  check(res, {
    "status is 302 or 404": (r) => r.status === 302 || r.status === 404,
  });
}
