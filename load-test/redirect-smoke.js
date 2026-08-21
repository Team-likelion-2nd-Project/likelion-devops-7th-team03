// 아주 간단한 redirect-service 부하테스트.
//
// 사용법:
//   BASE_URL=https://dev.snipy.life SLUG=abcd1234 k6 run load-test/redirect-smoke.js
//
// SLUG은 실제로 links 테이블에 등록된 slug로 넘겨야 302를 받는다.
// (없으면 404 응답을 대상으로 서버가 부하 상황에서 얼마나 버티는지만 확인하게 됨)

import http from "k6/http";
import { check, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "https://dev.snipy.life";
const SLUG = __ENV.SLUG || "test";

export const options = {
  vus: Number(__ENV.VUS || 10),
  duration: __ENV.DURATION || "30s",
};

export default function () {
  const res = http.get(`${BASE_URL}/${SLUG}`, { redirects: 0 });

  check(res, {
    "status is 302 or 404": (r) => r.status === 302 || r.status === 404,
  });

  sleep(1);
}
