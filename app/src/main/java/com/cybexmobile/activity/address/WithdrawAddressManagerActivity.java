package com.cybexmobile.activity.address;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;

import com.cybex.database.DBManager;
import com.cybex.database.entity.Address;
import com.cybexmobile.R;
import com.cybexmobile.adapter.DepositAndWithdrawAdapter;
import com.cybexmobile.api.RetrofitFactory;
import com.cybexmobile.base.BaseActivity;
import com.cybexmobile.faucet.DepositAndWithdrawObject;
import com.cybexmobile.fragment.WithdrawItemFragment;
import com.cybexmobile.service.WebSocketService;
import com.cybexmobile.utils.MyUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

import static com.cybexmobile.utils.Constant.PREF_NAME;

public class WithdrawAddressManagerActivity extends BaseActivity {
    private static final String TAG = WithdrawAddressManagerActivity.class.getName();

    private List<DepositAndWithdrawObject> mWithdrawObjectList = new ArrayList<>();
    private Unbinder mUnbinder;
    private DepositAndWithdrawAdapter mDepositAndWithdrawAdapter;
    private WebSocketService mWebSocketService;
    private Disposable mLoadAddressCountDisposable;

    private String mUserName;

    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.withdraw_address_rv)
    RecyclerView mWithdrawAddressRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_withdraw_address_manange);
        mUnbinder = ButterKnife.bind(this);
        setSupportActionBar(mToolbar);
        mUserName = PreferenceManager.getDefaultSharedPreferences(this).getString(PREF_NAME, "");
        Intent intent = new Intent(this, WebSocketService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
        mDepositAndWithdrawAdapter = new DepositAndWithdrawAdapter(this, TAG, mWithdrawObjectList);
        mWithdrawAddressRecyclerView.setAdapter(mDepositAndWithdrawAdapter);
        mWithdrawAddressRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mWithdrawAddressRecyclerView.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        if (mWithdrawObjectList.size() == 0) {
            requestWithdrawList();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        requestWithdrawList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mUnbinder.unbind();
        unbindService(mConnection);
        if (mLoadAddressCountDisposable != null && !mLoadAddressCountDisposable.isDisposed()) {
            mLoadAddressCountDisposable.dispose();
        }
    }

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            WebSocketService.WebSocketBinder binder = (WebSocketService.WebSocketBinder) service;
            mWebSocketService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mWebSocketService = null;
        }
    };

    private void loadAddressCount(List<DepositAndWithdrawObject> depositAndWithdrawObjectList, DepositAndWithdrawObject depositAndWithdrawObject, int i) {
        if (mUserName.isEmpty()) {
            return;
        }
        mLoadAddressCountDisposable = DBManager.getDbProvider(this).getCount(mUserName, depositAndWithdrawObject.getId(), Address.TYPE_WITHDRAW)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<Long>() {
                    @Override
                    public void accept(Long count) throws Exception {
                        depositAndWithdrawObject.setCount(count);
                        if (i == depositAndWithdrawObjectList.size() - 1) {

                            mWithdrawObjectList.clear();
                            mWithdrawObjectList.addAll(depositAndWithdrawObjectList);
                            mDepositAndWithdrawAdapter.notifyDataSetChanged();
                        }
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {

                    }
                });
    }

    private void requestWithdrawList() {
        RetrofitFactory.getInstance()
                .api()
                .getWithdrawList()
                .map(new Function<ResponseBody, List<DepositAndWithdrawObject>>() {
                    @Override
                    public List<DepositAndWithdrawObject> apply(ResponseBody responseBody) throws Exception {
                        List<DepositAndWithdrawObject> depositAndWithdrawObjectList = new ArrayList<>();
                        JSONArray jsonArray = null;
                        jsonArray = new JSONArray(responseBody.string());
                        for (int i = 0; i < jsonArray.length(); i++) {
                            DepositAndWithdrawObject depositAndWithdrawObject = new DepositAndWithdrawObject();
                            JSONObject jsonObject = jsonArray.getJSONObject(i);
                            depositAndWithdrawObject.setId(jsonObject.getString("id"));
                            depositAndWithdrawObject.setAssetObject(mWebSocketService.getAssetObject(jsonObject.getString("id")));
                            depositAndWithdrawObjectList.add(depositAndWithdrawObject);

                        }
                        return depositAndWithdrawObjectList;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<DepositAndWithdrawObject>>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(List<DepositAndWithdrawObject> depositAndWithdrawObjects) {
                        for (int i = 0; i < depositAndWithdrawObjects.size(); i++) {
                            loadAddressCount(depositAndWithdrawObjects, depositAndWithdrawObjects.get(i), i);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Override
    public void onNetWorkStateChanged(boolean isAvailable) {

    }
}