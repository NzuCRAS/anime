import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: Number(__ENV.VUS) || 50,
    duration: __ENV.DURATION || '20s',
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const REFRESH_PATH = '/api/auth/refresh';

// 读取环境变量 REFRESH_TOKEN（优先），否则你可以把值写到脚本里（不推荐）
const REFRESH_TOKEN_VALUE = __ENV.REFRESH_TOKEN || '';

if (!REFRESH_TOKEN_VALUE) {
    console.error('No REFRESH_TOKEN provided. Run with -e REFRESH_TOKEN=...');
}

export default function () {
    const headers = {
        'Content-Type': 'application/json',
        'Cookie': `refreshToken=${REFRESH_TOKEN_VALUE}`,
    };

    const res = http.post(`${BASE}${REFRESH_PATH}`, null, { headers: headers });

    check(res, {
        'status is 200': (r) => r.status === 200,
        'status is 401': (r) => r.status === 401,
    });

    sleep(0.05);
}