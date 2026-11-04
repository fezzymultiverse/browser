package com.fezzy.festersbrowser;

import android.os.CancellationSignal;
import android.util.Log;

import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.credentials.exceptions.CreateCredentialException;
import androidx.credentials.CreateCredentialResponse;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.PublicKeyCredential;
import androidx.credentials.CreatePublicKeyCredentialRequest;
import androidx.credentials.GetPublicKeyCredentialOption;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONObject;

import java.util.concurrent.Executor;

/**
 * Capacitor passkey bridge for AndroidX Credential Manager.
 *
 * The server must generate and verify WebAuthn challenge/options and verify the
 * returned registration/authentication response. BiometricPrompt is an extra
 * app-level gate; Credential Manager may also show its own system UI.
 */
@CapacitorPlugin(name = "FBCredentialPlugin")
public class FBCredentialPlugin extends Plugin {
    private CredentialManager credentialManager;
    private Executor mainExecutor;

    @Override
    public void load() {
        credentialManager = CredentialManager.create(getContext());
        mainExecutor = ContextCompat.getMainExecutor(getContext());
    }

    @PluginMethod
    public void createPasskey(PluginCall call) {
        String requestJson = call.getString("requestJson");
        if (requestJson == null || requestJson.trim().isEmpty()) {
            call.reject("requestJson is required");
            return;
        }
        try {
            // Validate JSON before prompting the user.
            new JSONObject(requestJson);
        } catch (Exception e) {
            call.reject("requestJson must be valid JSON", e);
            return;
        }

        authenticateThen(call, "Create passkey", () -> {
            CreatePublicKeyCredentialRequest request =
                    new CreatePublicKeyCredentialRequest(requestJson);
            credentialManager.createCredentialAsync(
                    getActivity(),
                    request,
                    new CancellationSignal(),
                    mainExecutor,
                    new androidx.credentials.CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>() {
                        @Override
                        public void onResult(CreateCredentialResponse response) {
                            JSObject result = new JSObject();
                            result.put("ok", true);
                            result.put("type", response.getType());
                            if (response instanceof androidx.credentials.CreatePublicKeyCredentialResponse) {
                                result.put("registrationResponseJson",
                                    ((androidx.credentials.CreatePublicKeyCredentialResponse) response)
                                        .getRegistrationResponseJson());
                            } else {
                                result.put("message", "Credential creation completed");
                            }
                            call.resolve(result);
                        }

                        @Override
                        public void onError(CreateCredentialException e) {
                            call.reject("Passkey creation failed: " + safeMessage(e), e);
                        }
                    });
        });
    }

    @PluginMethod
    public void getPasskey(PluginCall call) {
        String requestJson = call.getString("requestJson");
        if (requestJson == null || requestJson.trim().isEmpty()) {
            call.reject("requestJson is required");
            return;
        }
        try {
            new JSONObject(requestJson);
        } catch (Exception e) {
            call.reject("requestJson must be valid JSON", e);
            return;
        }

        authenticateThen(call, "Verify identity", () -> {
            GetPublicKeyCredentialOption option =
                    new GetPublicKeyCredentialOption(requestJson);
            androidx.credentials.GetCredentialRequest request =
                    new androidx.credentials.GetCredentialRequest.Builder()
                            .addCredentialOption(option)
                            .build();

            credentialManager.getCredentialAsync(
                    getActivity(),
                    request,
                    new CancellationSignal(),
                    mainExecutor,
                    new androidx.credentials.CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse response) {
                            Credential credential = response.getCredential();
                            if (!(credential instanceof PublicKeyCredential)) {
                                call.reject("Credential Manager returned an unsupported credential type");
                                return;
                            }
                            JSObject result = new JSObject();
                            result.put("ok", true);
                            result.put("type", credential.getType());
                            result.put("authenticationResponseJson",
                                    ((PublicKeyCredential) credential).getAuthenticationResponseJson());
                            call.resolve(result);
                        }

                        @Override
                        public void onError(GetCredentialException e) {
                            call.reject("Passkey sign-in failed: " + safeMessage(e), e);
                        }
                    });
        });
    }

    private interface CredentialOperation {
        void run() throws Exception;
    }

    private void authenticateThen(PluginCall call, String promptTitle, CredentialOperation operation) {
        if (getActivity() == null || getActivity().isFinishing()) {
            call.reject("Activity is not available");
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt(
                getActivity(),
                mainExecutor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        try {
                            operation.run();
                        } catch (Exception e) {
                            call.reject("Unable to start Credential Manager: " + safeMessage(e), e);
                        }
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        call.reject("Biometric authentication failed or was cancelled: " + errString);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        // The system prompt remains open and may allow another attempt.
                        Log.d("FBCredentialPlugin", "Biometric attempt not recognized");
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle("Authenticate before using a passkey")
                .setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
                                | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        prompt.authenticate(info);
    }

    private static String safeMessage(Exception e) {
        String message = e == null ? null : e.getMessage();
        return message == null || message.trim().isEmpty()
                ? (e == null ? "Unknown error" : e.getClass().getSimpleName())
                : message;
    }
}
