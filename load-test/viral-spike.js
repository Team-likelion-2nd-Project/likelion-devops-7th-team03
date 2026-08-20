// "Warmup 없는 바이럴 링크" 시나리오.
//
// 가설: 캐시에 없는 링크가 갑자기 바이럴되면, 동시 캐시 미스가 몰려 MySQL/HikariCP
// 커넥션 풀에 짧고 날카로운 병목이 생긴다. redirect-service의 캐시 미스 write-back
// 경로(RedirectService.findDatabaseRedirectTarget)에는 동시성 보호가 전혀 없어서
// (락/synchronized/분산락/요청 병합 없음) 이 herd가 그대로 DB까지 전달된다.
//
// 사용법:
//   BASE_URL=https://dev.snipy.life SLUGS=cold-test-1 k6 run load-test/viral-spike.js
//
// SLUGS는 콤마로 여러 개 넘길 수 있다 (herd 크기를 키우고 싶을 때). 반드시 테스트
// 시작 전 Redis 캐시에 없는 상태여야 한다 — 이미 캐싱된 slug를 쓰면 herd가 재현되지 않는다.
// 링크 자체는 links 테이블에 미리 등록돼 있어야 302를 받는다 (없으면 404 대상 테스트가 됨).
import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "https://dev.snipy.life";
const SLUGS = (__ENV.SLUGS || __ENV.SLUG || "test").split(",").map((s) => s.trim());

export const options = {
  scenarios: {
    viral_spike: {
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: 200,
      maxVUs: 500,
      stages: [
        { target: 800, duration: "10s" }, // 바이럴 — 급격한 유입
        { target: 800, duration: "4m30s" }, // 유지
        { target: 0, duration: "20s" }, // 정리
      ],
    },
  },
  thresholds: {
    // 참고용 기본값 — 실제 판정 기준은 팀 논의 후 확정 필요 (load-test/SCENARIOS.md 참고)
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1000"],
  },
};

export default function () {
  const slug = SLUGS[Math.floor(Math.random() * SLUGS.length)];
  const res = http.get(`${BASE_URL}/${slug}`, { redirects: 0 });

  check(res, {
    "status is 302 or 404": (r) => r.status === 302 || r.status === 404,
  });
}
