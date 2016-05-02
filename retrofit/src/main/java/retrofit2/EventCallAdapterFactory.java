/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package retrofit2;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.Executor;
import okhttp3.Request;
import okhttp3.ResponseBody;
import retrofit2.http.Streaming;
import static retrofit2.CallAdapter.Factory.getRawType;

final class EventCallAdapterFactory extends CallAdapter.Factory {
  final Executor callbackExecutor;
  private static Converter<?, ResponseBody> converter;
  
  EventCallAdapterFactory(Executor callbackExecutor) {
    this.callbackExecutor = callbackExecutor;
  }

  @Override @Streaming
  public CallAdapter<Call<?>> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
    if (getRawType(returnType) != EventCall.class) {
      return null;
    }
    // Kyler Conena
    // Comp490
    final Type responseType = Utils.getCallResponseType(returnType);
    converter = retrofit.responseBodyConverter(responseType, annotations);
    return new CallAdapter<Call<?>>() {
      @Override @Streaming public Type responseType() {
        return responseType;
      }

      @Override @Streaming public <R> Call<R> adapt(Call<R> call) {
        return new EventCallbackCall<>(callbackExecutor, call);
      }
    };
  }

  static final class EventCallbackCall<T> implements Call<T> {
    final Executor callbackExecutor;
    final Call<T> delegate;

    EventCallbackCall(Executor callbackExecutor, Call<T> delegate) {
      this.callbackExecutor = callbackExecutor;
      this.delegate = delegate;
    }

    @Override @Streaming public void enqueue(final Callback<T> callback) {
      if (callback == null) throw new NullPointerException("callback == null");

      delegate.enqueue(new Callback<T>() {
        @Override @Streaming public void onResponse(Call<T> call, final Response<T> response) {
          callbackExecutor.execute(new Runnable() {
            @Override @Streaming public void run() {
              if (delegate.isCanceled()) {
                // Emulate OkHttp's behavior of throwing/delivering an IOException on cancellation.
                callback.onFailure(EventCallbackCall.this, new IOException("Canceled"));
              } else {
                T body = response.body();
                // Kyler Conena
                // Comp490
                // Would have to define conversion type, and edit all surrounding mechanisms
                // ResponseBody newBody = converter.convert(body);
                // ResponseBody fullBody = Utils.ResponseBody(newBody);
                callback.onResponse(EventCallbackCall.this, response);
              }
            }
          });
        }

        @Override @Streaming public void onFailure(Call<T> call, final Throwable t) {
          callbackExecutor.execute(new Runnable() {
            @Override @Streaming public void run() {
              callback.onFailure(EventCallbackCall.this, t);
            }
          });
        }
      });
    }

    @Override @Streaming public boolean isExecuted() {
      return delegate.isExecuted();
    }

    @Override @Streaming public Response<T> execute() throws IOException {
      return delegate.execute();
    }

    @Override @Streaming public void cancel() {
      delegate.cancel();
    }

    @Override @Streaming public boolean isCanceled() {
      return delegate.isCanceled();
    }

    @SuppressWarnings("CloneDoesntCallSuperClone") // Performing deep clone.
    @Override @Streaming public Call<T> clone() {
      return new EventCallbackCall<>(callbackExecutor, delegate.clone());
    }

    @Override @Streaming public Request request() {
      return delegate.request();
    }
  }
}
