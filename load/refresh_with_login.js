import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    vus: 50,          // 并发虚拟用户数，可根据机器调整
    duration: '20s',  // 运行时长
    thresholds: {
        http_req_duration: ['p(95)<1000'],
    },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const LOGIN_PATH = '/api/user/login';
const REFRESH_PATH = '/api/auth/refresh';

// setup 登录并返回 refresh cookie 值
export function setup() {
    const credentials = { usernameOrEmail: __ENV.TEST_USER || 'testuser', password: __ENV.TEST_PWD || 'testpassword' };
    const res = http.post(`${BASE}${LOGIN_PATH}`, JSON.stringify(credentials), {
        headers: { 'Content-Type': 'application/json' },
        redirects: 0, // 不跟随重定向（如果有）
    });

    if (res.status !== 200) {
        console.error('Login failed in setup:', res.status, res.body);
        return { refreshCookie: null };
    }

    // k6 将 cookies 解析到 res.cookies
    let refreshCookie = null;
    if (res.cookies && res.cookies['refreshToken'] && res.cookies['refreshToken'].length > 0) {
        refreshCookie = res.cookies['refreshToken'][0].value;
    }
    return { refreshCookie };
}

export default function (data) {
    const refreshToken = data.refreshCookie;
    const headers = {
        'Content-Type': 'application/json',
        'Cookie': `refreshToken=${refreshToken}`,
    };

    const res = http.post(`${BASE}${REFRESH_PATH}`, null, { headers: headers });

    // 期望 200 或 401（并发同一 token 场景多为401）
    check(res, {
        'status 200 or 401': (r) => r.status === 200 || r.status === 401,
    });

    // 如果需要，读取 New-Access-Token header（注意 header 小写）
    if (res.headers['New-Access-Token'] || res.headers['new-access-token']) {
        // debug: console.log('new access in header');
    }

    sleep(0.05);
}