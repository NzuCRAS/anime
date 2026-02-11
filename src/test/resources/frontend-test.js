/**
 * frontend-test.js
 *
 * Node.js 测试脚本：登录 -> 建立 WS -> 拉取聊天记录 -> 通过 WS 发送消息
 *
 * 注意：脚本在构造 wss:// WebSocket 时会使用 `rejectUnauthorized: false`
 * 来跳过证书验证（仅用于本地开发 / 测试）。生产请不要这样做。
 */

const axios = require('axios');
const https = require('https');
const WebSocket = require('ws');

const SERVER_URL =  'https://localhost:8443';
const WS_URL =  'wss://localhost:8443/ws/chat';
const USERNAME =  '1';
const PASSWORD =  '1';
const FRIEND_ID = '2';

const axiosInstance = axios.create({
    httpsAgent: new https.Agent({ rejectUnauthorized: false })
});

async function login() {
    try {
        const res = await axiosInstance.post(`${SERVER_URL}/api/user/login`, {
            usernameOrEmail: USERNAME,
            password: PASSWORD
        }, {
            validateStatus: s => s < 500
        });

        if (res.status >= 400) {
            console.error('login failed', res.status, res.data);
            return null;
        }

        const newAccess = res.headers['new-access-token'] || res.headers['new-access-token'.toLowerCase()];
        if (newAccess) {
            console.log('login OK, got New-Access-Token header');
            return { accessToken: newAccess, userInfo: res.data?.data };
        }

        console.warn('login succeeded but New-Access-Token header not found. Response headers:', res.headers);
        return { accessToken: null, userInfo: res.data?.data };
    } catch (err) {
        console.error('login error', err && err.message ? err.message : err);
        return null;
    }
}

/**
 * Connect WebSocket.
 * - Provide both query token and Authorization header for compatibility.
 * - Pass rejectUnauthorized: false to skip TLS cert verification for testing.
 */
function connectWs(accessToken) {
    return new Promise((resolve, reject) => {
        const urlWithToken = accessToken ? `${WS_URL}?token=${encodeURIComponent(accessToken)}` : WS_URL;
        console.log('connect ws to', urlWithToken);

        // Options for ws: include Authorization header and disable cert validation (dev only)
        const wsOptions = {
            headers: {}
        };
        if (accessToken) {
            wsOptions.headers['Authorization'] = `Bearer ${accessToken}`;
        }
        // IMPORTANT (dev only): skip certificate verification when testing with self-signed certs
        wsOptions.rejectUnauthorized = false;

        const ws = new WebSocket(urlWithToken, wsOptions);

        const to = setTimeout(() => {
            try { ws.terminate(); } catch (_) {}
            reject(new Error('WS connection timeout'));
        }, 8000);

        ws.on('open', () => {
            clearTimeout(to);
            console.log('WS open');
            resolve(ws);
        });

        ws.on('message', (raw) => {
            try {
                const txt = raw.toString();
                const env = JSON.parse(txt);
                console.log('WS received:', env.type, JSON.stringify(env.payload, null, 2));
            } catch (e) {
                console.log('WS message (non-json):', raw.toString());
            }
        });

        ws.on('close', (code, reason) => {
            console.log('WS closed', code, reason && reason.toString ? reason.toString() : reason);
        });

        ws.on('error', (err) => {
            console.error('WS error', err && err.message ? err.message : err);
        });
    });
}

async function fetchPrivateMessages(accessToken, friendId, page = 1) {
    try {
        const headers = {};
        if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;
        const res = await axiosInstance.post(`${SERVER_URL}/api/chat/messages/private/getMessage`, {
            friendId: friendId,
            page: page
        }, {
            headers
        });
        if (res.status >= 400) {
            console.error('fetchPrivateMessages failed', res.status, res.data);
            return null;
        }
        const messages = res.data?.data?.messages || [];
        console.log(`Fetched ${messages.length} messages for friend ${friendId} (page ${page})`);
        messages.forEach(m => {
            console.log('---');
            console.log('id:', m.id);
            console.log('from:', m.fromUserId, 'to:', m.toUserId, 'type:', m.messageType, 'time:', m.createdAt);
            if (m.messageType === 'TEXT') {
                console.log('text:', m.content);
            } else {
                console.log('fileUrl:', m.fileUrl || m.imageUrl || '(no fileUrl)');
            }
        });
        return messages;
    } catch (err) {
        console.error('fetchPrivateMessages error', err && err.message ? err.message : err);
        return null;
    }
}

function sendPrivateTextWS(ws, targetUserId, text) {
    const clientMessageId = `cli-${Date.now()}-${Math.floor(Math.random() * 10000)}`;
    const envelope = {
        type: 'SEND_MESSAGE',
        payload: {
            conversationType: 'PRIVATE',
            messageType: 'TEXT',
            targetUserId: Number(targetUserId),
            content: text,
            clientMessageId
        }
    };
    ws.send(JSON.stringify(envelope));
    console.log('WS SENT SEND_MESSAGE (text) clientMessageId=', clientMessageId);
    return clientMessageId;
}

(async () => {
    console.log('=== frontend test script start ===');

    const loginRes = await login();
    if (!loginRes) {
        console.error('login failed, abort');
        process.exit(1);
    }
    const accessToken = loginRes.accessToken;
    if (!accessToken) {
        console.warn('no access token available from login; requests may fail if server requires Authorization header');
    }

    let ws;
    try {
        ws = await connectWs(accessToken);
    } catch (e) {
        console.error('ws connect failed:', e.message || e);
    }

    await new Promise(r => setTimeout(r, 800));

    await fetchPrivateMessages(accessToken, FRIEND_ID, 1);

    if (ws && ws.readyState === WebSocket.OPEN) {
        sendPrivateTextWS(ws, FRIEND_ID, 'Hello from frontend-test.js at ' + new Date().toISOString());
    } else {
        console.warn('ws not open, cannot send test message');
    }

    console.log('waiting 6s to collect WS events...');
    await new Promise(r => setTimeout(r, 6000));

    if (ws) {
        try { ws.close(); } catch (ignore) {}
    }
    console.log('=== frontend test script done ===');
})();