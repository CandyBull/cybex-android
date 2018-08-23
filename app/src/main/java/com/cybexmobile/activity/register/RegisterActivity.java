package com.cybexmobile.activity.register;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.cybexmobile.R;
import com.cybexmobile.activity.main.BottomNavigationActivity;
import com.cybexmobile.activity.introduction.WalletIntroductionActivity;
import com.cybexmobile.api.BitsharesWalletWraper;
import com.cybexmobile.api.RetrofitFactory;
import com.cybexmobile.api.WebSocketClient;
import com.cybexmobile.base.BaseActivity;
import com.cybexmobile.dialog.CybexDialog;
import com.cybexmobile.exception.ErrorCodeException;
import com.cybexmobile.exception.NetworkStatusException;
import com.cybexmobile.faucet.CreateAccountException;
import com.cybexmobile.faucet.CreateAccountRequest;
import com.cybexmobile.faucet.CreateAccountResponse;
import com.cybexmobile.graphene.chain.AccountObject;
import com.cybexmobile.graphene.chain.GlobalConfigObject;
import com.cybexmobile.graphene.chain.PrivateKey;
import com.cybexmobile.graphene.chain.Types;
import com.cybexmobile.utils.VirtualBarUtil;
import com.google.gson.Gson;
import com.pixplicity.sharp.Sharp;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.HttpException;

import static android.webkit.WebViewClient.ERROR_FILE_NOT_FOUND;
import static com.cybexmobile.constant.ErrorCode.ERROR_ACCOUNT_OBJECT_EXIST;
import static com.cybexmobile.constant.ErrorCode.ERROR_FILE_READ_FAIL;
import static com.cybexmobile.constant.ErrorCode.ERROR_IMPORT_NOT_MATCH_PRIVATE_KEY;
import static com.cybexmobile.constant.ErrorCode.ERROR_NETWORK_FAIL;
import static com.cybexmobile.constant.ErrorCode.ERROR_NO_ACCOUNT_OBJECT;
import static com.cybexmobile.constant.ErrorCode.ERROR_PASSWORD_CONFIRM_FAIL;
import static com.cybexmobile.constant.ErrorCode.ERROR_PASSWORD_INVALID;
import static com.cybexmobile.constant.ErrorCode.ERROR_SERVER_CREATE_ACCOUNT_FAIL;
import static com.cybexmobile.constant.ErrorCode.ERROR_SERVER_RESPONSE_FAIL;
import static com.cybexmobile.constant.ErrorCode.ERROR_UNKNOWN;
import static com.cybexmobile.utils.Constant.PREF_IS_LOGIN_IN;
import static com.cybexmobile.utils.Constant.PREF_NAME;
import static com.cybexmobile.utils.Constant.PREF_PASSWORD;

