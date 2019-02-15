/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.activities;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import autodagger.AutoInjector;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.CertificateEvent;
import com.nextcloud.talk.utils.HandlerExecutor;
import com.nextcloud.talk.utils.SecurityUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import javax.inject.Inject;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.List;
import java.util.concurrent.Executor;

@AutoInjector(NextcloudTalkApplication.class)
public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 112;

    @Inject
    EventBus eventBus;

    @Inject
    AppPreferences appPreferences;

    @Inject
    Context context;

    private KeyguardManager keyguardManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        super.onCreate(savedInstanceState);
        if (appPreferences.getIsScreenLocked()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecurityUtils.createKey(appPreferences.getScreenLockTimeout());
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (appPreferences.getIsScreenSecured()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkIfWeAreSecure();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkIfWeAreSecure() {
        keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isKeyguardSecure() && appPreferences.getIsScreenLocked()) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.getScreenLockTimeout())) {
                if (SecurityUtils.isFingerprintAvailable(context)) {
                    showBiometricDialog();
                } else {
                    showAuthenticationScreen();
                }
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showBiometricDialog() {
        final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(String.format(context.getString(R.string.nc_biometric_unlock), context.getString(R.string.nc_app_name)))
                .setNegativeButtonText(context.getString(R.string.nc_cancel))
                .build();

        Executor mainExecutor = new HandlerExecutor(new Handler(getMainLooper()));

        final BiometricPrompt biometricPrompt = new BiometricPrompt(this, mainExecutor,
                new BiometricPrompt.AuthenticationCallback() {

                }
        );

        biometricPrompt.authenticate(promptInfo, SecurityUtils.getCryptoObject());
    }

    private void showAuthenticationScreen() {
        Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
        if (intent != null) {
            startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (SecurityUtils.checkIfWeAreAuthenticated(appPreferences.getScreenLockTimeout())) {
                        // all went well
                    }
                }
            } else {
                // we didnt auth
            }
        }
    }

    public void showCertificateDialog(X509Certificate cert, MagicTrustManager magicTrustManager,
                                      @Nullable SslErrorHandler sslErrorHandler) {
        DateFormat formatter = DateFormat.getDateInstance(DateFormat.LONG);
        String validFrom = formatter.format(cert.getNotBefore());
        String validUntil = formatter.format(cert.getNotAfter());

        String issuedBy = cert.getIssuerDN().toString();
        String issuedFor;

        try {
            if (cert.getSubjectAlternativeNames() != null) {
                StringBuilder stringBuilder = new StringBuilder();
                for (Object o : cert.getSubjectAlternativeNames()) {
                    List list = (List) o;
                    int type = (Integer) list.get(0);
                    if (type == 2) {
                        String name = (String) list.get(1);
                        stringBuilder.append("[").append(type).append("]").append(name).append(" ");
                    }
                }
                issuedFor = stringBuilder.toString();
            } else {
                issuedFor = cert.getSubjectDN().getName();
            }

            @SuppressLint("StringFormatMatches") String dialogText = String.format(getResources()
                            .getString(R.string.nc_certificate_dialog_text),
                    issuedBy, issuedFor, validFrom, validUntil);

            new LovelyStandardDialog(this)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setNegativeButtonColorRes(R.color.nc_darkRed)
                    .setPositiveButtonColorRes(R.color.colorPrimaryDark)
                    .setIcon(R.drawable.ic_security_white_24dp)
                    .setTitle(R.string.nc_certificate_dialog_title)
                    .setMessage(dialogText)
                    .setPositiveButton(R.string.nc_yes, v -> {
                        magicTrustManager.addCertInTrustStore(cert);
                        if (sslErrorHandler != null) {
                            sslErrorHandler.proceed();
                        }
                    })
                    .setNegativeButton(R.string.nc_no, view1 -> {
                        if (sslErrorHandler != null) {
                            sslErrorHandler.cancel();
                        }
                    })
                    .show();

        } catch (CertificateParsingException e) {
            Log.d(TAG, "Failed to parse the certificate");
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(CertificateEvent event) {
        showCertificateDialog(event.getX509Certificate(), event.getMagicTrustManager(), event.getSslErrorHandler());
    }

    @Override
    public void onStart() {
        super.onStart();
        eventBus.register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        eventBus.unregister(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
