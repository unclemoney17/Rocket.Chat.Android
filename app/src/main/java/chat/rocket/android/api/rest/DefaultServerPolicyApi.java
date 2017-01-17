package chat.rocket.android.api.rest;

import android.support.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.ResponseBody;
import rx.Emitter;
import rx.Observable;

public class DefaultServerPolicyApi implements ServerPolicyApi {

  private static final String API_INFO_PATH = "/api/info";

  private final OkHttpClient client;
  private final String host;

  public DefaultServerPolicyApi(@NonNull OkHttpClient client, @NonNull String host) {
    this.client = client;
    this.host = host;
  }

  @Override
  public Observable<Response<JSONObject>> getApiInfoSecurely() {
    return getApiInfo(SECURE_PROTOCOL);
  }

  @Override
  public Observable<Response<JSONObject>> getApiInfoInsecurely() {
    return getApiInfo(INSECURE_PROTOCOL);
  }

  private Observable<Response<JSONObject>> getApiInfo(@NonNull String protocol) {
    return Observable.fromEmitter(responseEmitter -> {
      final Call call = client.newCall(createRequest(protocol));

      call.enqueue(getOkHttpCallback(responseEmitter, protocol));

      responseEmitter.setCancellation(call::cancel);
    }, Emitter.BackpressureMode.LATEST);
  }

  private Request createRequest(@NonNull String protocol) {
    return new Request.Builder()
        .url(protocol + host + API_INFO_PATH)
        .get()
        .build();
  }

  private okhttp3.Callback getOkHttpCallback(@NonNull Emitter<Response<JSONObject>> emitter,
                                             @NonNull String protocol) {
    return new okhttp3.Callback() {
      @Override
      public void onFailure(Call call, IOException ioException) {
        emitter.onError(ioException);
      }

      @Override
      public void onResponse(Call call, okhttp3.Response response) throws IOException {
        if (!response.isSuccessful()) {
          emitter.onNext(new Response<>(false, protocol, null));
          emitter.onCompleted();
          return;
        }

        final ResponseBody body = response.body();
        if (body == null || body.contentLength() == 0) {
          emitter.onNext(new Response<>(false, protocol, null));
          emitter.onCompleted();
          return;
        }

        try {
          emitter.onNext(new Response<>(true, protocol, new JSONObject(body.string())));
        } catch (Exception e) {
          emitter.onNext(new Response<>(false, protocol, null));
        }

        emitter.onCompleted();
      }
    };
  }
}