public class RegisterActivity extends BaseActivity {
    private static final String TAG = "RegisterActivity";
    ImageView mCloudWalletIntroductionQuestionMarker, mPinCodeImageView, mUserNameChecker, mPasswordChecker, mPasswordConfirmChecker, mRegisterErrorSign;
    ImageView mUserNameicon, mPasswordIcon, mPasswordConfirmIcon, mPinCodeIcon;
    TextView mTvLoginIn, mRegisterErrorText;
    EditText mPassWordTextView, mConfirmationTextView, mPinCodeTextView, mUserNameTextView;
    Button mSignInButton;
    private Toolbar mToolbar;
    private LinearLayout mLayoutContainer, mLayoutError;
    String mCapId;
    Timer mTimer = new Timer();
    Task mTask = new Task();
    private int mLastScrollHeight;
    private int mVirtualBarHeight = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        initViews();
        mTimer.schedule(mTask, 0, 90 * 1000);
        setViews();
        setOnClickListener();
    }

    public class Task extends TimerTask {
        @Override
        public void run() {
            requestForPinCode();
        }
    }

    private void initViews() {
        mCloudWalletIntroductionQuestionMarker = findViewById(R.id.register_cloud_wallet_question_marker);
        mTvLoginIn = findViewById(R.id.tv_login_in);
        mUserNameTextView = findViewById(R.id.register_et_account_name);
        mPassWordTextView = findViewById(R.id.register_et_password);
        mConfirmationTextView = findViewById(R.id.register_et_password_confirmation);
        mSignInButton = findViewById(R.id.email_sign_in_button);
        mPinCodeImageView = findViewById(R.id.register_pin_code_image);
        mPinCodeTextView = findViewById(R.id.register_et_pin_code);
        mRegisterErrorText = findViewById(R.id.register_error_text);
        mUserNameChecker = findViewById(R.id.user_name_check);
        mPasswordChecker = findViewById(R.id.password_check);
        mPasswordConfirmChecker = findViewById(R.id.password_confirm_check);
        mRegisterErrorSign = findViewById(R.id.register_error_sign);
        mToolbar = findViewById(R.id.toolbar);
        mUserNameicon = findViewById(R.id.register_account_icon);
        mPasswordIcon = findViewById(R.id.register_password_icon);
        mPasswordConfirmIcon = findViewById(R.id.register_password_confirmation_icon);
        mPinCodeIcon = findViewById(R.id.register_pin_code_icon);
        mLayoutError = findViewById(R.id.register_ll_error);
        mLayoutContainer = findViewById(R.id.register_ll_container);
        setSupportActionBar(mToolbar);
    }

    private void setViews() {
        mUserNameTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String strAccountName = s.toString();
                if (strAccountName.isEmpty()) {
                    return;
                }
                if (!Character.isLetter(strAccountName.charAt(0))) {
                    mRegisterErrorText.setText(R.string.create_account_account_name_error_start_letter);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                    mUserNameChecker.setVisibility(View.GONE);
                } else if(!strAccountName.matches("^[a-z0-9-]+$")){
                    mRegisterErrorText.setText(R.string.create_account_account_name_should_only_contain_letter_dash_and_numbers);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                    mUserNameChecker.setVisibility(View.GONE);
                } else if (strAccountName.length() < 3) {
                    mRegisterErrorText.setText(R.string.create_account_account_name_too_short);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                    mUserNameChecker.setVisibility(View.GONE);
                } else if (strAccountName.contains("--")) {
                    mRegisterErrorText.setText(R.string.create_account_account_name_should_not_contain_continuous_dashes);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                    mUserNameChecker.setVisibility(View.GONE);
                } else if (strAccountName.endsWith("-")) {
                    mRegisterErrorText.setText(R.string.create_account_account_name_error_dash_end);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                    mUserNameChecker.setVisibility(View.GONE);
                } else {
                    if (strAccountName.matches("^[a-z]+$")) {
                        mRegisterErrorText.setText(R.string.create_account_account_name_error_full_letter);
                        mRegisterErrorSign.setVisibility(View.VISIBLE);
                        mUserNameChecker.setVisibility(View.GONE);
                    } else {
                        mRegisterErrorText.setText("");
                        mRegisterErrorSign.setVisibility(View.GONE);
                        mUserNameChecker.setVisibility(View.VISIBLE);
                        processCheckAccount(strAccountName);
                    }
                    setRegisterButtonEnable(mUserNameChecker.getVisibility() == View.VISIBLE &&
                            mPasswordChecker.getVisibility() == View.VISIBLE &&
                            mPasswordConfirmChecker.getVisibility() == View.VISIBLE &&
                            !TextUtils.isEmpty(mPinCodeTextView.getText().toString().trim()));
                }

            }
        });

        mUserNameTextView.setOnFocusChangeListener(onFocusChangeListener);

        mPassWordTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String strPassword = s.toString();
                if (!strPassword.matches("(?=.*[0-9])(?=.*[A-Z])(?=.*[a-z])(?=.*[^a-zA-Z0-9]).{12,}")) {
                    mRegisterErrorText.setText(getResources().getString(R.string.create_account_password_error));
                    mPasswordChecker.setVisibility(View.GONE);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                } else {
                    mRegisterErrorText.setText("");
                    mPasswordChecker.setVisibility(View.VISIBLE);
                    mRegisterErrorSign.setVisibility(View.GONE);
                }
                setRegisterButtonEnable(mUserNameChecker.getVisibility() == View.VISIBLE &&
                        mPasswordChecker.getVisibility() == View.VISIBLE &&
                        mPasswordConfirmChecker.getVisibility() == View.VISIBLE &&
                        !TextUtils.isEmpty(mPinCodeTextView.getText().toString().trim()));
            }
        });

        mPassWordTextView.setOnFocusChangeListener(onFocusChangeListener);

        mConfirmationTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                String strPassword = mPassWordTextView.getText().toString();
                String strPasswordConfirm = s.toString();
                if (strPassword.compareTo(strPasswordConfirm) == 0) {
                    mPasswordConfirmChecker.setVisibility(View.VISIBLE);
                    mRegisterErrorText.setText("");
                    mRegisterErrorSign.setVisibility(View.GONE);
                } else {
                    mPasswordConfirmChecker.setVisibility(View.INVISIBLE);
                    mRegisterErrorText.setText(R.string.create_account_password_confirm_error);
                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                }
                setRegisterButtonEnable(mUserNameChecker.getVisibility() == View.VISIBLE &&
                        mPasswordChecker.getVisibility() == View.VISIBLE &&
                        mPasswordConfirmChecker.getVisibility() == View.VISIBLE &&
                        !TextUtils.isEmpty(mPinCodeTextView.getText().toString().trim()));
            }
        });

        mConfirmationTextView.setOnFocusChangeListener(onFocusChangeListener);
        mPinCodeTextView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                setRegisterButtonEnable(mUserNameChecker.getVisibility() == View.VISIBLE &&
                        mPasswordChecker.getVisibility() == View.VISIBLE &&
                        mPasswordConfirmChecker.getVisibility() == View.VISIBLE &&
                        !TextUtils.isEmpty(mPinCodeTextView.getText().toString().trim()));
            }
        });

        mPinCodeTextView.setOnFocusChangeListener(onFocusChangeListener);

    }

    private View.OnFocusChangeListener onFocusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            switch (v.getId()) {
                case R.id.register_et_account_name:
                    mUserNameicon.setAlpha(hasFocus ? 1f : 0.5f);
                    break;
                case R.id.register_et_password:
                    mPasswordIcon.setAlpha(hasFocus ? 1f : 0.5f);
                    break;
                case R.id.register_et_password_confirmation:
                    mPasswordConfirmIcon.setAlpha(hasFocus ? 1f : 0.5f);
                    break;
                case R.id.register_et_pin_code:
                    mPinCodeIcon.setAlpha(hasFocus ? 1f : 0.5f);
                    break;
            }
        }
    };

    private void requestForPinCode() {
        RetrofitFactory.getInstance()
                .api()
                .getPinCode(RetrofitFactory.url_pin_code)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ResponseBody>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.v(TAG, "onSubscribe");
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        Log.v(TAG, "onNext");
                        JSONObject jsonObject = null;
                        try {
                            jsonObject = new JSONObject(responseBody.string());
                            String svgString = jsonObject.getString("data");
                            mCapId = jsonObject.getString("id");
                            Sharp.loadString(svgString).into(mPinCodeImageView);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.v(TAG, "onError");
                    }

                    @Override
                    public void onComplete() {
                        Log.v(TAG, "onComplete");
                    }
                });
    }

    private void setOnClickListener() {
        mCloudWalletIntroductionQuestionMarker.setOnClickListener(v -> {
            Intent intent = new Intent(RegisterActivity.this, WalletIntroductionActivity.class);
            startActivity(intent);
        });
        mTvLoginIn.setOnClickListener(v -> onBackPressed());
        mSignInButton.setOnClickListener(v -> {
            String account = mUserNameTextView.getText().toString().trim();
            String password = mPassWordTextView.getText().toString().trim();
            String passwordConfirm = mConfirmationTextView.getText().toString();
            String pinCode = mPinCodeTextView.getText().toString().trim();
            if (TextUtils.isEmpty(account) || TextUtils.isEmpty(password) ||
                    TextUtils.isEmpty(passwordConfirm) || TextUtils.isEmpty(pinCode)) {
                return;
            }
            processCreateAccount(account, password, passwordConfirm, pinCode, mCapId);
        });

        mPinCodeImageView.setOnClickListener(v -> requestForPinCode());
        mLayoutContainer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if(mVirtualBarHeight == -1){
                    mVirtualBarHeight = VirtualBarUtil.getHeight(RegisterActivity.this);
                }
                /**
                 * fix bug
                 * 计算虚拟导航栏的高度
                 */
                Rect rect = new Rect();
                mLayoutContainer.getWindowVisibleDisplayFrame(rect);
                int invisibleHeight = mLayoutContainer.getRootView().getHeight() - (rect.bottom + mVirtualBarHeight);
                if(invisibleHeight > 100){
                    int[] location = new int[2];
                    mLayoutError.getLocationInWindow(location);
                    int scrollHeight = location[1] + mLayoutError.getHeight() - rect.bottom;
                    mLastScrollHeight += scrollHeight;
                    mLayoutContainer.scrollTo(0, mLastScrollHeight);
                }else{
                    mLayoutContainer.scrollTo(0, 0);
                    mLastScrollHeight = 0;
                }
            }
        });
    }

    private void setRegisterButtonEnable(boolean enabled) {
        mSignInButton.setEnabled(enabled);
    }


    private String parseAccount(String strAccountName,
                                              String strPassword,
                                              String pinCode,
                                              String capId){
        PrivateKey privateActiveKey = PrivateKey.from_seed(strAccountName + "active" + strPassword);
        PrivateKey privateOwnerKey = PrivateKey.from_seed(strAccountName + "owner" + strPassword);
        Types.public_key_type publicActiveKeyType = new Types.public_key_type(privateActiveKey.get_public_key(true), true);
        Types.public_key_type publicOwnerKeyType = new Types.public_key_type(privateOwnerKey.get_public_key(true), true);
        CreateAccountRequest createAccountRequest = new CreateAccountRequest();
        CreateAccountRequest.Account account = new CreateAccountRequest.Account();
        CreateAccountRequest.Cap cap = new CreateAccountRequest.Cap();
        cap.id = capId;
        cap.captcha = pinCode;
        createAccountRequest.cap = cap;
        account.name = strAccountName;
        account.active_key = publicActiveKeyType;
        account.owner_key = publicOwnerKeyType;
        account.memo_key = publicActiveKeyType;
        account.refcode = null;
        account.referrer = null;
        createAccountRequest.account = account;
        Gson gson = GlobalConfigObject.getInstance().getGsonBuilder().create();
        Log.v(TAG, gson.toJson(createAccountRequest));
        return gson.toJson(createAccountRequest);
    }

    private void processCreateAccount(final String strAccount, final String strPassword, String strPasswordConfirm, String pinCode, String capId) {
        if (strPassword.compareTo(strPasswordConfirm) != 0) {
            processErrorCode(ERROR_PASSWORD_CONFIRM_FAIL);
            return;
        }
        showLoadDialog();
        RetrofitFactory.getInstance()
                .api()
                .register(RetrofitFactory.url_register, RequestBody.create(MediaType.parse("application/json"), parseAccount(strAccount, strPassword, pinCode, capId)))
                .map(new Function<CreateAccountResponse, CreateAccountResponse>() {
                    @Override
                    public CreateAccountResponse apply(CreateAccountResponse createAccountResponse) {
                        if(!TextUtils.isEmpty(createAccountResponse.error)){
                            Observable.error(new CreateAccountException(createAccountResponse.error));
                        }
                        return createAccountResponse;
                    }
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Object>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        Log.v(TAG, "processCreateAccount: onSubscribe");
                    }

                    @Override
                    public void onNext(Object o) {
                        Log.v(TAG, "processCreateAccount: onNext");
                        login(strAccount, strPassword);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Log.v(TAG, "processCreateAccount: onError");
                        hideLoadDialog();
                        if (e instanceof NetworkStatusException) {
                            processErrorCode(ERROR_NETWORK_FAIL);
                        } else if (e instanceof CreateAccountException) {
                            processExceptionMessage(e.getMessage());
                        } else if (e instanceof ErrorCodeException) {
                            ErrorCodeException errorCodeException = (ErrorCodeException) e;
                            processErrorCode(errorCodeException.getErrorCode());
                        } else if (e instanceof HttpException) {
                            processExceptionMessage(e.getMessage());
                        }
                    }

                    @Override
                    public void onComplete() {
                        Log.v(TAG, "processCreateAccount: onComplete");

                    }
                });
    }

    private void login(String account, String password){
        try {
            BitsharesWalletWraper.getInstance().get_account_object(account, new WebSocketClient.MessageCallback<WebSocketClient.Reply<AccountObject>>() {
                @Override
                public void onMessage(WebSocketClient.Reply<AccountObject> reply) {
                    AccountObject accountObject = reply.result;
                    int result = BitsharesWalletWraper.getInstance().import_account_password(accountObject, account, password);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            hideLoadDialog();
                            if (result == 0) {
                                hideLoadDialog();
                                CybexDialog.showRegisterDialog(RegisterActivity.this, password, new View.OnClickListener(){
                                    @Override
                                    public void onClick(View view) {
                                        Intent intent = new Intent(RegisterActivity.this, BottomNavigationActivity.class);
                                        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(RegisterActivity.this);
                                        sharedPreferences.edit().putBoolean(PREF_IS_LOGIN_IN, true).apply();
                                        sharedPreferences.edit().putString(PREF_NAME, account).apply();
                                        sharedPreferences.edit().putString(PREF_PASSWORD, password).apply();
                                        startActivity(intent);
                                    }
                                });
                            }
                        }
                    });
                }

                @Override
                public void onFailure() {
                    hideLoadDialog();
                }
            });
        } catch (NetworkStatusException e) {
            e.printStackTrace();
        }
    }

    private void processErrorCode(final int nErrorCode) {
        final TextView textView = findViewById(R.id.register_error_text);
        switch (nErrorCode) {
            case ERROR_NETWORK_FAIL:
                textView.setText(R.string.create_account_activity_network_fail);
                break;
            case ERROR_ACCOUNT_OBJECT_EXIST:
                textView.setText(R.string.create_account_activity_account_object_exist);
                break;
            case ERROR_SERVER_RESPONSE_FAIL:
                textView.setText(R.string.create_account_activity_response_fail);
                break;
            case ERROR_SERVER_CREATE_ACCOUNT_FAIL:
                textView.setText(R.string.create_account_activity_create_fail);
                break;
            case ERROR_FILE_NOT_FOUND:
                textView.setText(R.string.import_activity_file_failed);
                break;
            case ERROR_FILE_READ_FAIL:
                textView.setText(R.string.import_activity_file_failed);
                break;
            case ERROR_NO_ACCOUNT_OBJECT:
                textView.setText(R.string.import_activity_account_name_invalid);
                break;
            case ERROR_IMPORT_NOT_MATCH_PRIVATE_KEY:
                textView.setText(R.string.import_activity_private_key_invalid);
                break;
            case ERROR_PASSWORD_INVALID:
                textView.setText(R.string.import_activity_password_invalid);
                break;
            case ERROR_UNKNOWN:
                textView.setText(R.string.import_activity_unknown_error);
                break;
            default:
                textView.setText(R.string.import_activity_unknown_error);
                break;
        }
    }

    private void processExceptionMessage(final String strMessage) {
        if (strMessage.contains("403")) {
            mRegisterErrorText.setText(getResources().getString(R.string.create_account_pin_code_not_correct));
        } else {
            mRegisterErrorText.setText(getResources().getString(R.string.create_account_pin_code_not_correct));
        }
        mRegisterErrorSign.setVisibility(View.VISIBLE);
    }

    private void processCheckAccount(final String strAccount) {
        try {
            BitsharesWalletWraper.getInstance().get_account_object(strAccount, new WebSocketClient.MessageCallback<WebSocketClient.Reply<AccountObject>>() {
                @Override
                public void onMessage(WebSocketClient.Reply<AccountObject> reply) {
                    AccountObject accountObject = reply.result;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (accountObject == null) {
                                mUserNameChecker.setVisibility(View.VISIBLE);
                                mRegisterErrorSign.setVisibility(View.GONE);
                            } else {
                                if (strAccount.compareTo(accountObject.name) == 0) {
                                    mRegisterErrorText.setText(R.string.create_account_activity_account_object_exist);
                                    mRegisterErrorSign.setVisibility(View.VISIBLE);
                                    mUserNameChecker.setVisibility(View.GONE);
                                }
                            }
                        }
                    });
                }

                @Override
                public void onFailure() {

                }
            });


        } catch (NetworkStatusException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNetWorkStateChanged(boolean isAvailable) {

    }
}