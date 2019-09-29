package com.cybex.provider.websocket.apihk;


import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.cybex.provider.exception.NetworkStatusException;
import com.cybex.provider.graphene.chain.CoinAgeObject;
import com.cybex.provider.graphene.chain.GlobalConfigObject;
import com.cybex.provider.graphene.chain.LimitOrder;
import com.cybex.provider.websocket.Call;
import com.cybex.provider.websocket.DelayCall;
import com.cybex.provider.websocket.IReplyProcess;
import com.cybex.provider.websocket.MessageCallback;
import com.cybex.provider.websocket.Reply;
import com.cybex.provider.websocket.ReplyProcessImpl;
import com.cybex.provider.websocket.WebSocketNodeConfig;
import com.cybex.provider.websocket.WebSocketStatus;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class ApihkWebSocketClient extends WebSocketListener {

    private static final String TAG = ApihkWebSocketClient.class.getSimpleName();

    private static final String APIHK_SERVER = "wss://normal-hongkong.cybex.io";

    private static final int WHAT_MESSAGE = 10000002;

    private volatile int mStatusId = -1;

    private OkHttpClient mOkHttpClient;
    private WebSocket mWebSocket;
    //websocket connect status
    private volatile WebSocketStatus mConnectStatus = WebSocketStatus.DEFAULT;

    private ConcurrentHashMap<Integer, ReplyProcessImpl> mHashMapIdToProcess = new ConcurrentHashMap<>();
    private List<DelayCall> delayCalls = null;
    private final AtomicInteger mCallId = new AtomicInteger(1);
    private final JsonParser mJsonParser = new JsonParser();

    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if(msg.what != WHAT_MESSAGE){
                return;
            }
            IReplyProcess iReplyProcess = (IReplyProcess) msg.obj;
            MessageCallback callback = iReplyProcess.getCallback();
            if(callback != null){
                callback.onMessage(iReplyProcess.getReply());
                iReplyProcess.release();
            }
        }
    };

    public AtomicInteger getCallId() {
        return mCallId;
    }

    public ApihkWebSocketClient(){
        delayCalls = Collections.synchronizedList(new LinkedList<DelayCall>());
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        super.onOpen(webSocket, response);
        Log.v(TAG, "onOpen: ApihkWebSocketClient is connected" );
        mWebSocket = webSocket;
        mConnectStatus = WebSocketStatus.OPENED;
        try {
            //websocket连接成功, send login
            login(loginCallback);
        } catch (NetworkStatusException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, ByteString bytes) {
        super.onMessage(webSocket, bytes);
        Log.v(TAG, String.format("onMessage: %s", bytes.toString()));
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        super.onMessage(webSocket, text);
        Log.v(TAG, String.format("onMessage: %s", text));
        try {
            int callId = mJsonParser.parse(text).getAsJsonObject().get("id").getAsInt();
            IReplyProcess iReplyProcess = mHashMapIdToProcess.remove(callId);
            if (iReplyProcess != null) {
                iReplyProcess.processTextToObject(text);
                mHandler.sendMessage(mHandler.obtainMessage(WHAT_MESSAGE, iReplyProcess));
            }
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        super.onFailure(webSocket, t, response);
        Log.v(TAG, "onFailure: ApihkWebSocketClient on failure", t);
        mConnectStatus = WebSocketStatus.FAILURE;
        mHashMapIdToProcess.clear();
        try {
            Thread.sleep(3*1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        super.onClosing(webSocket, code, reason);
        Log.v(TAG, "ApihkWebSocketClient is closing, code:" + code + " reason:" + reason);
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        super.onClosed(webSocket, code, reason);
        Log.v(TAG, "ApihkWebSocketClient is closed, code:" + code + " reason:" + reason);
        mConnectStatus = WebSocketStatus.CLOSED;
    }

    //连接WebSocket
    public void connect() {
        if(mConnectStatus == WebSocketStatus.OPENING || mConnectStatus == WebSocketStatus.OPENED
                || mConnectStatus == WebSocketStatus.LOGIN){
            return;
        }
        Request request = new Request.Builder().url(WebSocketNodeConfig.getInstance().getLimit_order() != null ? WebSocketNodeConfig.getInstance().getLimit_order() : APIHK_SERVER).build();
        mOkHttpClient = new OkHttpClient();
        mOkHttpClient.newWebSocket(request, this);
        mConnectStatus = WebSocketStatus.OPENING;
    }

    public void disconnect() {
        this.close();
        mHandler.removeCallbacksAndMessages(null);
    }

    //关闭WebSocket
    public void close() {
        if(mWebSocket != null){
            mWebSocket.close(1000, "Close");
            mWebSocket = null;
        }
        mOkHttpClient = null;
        mConnectStatus = WebSocketStatus.CLOSING;
        mHashMapIdToProcess.clear();
        mStatusId = -1;
    }

    /**
     * request: {"method": "call", "params": [1, "limit_order_status", []], "id": 1}
     * response: {"id":1,"jsonrpc":"2.0","result":2}
     */
    private void login(MessageCallback<Reply<Integer>> callback) throws NetworkStatusException {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(1);
        callObject.params.add("limit_order_status");
        callObject.params.add(new ArrayList<>());

        ReplyProcessImpl<Reply<Integer>> replyObject = new ReplyProcessImpl<>(new TypeToken<Reply<Integer>>(){}.getType(), callback);
        sendForReplyImpl(callObject, replyObject);
    }

    /**
     *
     * @param accountId 用户ID
     * @param callback 回调
     * @throws NetworkStatusException
     */
    public void get_opend_limit_orders(String accountId,
                                       MessageCallback<Reply<List<LimitOrder>>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_opened_limit_order_status");

        List<Object> listParams = new ArrayList<>();
        listParams.add(accountId);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<List<LimitOrder>>> replyObjectProcess = new ReplyProcessImpl<>(new TypeToken<Reply<List<LimitOrder>>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }

    /**
     *
     * @param accountId 用户ID
     * @param callback 回调
     * @throws NetworkStatusException
     */
    public void get_opend_market_limit_orders(String accountId,
                                       String baseId,
                                       String quote,
                                       MessageCallback<Reply<List<LimitOrder>>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_opened_market_limit_order_status");

        List<Object> listParams = new ArrayList<>();
        listParams.add(accountId);
        listParams.add(baseId);
        listParams.add(quote);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<List<LimitOrder>>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<List<LimitOrder>>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }

    /**
     * 获取用户历史委单
     * @param accountId 用户名
     * @param lastOrderId 最后订单号
     * @param limit 分页加载数量
     */
    public void get_limit_order_status(String accountId,
                                       String lastOrderId,
                                       int limit,
                                       MessageCallback<Reply<List<LimitOrder>>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_limit_order_status");

        List<Object> listParams = new ArrayList<>();
        listParams.add(accountId);
        listParams.add(lastOrderId);
        listParams.add(limit);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<List<LimitOrder>>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<List<LimitOrder>>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }

    /**
     * 获取用户历史委单
     * @param accountId 用户名
     * @param lastOrderId 最后订单号
     * @param baseId baseId
     * @param quoteId quoteId
     * @param limit 分页加载数量
     */
    public void get_market_limit_order_status(String accountId,
                                       String lastOrderId,
                                       String baseId,
                                       String quoteId,
                                       int limit,
                                       MessageCallback<Reply<List<LimitOrder>>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_market_limit_order_status");

        List<Object> listParams = new ArrayList<>();
        listParams.add(accountId);
        listParams.add(baseId);
        listParams.add(quoteId);
        listParams.add(lastOrderId);
        listParams.add(limit);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<List<LimitOrder>>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<List<LimitOrder>>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }


    /**
     * 添加过滤器
     * @param filteredPair 需要过滤的交易对
     */
    public void add_filtered_market(List<List<String>> filteredPair, MessageCallback<Reply<String>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("add_filtered_market");

        List<Object> listParams = new ArrayList<>();
        listParams.add(filteredPair);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<String>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<String>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);

    }

    /**
     * 获取用户历史委单
     * @param accountId 用户名
     * @param lastOrderId 最后订单号
     * @param limit 分页加载数量
     * @param containFilteredPairs 是否查询添加在过滤其中的交易对
     */
    public void get_filtered_limit_order_status(String accountId,
                                                String lastOrderId,
                                                int limit,
                                                boolean containFilteredPairs,
                                                MessageCallback<Reply<List<LimitOrder>>> callback) {

        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_filtered_limit_order_status");

        List<Object> listParams = new ArrayList<>();
        listParams.add(accountId);
        listParams.add(lastOrderId);
        listParams.add(limit);
        listParams.add(containFilteredPairs);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<List<LimitOrder>>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<List<LimitOrder>>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }

    /**
     * 获取当前时间最后一笔交易订单号
     * @param timestamp
     * @param callback
     */
    public void get_limit_order_id_by_time(String timestamp, MessageCallback<Reply<String>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "call";
        callObject.params = new ArrayList<>();
        callObject.params.add(mStatusId);
        callObject.params.add("get_limit_order_id_by_time");

        List<Object> listParams = new ArrayList<>();
        listParams.add(timestamp);
        callObject.params.add(listParams);

        ReplyProcessImpl<Reply<String>> replyObjectProcess =
                new ReplyProcessImpl<>(new TypeToken<Reply<String>>() {}.getType(), callback);
        sendForReply(callObject, replyObjectProcess);
    }

    public void get_account_token_age(String accountId, MessageCallback<Reply<List<CoinAgeObject>>> callback) {
        Call callObject = new Call();
        callObject.id = mCallId.getAndIncrement();
        callObject.method = "get_account_token_age";
        callObject.params = new ArrayList<>();
        callObject.params.add(accountId);

        ReplyProcessImpl<Reply<List<CoinAgeObject>>> replyObject = new ReplyProcessImpl<>(new TypeToken<Reply<List<CoinAgeObject>>> () {}.getType(), callback);
        sendForReply(callObject, replyObject);
    }


    private <T> void sendForReply(Call callObject, ReplyProcessImpl<Reply<T>> replyObjectProcess) {
        if(mWebSocket != null && mConnectStatus == WebSocketStatus.LOGIN){
            sendForReplyImpl(callObject, replyObjectProcess);
        }else {
            delayCalls.add(new DelayCall<>(callObject, replyObjectProcess));
            if (mConnectStatus != WebSocketStatus.CLOSING || mConnectStatus != WebSocketStatus.CLOSED) {
                connect();
            }
        }
    }

    private <T> void sendForReplyImpl(Call call, ReplyProcessImpl<Reply<T>> replyObjectProcess) {
        Gson gson = GlobalConfigObject.getInstance().getGsonBuilder().create();
        String strMessage = gson.toJson(call);
        Log.d(TAG, String.format("call: %s", strMessage));
        boolean result = mWebSocket.send(strMessage);
        if(result){
            mHashMapIdToProcess.put(call.id, replyObjectProcess);
        }
    }

    private <T> void sendDelayForReply(){
        if(delayCalls == null || delayCalls.size() == 0){
            return;
        }
        Iterator<DelayCall> iterator = delayCalls.iterator();
        while (iterator.hasNext()){
            DelayCall<Reply<T>> delayCall = iterator.next();
            if(delayCall.call.params.size() > 1) {
                delayCall.call.params.set(0, mStatusId);
            }
            iterator.remove();
            sendForReply(delayCall.call, delayCall.replyProcess);
        }
    }

    private MessageCallback<Reply<Integer>> loginCallback = new MessageCallback<Reply<Integer>>() {
        @Override
        public void onMessage(Reply<Integer> reply) {
            mStatusId = reply.result;
            mConnectStatus = WebSocketStatus.LOGIN;
            sendDelayForReply();
        }

        @Override
        public void onFailure() {
            mStatusId = -1;
        }
    };

}

