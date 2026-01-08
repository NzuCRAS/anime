首先建立一个webSocket连接

wsBaseUrl --> wss://服务器地址:8443

最终连接到[wsBaseUrl]/ws/chat?token=accessToken

拿着这个地址创建连接

```
  const url = wsBaseUrl + '/ws/chat?token=' + encodeURIComponent(accessToken);
  ws = new WebSocket(url);
```

接着打开连接并持续监听
```
  ws.onopen = () => { console.log('WS open'); };
  ws.onmessage = ev => {
    try {
      const env = JSON.parse(ev.data);
      handleEnvelope(env);
    } catch(e) {
      console.error('WS msg parse error', e, ev.data);
    }
  };
```
onmessage相当于一直在监听后端的socket，如果有信息传输，直接调用其中的逻辑

handleEnvelope是自定义的处理函数

envelope相当于前后端约定好的信息传输格式，包含type和payload

如果要发送长这样

```
        const msg = { type, payload };
        log('WS SEND', msg);
        ws.send(JSON.stringify(msg));
```

type表示我这次传输的信息是什么类型，是你主要需要看的内容，因为你要根据我传递的信息类型来进行相应的操作，就像：

```
function handleEnvelope(env) {
  const type = env.type;
  const p = env.payload;
  switch(type) {
    case 'ACK':
      handleAck(p); break;
    case 'NEW_MESSAGE':
    case 'NEW_PRIVATE_MESSAGE':
      handleNewMessage(p); break;
    case 'NEW_FRIEND_REQUEST':
      showFriendRequestNotification(p); break;
    case 'FRIEND_REQUEST_RESPONSE':
      handleFriendRequestResponse(p); break;
    case 'FRIEND_LIST_UPDATED':
    case 'ACCEPT_FRIEND_REQUEST':
      refreshFriendList(); break;
    case 'MESSAGE_READ':
    case 'PRIVATE_MESSAGES_READ':
    case 'MESSAGES_READ':
      handleMessagesRead(p); break;
    case 'GROUP_SESSION_NEW_MESSAGE':
      updateGroupSession(p); break;
    case 'GROUP_SESSION_READ':
      updateGroupSessionRead(p); break;
    default:
      console.debug('Unhandled WS event', type, p);
  }
}

// send a chat message
async function sendPrivateMessage(targetUserId, content) {
  const clientMessageId = crypto.randomUUID ? crypto.randomUUID() : 'c-'+Date.now();
  const payload = {
    clientMessageId,
    conversationType: 'PRIVATE',
    targetUserId,
    messageType: 'TEXT',
    content
  };
  ws.send(JSON.stringify({ type: 'SEND_MESSAGE', payload }));
  // add to local UI as pending with clientMessageId
}

// handle NEW_MESSAGE
function handleNewMessage(msg) {
  // msg: { id, conversationType, fromUserId, toUserId, groupId, messageType, content, createdAt, imageUrl }
  // insert into local message store for session; if session open, show immediately
  upsertMessageIntoConversation(msg);
  // update left-side session list using provided data OR call /api/chat/sessions/list to be safe
}

// handleMessagesRead
function handleMessagesRead(payload) {
  // payload: { conversationType, readerId, friendId, lastReadMessageId }
  // for a sender, mark messages up to lastReadMessageId read
  markMessagesReadInConversation(payload);
}
```

其中主要包含了接收到后端信息，前端再通过socket发送信息的函数，但是我们业务中不太需要这个，你可以在handleEnvelop函数里面，每个case进入不同的逻辑处理，比如说接收到了

```
NEW_FRIEND_REQUEST — 收到好友申请（payload: { requestId, fromUserId, message }）
```

那么就调用接口拉取好友申请列表，这样实现实时通信

后端现在定义了的type：

```
public enum SocketType {
    ACCEPT_FRIEND_REQUEST,
    REJECT_FRIEND_REQUEST,
    NEW_FRIEND_REQUEST,
    PRIVATE_MESSAGES_READ,
    NEW_PRIVATE_MESSAGE,
    NEW_GROUP_MESSAGE,
    GROUP_MESSAGES_READ,
    USER_ONLINE,
    USER_OFFLINE; 
}
```

```
ACCEPT_FRIEND_REQUEST 有一条好友申请通过（当前用户发起的好友申请）
REJECT_FRIEND_REQUEST 有一条好友申请被拒绝（同上）
NEW_FRIEND_REQUEST 有一条新的好友申请（当前用户是接收方）
PRIVATE_MESSAGES_READ 有一个私聊中当前用户发出的信息被已读
NEW_PRIVATE_MESSAGE 有一条新的私聊消息（当前用户是接收方）
USER_ONLINE 你好友中有一个用户在线（连接到websocket）
USER_OFFLINE 你好友中有一个用户离线
```